/*
 * Copyright (c) 2006-2025 Chris Collins
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
package com.hitorro.jsontypesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.json.String2JsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.hitorro.util.basefile.tools.EnvBaseFiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("JVSValidator Tests")
class JVSValidatorTest {

	private static final String2JsonMapper jsonMapper = new String2JsonMapper();

	private static JsonNode typeDef(String json) {
		return jsonMapper.apply(json);
	}

	@Nested
	@DisplayName("Structural validation")
	class StructuralValidation {

		@Test
		@DisplayName("Valid document should produce no violations")
		void validDocument() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "title", "type": "core_string"},
						{"name": "count", "type": "core_long"}
					]}""");

			JVS doc = JVS.read("{\"title\":\"hello\",\"count\":42}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).isEmpty();
		}

		@Test
		@DisplayName("Missing field should report violation")
		void missingField() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "title", "type": "core_string"},
						{"name": "count", "type": "core_long"}
					]}""");

			JVS doc = JVS.read("{\"title\":\"hello\"}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("count") && v.level() == JVSValidator.Level.WARNING);
		}

		@Test
		@DisplayName("Extra field not in type should report info violation")
		void extraField() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "title", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{\"title\":\"hello\",\"extra\":\"unexpected\"}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("extra") && v.level() == JVSValidator.Level.INFO);
		}

		@Test
		@DisplayName("Empty document should report all fields as missing")
		void emptyDocument() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "a", "type": "core_string"},
						{"name": "b", "type": "core_string"},
						{"name": "c", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).hasSize(3);
		}
	}

	@Nested
	@DisplayName("Type mismatch detection")
	class TypeMismatch {

		@Test
		@DisplayName("String where number expected should report error")
		void stringWhereNumberExpected() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "count", "type": "core_long"}
					]}""");

			JVS doc = JVS.read("{\"count\":\"not a number\"}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("count") && v.level() == JVSValidator.Level.ERROR);
		}

		@Test
		@DisplayName("Number where string expected should report error")
		void numberWhereStringExpected() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "name", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{\"name\":42}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("name") && v.level() == JVSValidator.Level.ERROR);
		}

		@Test
		@DisplayName("Scalar where vector expected should report error")
		void scalarWhereVectorExpected() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "tags", "type": "core_string", "vector": true}
					]}""");

			JVS doc = JVS.read("{\"tags\":\"single\"}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("tags") && v.level() == JVSValidator.Level.ERROR);
		}

		@Test
		@DisplayName("Array where scalar expected should report error")
		void arrayWhereScalarExpected() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "name", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{\"name\":[\"a\",\"b\"]}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("name") && v.level() == JVSValidator.Level.ERROR);
		}
	}

	@Nested
	@DisplayName("Null handling")
	class NullHandling {

		@Test
		@DisplayName("Null value should report warning")
		void nullField() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "data", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{\"data\":null}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).anyMatch(v ->
					v.path().equals("data") && v.level() == JVSValidator.Level.WARNING);
		}
	}

	@Nested
	@DisplayName("Dynamic field handling")
	class DynamicFields {

		@Test
		@DisplayName("Missing dynamic field should not be a violation")
		void missingDynamicField() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "source", "type": "core_string"},
						{"name": "computed", "type": "core_string",
						 "dynamic": {"class": "dynamic-mapper", "mapper": {"class": "fp-hash"}, "fields": [".source"]}}
					]}""");

			JVS doc = JVS.read("{\"source\":\"hello\"}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).noneMatch(v -> v.path().equals("computed"));
		}
	}

	@Nested
	@DisplayName("Violation model")
	class ViolationModel {

		@Test
		@DisplayName("Violation should have path, message, and level")
		void violationStructure() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "required", "type": "core_string"}
					]}""");

			JVS doc = JVS.read("{}");

			List<JVSValidator.Violation> violations = JVSValidator.validateAgainstDefinition(doc, def);
			assertThat(violations).hasSize(1);

			JVSValidator.Violation v = violations.get(0);
			assertThat(v.path()).isEqualTo("required");
			assertThat(v.message()).isNotEmpty();
			assertThat(v.level()).isEqualTo(JVSValidator.Level.WARNING);
		}

		@Test
		@DisplayName("Report should produce readable summary")
		void reportFormat() {
			JsonNode def = typeDef("""
					{"name": "test", "fields": [
						{"name": "a", "type": "core_string"},
						{"name": "b", "type": "core_long"}
					]}""");

			JVS doc = JVS.read("{\"b\":\"wrong type\"}");

			String report = JVSValidator.reportAgainstDefinition(doc, def);
			assertThat(report).contains("a");
			assertThat(report).contains("b");
		}
	}

	@Nested
	@DisplayName("End-to-end: type loaded from disk via JsonTypeSystem")
	class LoadedFromConfigDir {

		/**
		 * Full-pipeline coverage: write a constrained type-def file into the runtime
		 * config directory, load it through {@link JsonTypeSystem#getType(String)},
		 * and validate a JVS against the resulting Type.
		 *
		 * <p>Runs only when {@code $HT_HOME/config/types/} exists and is writable.
		 * Surefire sets {@code HT_HOME} to {@code session.executionRootDirectory}, so
		 * this passes when the suite runs from the parent repo (the usual path).
		 */
		private static final String TYPE_NAME = "demo_constrained_person";
		private static Path typeFile;

		@BeforeAll
		static void writeConstrainedTypeFile() throws Exception {
			// JsonTypeSystem's static cache is built off of EnvBaseFiles.getBinConfigBaseFile() at
			// class-load time. If HT_BIN wasn't set before that first class-load, the cache
			// points somewhere useless and getType always returns null. To make this test
			// deterministic, we find the real config/types dir that ships with the monorepo
			// and set HT_BIN to point above it BEFORE any getType call.
			File typesDir = null;
			for (File candidate : new File[]{
					new File("config/types"),                                 // CWD is monorepo root
					new File("../config/types"),                              // CWD is a submodule
					new File(System.getProperty("user.dir"), "config/types")
			}) {
				if (candidate.isDirectory() && new File(candidate, "core_string.json").isFile()) {
					typesDir = candidate.getAbsoluteFile();
					break;
				}
			}
			assumeTrue(typesDir != null,
					"could not locate a config/types directory containing core_string.json — " +
							"skipping loaded-type test");

			// Belt-and-braces: set HT_BIN so a caller running this class in isolation (no
			// pom-level HT_BIN) still hits the right dir, as long as JsonTypeSystem hasn't
			// already been class-loaded with a bad config path.
			System.setProperty("HT_BIN", typesDir.getParentFile().getParentFile().getAbsolutePath());

			// Verify JsonTypeSystem can actually resolve — if not, the static cache was
			// pinned to a bad config dir earlier in this JVM. Skip rather than fail so
			// running the class from a wrong CWD doesn't produce a red herring.
			Type anchor = JsonTypeSystem.getMe().getType("core_string");
			assumeTrue(anchor != null,
					"JsonTypeSystem cached a bad config dir before this test class ran — skipping");

			typeFile = typesDir.toPath().resolve(TYPE_NAME + ".json");
			String json = """
					{
					  "name": "%s",
					  "fields": [
					    {"name": "email",  "type": "core_string", "format": "email"},
					    {"name": "age",    "type": "core_long",   "minimum": 0, "maximum": 150},
					    {"name": "status", "type": "core_string",
					     "enum": ["active", "banned", "pending"]}
					  ]
					}""".formatted(TYPE_NAME);
			Files.writeString(typeFile, json);
		}

		@AfterAll
		static void removeConstrainedTypeFile() throws Exception {
			if (typeFile != null) Files.deleteIfExists(typeFile);
		}

		@Test
		@DisplayName("Type loaded from disk via JsonTypeSystem carries constraints through validation")
		void loadedFromDiskEnforcesConstraints() {
			assumeTrue(typeFile != null && Files.exists(typeFile), "type file setup skipped");

			Type person = JsonTypeSystem.getMe().getType(TYPE_NAME);
			assertThat(person)
					.as("Type must be loadable from disk via JsonTypeSystem")
					.isNotNull();
			// Prove the file was really parsed — constraint metadata should be present
			// on the loaded Type's metaNode (this is what JVSValidator reads).
			JsonNode fields = person.getMetaNode().get("fields");
			assertThat(fields.isArray()).isTrue();
			assertThat(fields.get(1).get("maximum").asInt()).isEqualTo(150);

			JVS clean = JVS.read("""
					{
					  "email":  "chris@hitorro.com",
					  "age":    42,
					  "status": "active"
					}""");
			assertThat(JVSValidator.validate(clean, person))
					.as("Well-formed document should have no ERROR violations")
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);

			JVS dirty = JVS.read("""
					{
					  "email":  "not-an-email",
					  "age":    999,
					  "status": "unknown"
					}""");
			List<JVSValidator.Violation> vs = JVSValidator.validate(dirty, person);
			assertThat(vs).anyMatch(v -> v.path().equals("email")  && v.message().contains("email"));
			assertThat(vs).anyMatch(v -> v.path().equals("age")    && v.message().contains("maximum"));
			assertThat(vs).anyMatch(v -> v.path().equals("status") && v.message().contains("enum"));
		}
	}

	@Nested
	@DisplayName("End-to-end: real Type object through JsonTypeSystem loading")
	class EndToEndWithLoadedType {

		/**
		 * Build a real Type in-memory (no on-disk config, no HT_HOME dependency) so we can
		 * prove constraints survive the JsonTypeSystem loading path — not just the raw
		 * JsonNode overload used elsewhere in this test class.
		 */
		private Type loadedType(String typeJson) {
			JsonNode node = typeDef(typeJson);
			Type t = new Type();
			t.init(node);
			return t;
		}

		@Test
		@DisplayName("validate(JVS, Type) enforces constraints on a fully-loaded Type")
		void loadedTypeEnforcesConstraints() {
			Type person = loadedType("""
					{
					  "name": "person",
					  "fields": [
					    {"name": "id",    "type": "core_string", "format": "uuid"},
					    {"name": "email", "type": "core_string", "format": "email"},
					    {"name": "age",   "type": "core_long",   "minimum": 0, "maximum": 150},
					    {"name": "status","type": "core_string", "enum": ["active", "banned"]}
					  ]
					}""");

			// Sanity: constraints survived the Type.init() round-trip via getMetaNode().
			JsonNode fields = person.getMetaNode().get("fields");
			assertThat(fields.get(0).get("format").asText()).isEqualTo("uuid");
			assertThat(fields.get(1).get("format").asText()).isEqualTo("email");
			assertThat(fields.get(2).get("minimum").asInt()).isEqualTo(0);
			assertThat(fields.get(3).get("enum").isArray()).isTrue();

			JVS clean = JVS.read("""
					{
					  "id":     "550e8400-e29b-41d4-a716-446655440000",
					  "email":  "chris@hitorro.com",
					  "age":    42,
					  "status": "active"
					}""");
			assertThat(JVSValidator.validate(clean, person))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);

			JVS dirty = JVS.read("""
					{
					  "id":     "not-a-uuid",
					  "email":  "definitely@ not an email",
					  "age":    200,
					  "status": "trashed"
					}""");
			List<JVSValidator.Violation> vs = JVSValidator.validate(dirty, person);
			assertThat(vs).anyMatch(v -> v.path().equals("id")     && v.message().contains("UUID"));
			assertThat(vs).anyMatch(v -> v.path().equals("email")  && v.message().contains("email"));
			assertThat(vs).anyMatch(v -> v.path().equals("age")    && v.message().contains("maximum"));
			assertThat(vs).anyMatch(v -> v.path().equals("status") && v.message().contains("enum"));
		}

		@Test
		@DisplayName("Handing a JVS directly with a full document-type example works")
		void fullDocumentExample() {
			Type article = loadedType("""
					{
					  "name": "article",
					  "fields": [
					    {"name": "slug",     "type": "core_string",
					     "pattern": "^[a-z0-9-]+$", "minLength": 3, "maxLength": 64},
					    {"name": "author",   "type": "core_string", "minLength": 1},
					    {"name": "title",    "type": "core_string", "minLength": 1, "maxLength": 200},
					    {"name": "published","type": "core_string", "format": "date-time"},
					    {"name": "wordCount","type": "core_long",   "minimum": 0},
					    {"name": "canonical","type": "core_string", "format": "uri"},
					    {"name": "status",   "type": "core_string",
					     "enum": ["draft", "published", "archived"]}
					  ]
					}""");

			JVS goodArticle = JVS.read("""
					{
					  "slug":      "hello-world",
					  "author":    "chris",
					  "title":     "Hello, world",
					  "published": "2026-07-15T09:00:00Z",
					  "wordCount": 1247,
					  "canonical": "https://example.com/hello-world",
					  "status":    "published"
					}""");
			assertThat(JVSValidator.validate(goodArticle, article))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);

			JVS badArticle = JVS.read("""
					{
					  "slug":      "Hello World",
					  "author":    "",
					  "title":     "OK",
					  "published": "last Tuesday",
					  "wordCount": -5,
					  "canonical": "not a url",
					  "status":    "trashed"
					}""");
			List<JVSValidator.Violation> vs = JVSValidator.validate(badArticle, article);
			assertThat(vs).anyMatch(v -> v.path().equals("slug")      && v.message().contains("pattern"));
			assertThat(vs).anyMatch(v -> v.path().equals("author")    && v.message().contains("minLength"));
			assertThat(vs).anyMatch(v -> v.path().equals("published") && v.message().contains("date-time"));
			assertThat(vs).anyMatch(v -> v.path().equals("wordCount") && v.message().contains("minimum"));
			assertThat(vs).anyMatch(v -> v.path().equals("canonical")
					&& (v.message().contains("URI") || v.message().contains("scheme")));
			assertThat(vs).anyMatch(v -> v.path().equals("status")    && v.message().contains("enum"));
		}

		@Test
		@DisplayName("report(JVS, Type) formats violations on a loaded Type")
		void reportViaLoadedType() {
			Type contact = loadedType("""
					{"name": "contact", "fields": [
					    {"name": "email", "type": "core_string", "format": "email"}
					]}""");
			String report = JVSValidator.report(JVS.read("{\"email\":\"nope\"}"), contact);
			assertThat(report).contains("email");
			assertThat(report).contains("contact");
		}
	}

	@Nested
	@DisplayName("Constraint validation")
	class ConstraintValidation {

		@Test
		@DisplayName("minLength / maxLength on string fields")
		void stringLengthBounds() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "code", "type": "core_string", "minLength": 3, "maxLength": 8}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"code\":\"ab\"}"), def))
					.anyMatch(v -> v.message().contains("minLength"));
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"code\":\"way too long\"}"), def))
					.anyMatch(v -> v.message().contains("maxLength"));
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"code\":\"fine\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
		}

		@Test
		@DisplayName("pattern must match full value")
		void patternMatch() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "sku", "type": "core_string", "pattern": "^[A-Z]{3}-[0-9]{4}$"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"sku\":\"ABC-1234\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"sku\":\"abc-1234\"}"), def))
					.anyMatch(v -> v.message().contains("pattern"));
		}

		@Test
		@DisplayName("enum rejects values outside the allowed set")
		void enumMembership() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "status", "type": "core_string", "enum": ["draft", "published", "archived"]}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"status\":\"published\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"status\":\"trashed\"}"), def))
					.anyMatch(v -> v.message().contains("enum"));
		}

		@Test
		@DisplayName("minimum / maximum on numeric fields")
		void numericBounds() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "age", "type": "core_long", "minimum": 0, "maximum": 150}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"age\":42}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"age\":-1}"), def))
					.anyMatch(v -> v.message().contains("minimum"));
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"age\":200}"), def))
					.anyMatch(v -> v.message().contains("maximum"));
		}

		@Test
		@DisplayName("format=email flags obvious non-emails")
		void formatEmail() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "contact", "type": "core_string", "format": "email"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"contact\":\"chris@hitorro.com\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"contact\":\"not-an-email\"}"), def))
					.anyMatch(v -> v.message().contains("email"));
		}

		@Test
		@DisplayName("format=date-time enforces ISO-8601")
		void formatDateTime() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "ts", "type": "core_string", "format": "date-time"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"ts\":\"2026-07-15T09:00:00Z\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"ts\":\"yesterday\"}"), def))
					.anyMatch(v -> v.message().contains("date-time"));
		}

		@Test
		@DisplayName("format=uri requires an absolute URI (scheme present)")
		void formatUri() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "url", "type": "core_string", "format": "uri"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"url\":\"https://example.com/x\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"url\":\"just a path/nothing\"}"), def))
					.anyMatch(v -> v.message().contains("URI") || v.message().contains("scheme"));
		}

		@Test
		@DisplayName("format=uuid checks canonical form")
		void formatUuid() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "id", "type": "core_string", "format": "uuid"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(
					JVS.read("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"id\":\"not-a-uuid\"}"), def))
					.anyMatch(v -> v.message().contains("UUID"));
		}

		@Test
		@DisplayName("Unknown format values are ignored (forward-compat)")
		void unknownFormatIgnored() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "custom", "type": "core_string", "format": "hitorro-locale"}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"custom\":\"anything\"}"), def))
					.noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
		}

		@Test
		@DisplayName("Malformed regex in type-def is reported, not silently swallowed")
		void malformedPattern() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "x", "type": "core_string", "pattern": "["}
					]}""");
			assertThat(JVSValidator.validateAgainstDefinition(JVS.read("{\"x\":\"abc\"}"), def))
					.anyMatch(v -> v.message().contains("invalid pattern"));
		}

		@Test
		@DisplayName("Constraints on absent fields are ignored (missing-field is separate)")
		void constraintsSkipMissing() {
			JsonNode def = typeDef("""
					{"name": "t", "fields": [
						{"name": "opt", "type": "core_string", "minLength": 5}
					]}""");
			var vs = JVSValidator.validateAgainstDefinition(JVS.read("{}"), def);
			// missing warning is fine; constraint ERROR is not
			assertThat(vs).noneMatch(v -> v.level() == JVSValidator.Level.ERROR);
		}
	}
}
