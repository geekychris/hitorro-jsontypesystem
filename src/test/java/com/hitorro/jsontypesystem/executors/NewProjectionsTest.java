/*
 * Copyright (c) 2006-2026 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.jsontypesystem.executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.jsontypesystem.BaseT;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.JsonTypeSystem;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.jsontypesystem.grouppredicates.GroupNameFilter;
import com.hitorro.jsontypesystem.projections.DocumentStore;
import com.hitorro.jsontypesystem.projections.EmbeddingProvider;
import com.hitorro.jsontypesystem.projections.HashingEmbeddingProvider;
import com.hitorro.jsontypesystem.projections.InMemoryDocumentStore;
import com.hitorro.util.core.Env;
import com.hitorro.util.json.String2JsonMapper;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the six new projections. Every test anchors on the {@code demo_document}
 * type — the module's canonical "document" fixture — either by loading it directly via
 * {@link JsonTypeSystem} or by building a variant whose field shape mirrors demo_document but
 * adds the projection group annotations under test.
 *
 * <p>Requires the runtime {@code config/types/} directory (HT_BIN driven, wired in the surefire
 * pom); each test's {@code @BeforeAll} verifies demo_document is loadable and skips cleanly if not.
 */
@DisplayName("New projections (anchored to demo_document)")
class NewProjectionsTest {

	private static final String2JsonMapper jsonMapper = new String2JsonMapper();

	/**
	 * Load the real demo_document type from config/types via JsonTypeSystem. Returns null if the
	 * type registry isn't wired in this env — callers use {@code assumeTrue} to skip.
	 */
	private static Type loadDemoDocument() {
		try {
			return JsonTypeSystem.getMe().getType("demo_document");
		} catch (Throwable t) {
			return null;
		}
	}

	/** Confirm demo_document is on disk and the type registry can see it. */
	private static boolean demoDocumentAvailable() {
		try {
			File binDir = Env.getBin();
			if (binDir == null) return false;
			File demo = new File(binDir, "config/types/demo_document.json");
			return demo.isFile() && loadDemoDocument() != null;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Build a Type from an inline JSON blob (used to add projection-group annotations). */
	private static Type buildType(String json) {
		Type t = new Type();
		t.init(jsonMapper.apply(json));
		return t;
	}

	private static <A extends ExecutorAction<ExecutionBuilder>> ExecutionBuilder<A> plan(
			Type type, Predicate<BaseT> predicate, ExecutorFactory<A> factory) {
		ExecutionBuilder<A> builder = new ExecutionBuilder<>(factory);
		type.visit(builder, predicate, new Propaccess(""));
		builder.finalizeNode();
		return builder;
	}

	// A demo_document-shaped JVS payload used across tests. Uses the same field names and types
	// as the real demo_document type on disk. Individual tests reference the subset they care about.
	private static final String DOC_JSON = """
			{
			  "filename": "annual-report.pdf",
			  "file_type": "application/pdf",
			  "file_size": 12345,
			  "version": "2.1",
			  "author": "chris.collins@hitorro.com",
			  "department": "engineering",
			  "content": {"mls": [
			    {"lang": "en", "text": "The quick brown fox jumps over the lazy dog."},
			    {"lang": "fr", "text": "Le renard brun rapide saute par-dessus le chien paresseux."}
			  ]},
			  "keywords": ["quarterly", "report", "engineering"],
			  "classification": "internal",
			  "checksum": "abc123",
			  "download_url": "https://example.com/annual-report.pdf"
			}""";

	@BeforeAll
	static void checkDemoDocument() {
		assumeTrue(demoDocumentAvailable(),
				"demo_document not loadable via JsonTypeSystem in this env — skipping projection tests");
	}

	@Nested
	@DisplayName("Baseline: demo_document loads and validates via the real type registry")
	class Baseline {
		@Test
		@DisplayName("demo_document loads and reports its field set")
		void demoDocumentLoads() {
			Type demo = loadDemoDocument();
			assertThat(demo).isNotNull();
			JsonNode fields = demo.getMetaNode().get("fields");
			assertThat(fields.isArray()).isTrue();
			// demo_document has ~11 fields — spot-check a couple of well-known ones
			assertThat(demo.getMetaNode().get("name").asText()).isEqualTo("document");
		}
	}

	@Nested
	@DisplayName("Projection 1: redact — masks PII fields")
	class Redact {
		@Test
		@DisplayName("Redact author (mask) and checksum (hash) on a demo_document-shaped JVS")
		void redactAuthorAndChecksum() {
			// demo_document has 'author' and 'checksum' — we add redact-group annotations to them.
			Type t = buildType("""
					{"name": "document_redact_test", "fields": [
					  {"name": "author",   "type": "core_string",
					   "groups": [{"name": "redact", "method": "mask"}]},
					  {"name": "checksum", "type": "core_string",
					   "groups": [{"name": "redact", "method": "hash"}]},
					  {"name": "filename", "type": "core_string"}
					]}""");
			ExecutionBuilder<RedactAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.redactFilter, new RedactFactory());

			JVS doc = JVS.read(DOC_JSON);
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			plan.getExecutor().project(pc);

			assertThat(doc.getString("author")).isEqualTo("***");
			// Hash is SHA-256 hex of the JSON string form ("chris.collins@hitorro.com" with quotes),
			// which is 64 hex chars — check length + hex-ness rather than exact bytes.
			String hashed = doc.getString("checksum");
			assertThat(hashed).matches("^[0-9a-f]{64}$");
			// Untouched field
			assertThat(doc.getString("filename")).isEqualTo("annual-report.pdf");
		}
	}

	@Nested
	@DisplayName("Projection 2: validate — group-scoped constraint checking")
	class Validate {
		@Test
		@DisplayName("Constraint failures on demo_document-shaped fields are collected in pc.violations")
		void validateGroupCatchesFailures() {
			Type t = buildType("""
					{"name": "document_validate_test", "fields": [
					  {"name": "author",     "type": "core_string", "format": "email",
					   "groups": [{"name": "validate", "method": "check"}]},
					  {"name": "file_size",  "type": "core_long", "minimum": 0, "maximum": 1000000,
					   "groups": [{"name": "validate", "method": "check"}]},
					  {"name": "classification", "type": "core_string",
					   "enum": ["public", "internal", "restricted"],
					   "groups": [{"name": "validate", "method": "check"}]},
					  {"name": "filename",   "type": "core_string"}
					]}""");
			ExecutionBuilder<ValidateAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.validateFilter, new ValidateFactory());

			// Bad doc: bogus email, size out of range, classification not in the enum.
			JVS bad = JVS.read("""
					{"author": "not-an-email", "file_size": 5000000, "classification": "top-secret",
					 "filename": "x.pdf"}""");
			ProjectionContext pc = new ProjectionContext();
			pc.source = bad;
			pc.target = new JVS();
			plan.getExecutor().project(pc);

			assertThat(pc.violations).hasSize(3);
			assertThat(pc.violations).anyMatch(v -> v.message().contains("email"));
			assertThat(pc.violations).anyMatch(v -> v.message().contains("maximum"));
			assertThat(pc.violations).anyMatch(v -> v.message().contains("enum"));
		}

		@Test
		@DisplayName("Clean document produces no violations")
		void validateClean() {
			Type t = buildType("""
					{"name": "document_validate_test2", "fields": [
					  {"name": "author",    "type": "core_string", "format": "email",
					   "groups": [{"name": "validate", "method": "check"}]},
					  {"name": "file_size", "type": "core_long", "minimum": 0,
					   "groups": [{"name": "validate", "method": "check"}]}
					]}""");
			ExecutionBuilder<ValidateAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.validateFilter, new ValidateFactory());

			JVS clean = JVS.read(DOC_JSON);
			ProjectionContext pc = new ProjectionContext();
			pc.source = clean;
			pc.target = new JVS();
			plan.getExecutor().project(pc);

			assertThat(pc.violations).isEmpty();
		}
	}

	@Nested
	@DisplayName("Projection 3: fingerprint — stable hash over projected fields")
	class Fingerprint {
		@Test
		@DisplayName("Same demo_document-shaped input produces the same digest")
		void deterministicHash() throws Exception {
			Type t = buildType("""
					{"name": "document_fp_test", "fields": [
					  {"name": "filename", "type": "core_string",
					   "groups": [{"name": "fingerprint", "method": "sha256"}]},
					  {"name": "version",  "type": "core_string",
					   "groups": [{"name": "fingerprint", "method": "sha256"}]},
					  {"name": "file_size","type": "core_long",
					   "groups": [{"name": "fingerprint", "method": "sha256"}]}
					]}""");
			ExecutionBuilder<FingerprintAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.fingerprintFilter, new FingerprintFactory());

			String a = digestOf(plan, JVS.read(DOC_JSON));
			String b = digestOf(plan, JVS.read(DOC_JSON));
			assertThat(a).isEqualTo(b).matches("^[0-9a-f]{64}$");
		}

		@Test
		@DisplayName("Different field values produce different digests")
		void differentInputsDifferentHash() throws Exception {
			Type t = buildType("""
					{"name": "document_fp_test2", "fields": [
					  {"name": "filename", "type": "core_string",
					   "groups": [{"name": "fingerprint"}]},
					  {"name": "version",  "type": "core_string",
					   "groups": [{"name": "fingerprint"}]}
					]}""");
			ExecutionBuilder<FingerprintAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.fingerprintFilter, new FingerprintFactory());

			String a = digestOf(plan, JVS.read(DOC_JSON));
			String b = digestOf(plan, JVS.read(DOC_JSON.replace("2.1", "2.2")));
			assertThat(a).isNotEqualTo(b);
		}

		private String digestOf(ExecutionBuilder<FingerprintAction> plan, JVS doc) throws Exception {
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			pc.fingerprint = MessageDigest.getInstance("SHA-256");
			plan.getExecutor().project(pc);
			return HexFormat.of().formatHex(pc.fingerprint.digest());
		}
	}

	@Nested
	@DisplayName("Projection 4: materialize — dereference references via DocumentStore")
	class Materialize {
		@Test
		@DisplayName("String reference (author ID) is replaced with the full referenced document")
		void materializeAuthorRef() {
			// demo_document has 'author' as a string. Here we treat it as a foreign-key reference
			// to a person doc stored in the DocumentStore.
			Type t = buildType("""
					{"name": "document_materialize_test", "fields": [
					  {"name": "author", "type": "core_string",
					   "groups": [{"name": "materialize", "method": "ref"}]},
					  {"name": "filename", "type": "core_string"}
					]}""");
			ExecutionBuilder<MaterializeAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.materializeFilter, new MaterializeFactory());

			// Payload with author pointing to an ID
			JVS doc = JVS.read("""
					{"author": "user-42", "filename": "annual-report.pdf"}""");

			InMemoryDocumentStore store = new InMemoryDocumentStore();
			store.put("user-42", jsonMapper.apply(
					"{\"name\":\"Chris Collins\",\"email\":\"chris@hitorro.com\"}"));

			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			pc.documentStore = store;
			plan.getExecutor().project(pc);

			JsonNode author = doc.getJsonNode().get("author");
			assertThat(author.isObject()).isTrue();
			assertThat(author.get("name").asText()).isEqualTo("Chris Collins");
			assertThat(author.get("email").asText()).isEqualTo("chris@hitorro.com");
		}

		@Test
		@DisplayName("Unresolvable references are left untouched (never silently dropped)")
		void unresolvedReferenceUntouched() {
			Type t = buildType("""
					{"name": "document_materialize_test2", "fields": [
					  {"name": "author", "type": "core_string",
					   "groups": [{"name": "materialize"}]}
					]}""");
			ExecutionBuilder<MaterializeAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.materializeFilter, new MaterializeFactory());

			JVS doc = JVS.read("{\"author\": \"user-nonexistent\"}");
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			pc.documentStore = new InMemoryDocumentStore(); // empty
			plan.getExecutor().project(pc);

			assertThat(doc.getString("author")).isEqualTo("user-nonexistent");
		}

		@Test
		@DisplayName("MaterializeAction.extractId handles the standard reference shapes")
		void extractIdShapes() {
			assertThat(MaterializeAction.extractId(jsonMapper.apply("\"user-1\""))).isEqualTo("user-1");
			assertThat(MaterializeAction.extractId(jsonMapper.apply("{\"did\":\"d1\"}"))).isEqualTo("d1");
			assertThat(MaterializeAction.extractId(jsonMapper.apply("{\"id\":42}"))).isEqualTo("42");
			assertThat(MaterializeAction.extractId(jsonMapper.apply("null"))).isNull();
			assertThat(MaterializeAction.extractId(jsonMapper.apply("{\"other\":\"x\"}"))).isNull();
		}
	}

	@Nested
	@DisplayName("Projection 5: i18n — flatten MLS envelopes into per-language scalars")
	class I18n {
		@Test
		@DisplayName("'content' MLS on demo_document flattens to French when lang=fr")
		void flattenToFrench() {
			Type t = buildType("""
					{"name": "document_i18n_test", "fields": [
					  {"name": "content", "type": "core_mls",
					   "groups": [{"name": "i18n", "method": "flatten"}]}
					]}""");
			ExecutionBuilder<I18nAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.i18nFilter, new I18nFactory());

			JVS doc = JVS.read(DOC_JSON);
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			// project(pc, lang) is a convenience; here we pass through project(pc) which uses
			// the default lang wired by the executor. Use the underlying API to set lang.
			plan.getExecutor().project(pc, pc.path, false, "fr");

			JsonNode content = doc.getJsonNode().get("content");
			assertThat(content.isTextual()).isTrue();
			assertThat(content.asText()).contains("renard");
		}

		@Test
		@DisplayName("Falls back to 'en' then first entry when requested lang not present")
		void fallbackOrder() {
			Type t = buildType("""
					{"name": "document_i18n_test2", "fields": [
					  {"name": "content", "type": "core_mls",
					   "groups": [{"name": "i18n"}]}
					]}""");
			ExecutionBuilder<I18nAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.i18nFilter, new I18nFactory());

			JVS doc = JVS.read(DOC_JSON);
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = new JVS();
			plan.getExecutor().project(pc, pc.path, false, "de"); // German not present → fallback to English

			assertThat(doc.getJsonNode().get("content").asText()).contains("quick brown fox");
		}

		@Test
		@DisplayName("pickText helper honours preferred → en → first ordering")
		void pickTextOrdering() {
			JsonNode mls = jsonMapper.apply(
					"[{\"lang\":\"en\",\"text\":\"en-text\"},{\"lang\":\"fr\",\"text\":\"fr-text\"}]");
			assertThat(I18nAction.pickText(mls, "fr")).isEqualTo("fr-text");
			assertThat(I18nAction.pickText(mls, "de")).isEqualTo("en-text");
			assertThat(I18nAction.pickText(mls, null)).isEqualTo("en-text");
		}
	}

	@Nested
	@DisplayName("Projection 6: vectorize — embed text fields into vectors")
	class Vectorize {
		@Test
		@DisplayName("'content' MLS on demo_document yields a fixed-dim vector under content_vector")
		void vectorizeContent() {
			Type t = buildType("""
					{"name": "document_vectorize_test", "fields": [
					  {"name": "content", "type": "core_mls",
					   "groups": [{"name": "vectorize", "method": "embed"}]}
					]}""");
			ExecutionBuilder<VectorizeAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.vectorizeFilter, new VectorizeFactory());

			JVS doc = JVS.read(DOC_JSON);
			JVS target = new JVS();
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = target;
			EmbeddingProvider embedder = new HashingEmbeddingProvider(64);
			pc.embeddingProvider = embedder;
			plan.getExecutor().project(pc);

			JsonNode vec = target.getJsonNode().get("content_vector");
			assertThat(vec).isNotNull();
			assertThat(vec.isArray()).isTrue();
			assertThat(vec.size()).isEqualTo(embedder.dimensions());
			// Should be L2-normalised (sum of squares ≈ 1) — cosine-friendly.
			double sq = 0;
			for (JsonNode f : vec) sq += f.doubleValue() * f.doubleValue();
			assertThat(sq).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
		}

		@Test
		@DisplayName("Same text → same vector; different text → different vector")
		void embeddingDeterminism() {
			EmbeddingProvider e = new HashingEmbeddingProvider(64);
			assertThat(e.embed("the quick brown fox")).isEqualTo(e.embed("the quick brown fox"));
			assertThat(e.embed("the quick brown fox")).isNotEqualTo(e.embed("something entirely different"));
		}

		@Test
		@DisplayName("Missing embedding provider is a graceful no-op")
		void noProviderIsNoOp() {
			Type t = buildType("""
					{"name": "document_vectorize_test2", "fields": [
					  {"name": "filename", "type": "core_string",
					   "groups": [{"name": "vectorize"}]}
					]}""");
			ExecutionBuilder<VectorizeAction> plan = plan(t, (Predicate<BaseT>) GroupNameFilter.vectorizeFilter, new VectorizeFactory());

			JVS doc = JVS.read(DOC_JSON);
			JVS target = new JVS();
			ProjectionContext pc = new ProjectionContext();
			pc.source = doc;
			pc.target = target;
			// pc.embeddingProvider deliberately unset — nothing should crash, nothing should be written.
			plan.getExecutor().project(pc);
			assertThat(target.getJsonNode().get("filename_vector")).isNull();
		}
	}
}
