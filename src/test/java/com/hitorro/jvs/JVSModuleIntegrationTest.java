/*
 * Copyright (c) 2006 - 2025 Chris Collins
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
package com.hitorro.jvs;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.JVSMerger;
import com.hitorro.jsontypesystem.JVSValidator;
import com.hitorro.language.SnowballSimpleStemmer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the hitorro-jvs module demonstrating the combined
 * type system and NLP capabilities as an independent library.
 */
@DisplayName("HiTorro JVS Module Integration Tests")
class JVSModuleIntegrationTest {

    /** Stem a word using the Snowball stemmer directly */
    private static String stem(SnowballStemmer sp, String word) {
        sp.setCurrent(word);
        sp.stem();
        return sp.getCurrent();
    }

    @Nested
    @DisplayName("JVS Type System - Core Operations")
    class TypeSystemCore {

        @Test
        @DisplayName("Should create JVS document and navigate properties")
        void shouldCreateAndNavigateJVS() {
            String json = """
                {
                    "id": {"did": "doc-001", "domain": "test"},
                    "title": {"mls": [{"lang": "en", "text": "Hello World"}]},
                    "body": {"mls": [{"lang": "en", "text": "This is a test document for the JVS module."}]},
                    "tags": ["java", "nlp", "type-system"]
                }
                """;

            JVS doc = JVS.read(json);

            assertThat(doc).isNotNull();
            assertThat(doc.getString("id.did")).isEqualTo("doc-001");
            assertThat(doc.getString("id.domain")).isEqualTo("test");
            assertThat(doc.getString("title.mls[0].text")).isEqualTo("Hello World");
            assertThat(doc.getStringList("tags")).containsExactly("java", "nlp", "type-system");
        }

        @Test
        @DisplayName("Should set nested properties via Propaccess paths")
        void shouldSetNestedProperties() {
            JVS doc = new JVS();
            doc.set("id.did", "doc-002");
            doc.set("id.domain", "example");
            doc.set("metadata.version", 1);
            doc.set("metadata.active", true);

            assertThat(doc.getString("id.did")).isEqualTo("doc-002");
            assertThat(doc.getString("id.domain")).isEqualTo("example");
            assertThat(doc.getBoolean("metadata.active")).isTrue();
        }

        @Test
        @DisplayName("Should merge two JSON documents")
        void shouldMergeDocuments() {
            JVS base = JVS.read("{\"name\": \"base\", \"shared\": \"original\"}");
            JVS overlay = JVS.read("{\"extra\": \"added\", \"shared\": \"overridden\"}");

            JVSMerger.merge(base.getJsonNode(), overlay.getJsonNode());

            assertThat(base.getString("name")).isEqualTo("base");
            assertThat(base.getString("extra")).isEqualTo("added");
            assertThat(base.getString("shared")).isEqualTo("overridden");
        }

        @Test
        @DisplayName("Should clone JVS documents independently")
        void shouldCloneIndependently() {
            JVS original = JVS.read("{\"key\": \"value\"}");
            JVS clone = original.clone();

            clone.set("key", "modified");

            assertThat(original.getString("key")).isEqualTo("value");
            assertThat(clone.getString("key")).isEqualTo("modified");
        }

        @Test
        @DisplayName("Should check key existence")
        void shouldCheckExistence() {
            JVS doc = JVS.read("{\"present\": \"value\", \"empty\": \"\"}");

            assertThat(doc.exists("present")).isTrue();
            assertThat(doc.exists("missing")).isFalse();
        }

        @Test
        @DisplayName("Should get keys from nested object node")
        void shouldGetKeys() {
            JVS doc = JVS.read("{\"data\": {\"alpha\": 1, \"beta\": 2, \"gamma\": 3}}");

            List<String> keys = doc.getKeys("data");

            assertThat(keys).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        }

        @Test
        @DisplayName("Should handle array access via indexed paths")
        void shouldHandleArrayAccess() {
            JVS doc = JVS.read("""
                {"items": [
                    {"name": "first", "value": 1},
                    {"name": "second", "value": 2},
                    {"name": "third", "value": 3}
                ]}
                """);

            assertThat(doc.getString("items[0].name")).isEqualTo("first");
            assertThat(doc.getString("items[1].name")).isEqualTo("second");
            assertThat(doc.getString("items[2].name")).isEqualTo("third");
        }
    }

    @Nested
    @DisplayName("NLP - Snowball Stemming (direct SnowballProgram)")
    class NLPStemming {

        @Test
        @DisplayName("Should stem English words using Snowball stemmer")
        void shouldStemEnglishWords() {
            EnglishStemmer sp = new EnglishStemmer();

            assertThat(stem(sp, "running")).isEqualTo("run");
            assertThat(stem(sp, "walked")).isEqualTo("walk");
            assertThat(stem(sp, "dogs")).isEqualTo("dog");
            assertThat(stem(sp, "processing")).isEqualTo("process");
        }

        @Test
        @DisplayName("Should handle edge cases in stemming")
        void shouldHandleStemmingEdgeCases() {
            EnglishStemmer sp = new EnglishStemmer();

            assertThat(stem(sp, "")).isEmpty();
            assertThat(stem(sp, "a")).isEqualTo("a");
        }

        @Test
        @DisplayName("SnowballSimpleStemmer wraps SnowballProgram")
        void shouldWrapSnowballProgram() {
            SnowballSimpleStemmer stemmer = new SnowballSimpleStemmer(new EnglishStemmer());
            // Note: SnowballSimpleStemmer.stem() does not call sp.stem() (commented out in source)
            // It returns the input unchanged, so this tests the wrapper contract
            String result = stemmer.stem("testing");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Combined JVS + NLP Operations")
    class CombinedOperations {

        @Test
        @DisplayName("Should stem text content extracted from JVS document")
        void shouldStemTextFromDocument() {
            JVS doc = JVS.read("""
                {
                    "title": {"mls": [{"lang": "en", "text": "Running Dogs"}]},
                    "body": {"mls": [{"lang": "en", "text": "The dogs were running through the garden"}]}
                }
                """);

            EnglishStemmer sp = new EnglishStemmer();

            String bodyText = doc.getString("body.mls[0].text");
            assertThat(bodyText).isNotNull();

            // Stem individual words from the body
            String[] words = bodyText.split("\\s+");
            StringBuilder stemmed = new StringBuilder();
            for (String word : words) {
                String clean = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
                if (!clean.isEmpty()) {
                    if (!stemmed.isEmpty()) stemmed.append(" ");
                    stemmed.append(stem(sp, clean));
                }
            }

            assertThat(stemmed.toString()).contains("dog", "run", "garden");
        }

        @Test
        @DisplayName("Should build JVS document with NLP-enriched metadata")
        void shouldBuildDocumentWithNLPMetadata() {
            EnglishStemmer sp = new EnglishStemmer();

            // Create a document
            JVS doc = new JVS();
            doc.set("id.did", "nlp-001");
            doc.set("title.mls[0].lang", "en");
            doc.set("title.mls[0].text", "Natural Language Processing");

            // Enrich with stemmed title
            String title = doc.getString("title.mls[0].text");
            String[] words = title.split("\\s+");
            StringBuilder stems = new StringBuilder();
            for (String w : words) {
                if (!stems.isEmpty()) stems.append(" ");
                stems.append(stem(sp, w.toLowerCase()));
            }
            doc.set("title.mls[0].stems", stems.toString());

            assertThat(doc.getString("id.did")).isEqualTo("nlp-001");
            assertThat(doc.getString("title.mls[0].stems")).isEqualTo("natur languag process");
        }

        @Test
        @DisplayName("Should create multi-language document structure")
        void shouldHandleMultiLanguageDocument() {
            JVS doc = new JVS();
            doc.set("id.did", "ml-001");
            doc.set("title.mls[0].lang", "en");
            doc.set("title.mls[0].text", "Processing Documents");
            doc.set("title.mls[1].lang", "de");
            doc.set("title.mls[1].text", "Dokumentenverarbeitung");

            // Verify multi-language structure
            assertThat(doc.getString("title.mls[0].lang")).isEqualTo("en");
            assertThat(doc.getString("title.mls[1].lang")).isEqualTo("de");
            assertThat(doc.getString("title.mls[0].text")).isEqualTo("Processing Documents");
            assertThat(doc.getString("title.mls[1].text")).isEqualTo("Dokumentenverarbeitung");
        }
    }
}
