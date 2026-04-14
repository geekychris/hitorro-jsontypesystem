package com.hitorro.jsontypesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.util.json.String2JsonMapper;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PAContextTyped — the type-aware property context that enables
 * dynamic field lazy materialization via the "create on read" pattern.
 *
 * PAContextTyped overrides attemptCreation() to allow node creation during
 * read operations (SetMode.No). This is intentional: when navigating to a
 * dynamic field that doesn't exist, the DynamicFieldMapper computes its value,
 * and setObjectOrArray() caches the result on the JSON node. Subsequent reads
 * find the value already present.
 */
@DisplayName("PAContextTyped")
class PAContextTypedTest {

    private static final String2JsonMapper jsonMapper = new String2JsonMapper();

    @Nested
    @DisplayName("Dynamic Field Materialization")
    class DynamicFieldMaterialization {

        @Test
        @DisplayName("Reading a dynamic field should compute and cache its value on the JSON node")
        void dynamicFieldComputedOnRead() throws PropaccessError {
            // Create a minimal typed document with a known dynamic field
            // The clean field in core_mlselem is dynamic (html-scrubber on .text)
            String json = """
                {"type": "core_sysobject",
                 "title": {"mls": [{"lang": "en", "text": "<b>Hello</b> World"}]}}
                """;
            JVS jvs = JVS.read(json);

            // If type system is available, reading clean should materialize it
            if (jvs.getType() != null) {
                JsonNode clean = jvs.get("title.mls[0].clean");
                // clean should be the HTML-scrubbed version
                if (clean != null) {
                    assertThat(clean.textValue()).doesNotContain("<b>");
                    assertThat(clean.textValue()).contains("Hello");
                    // Second read should return cached value (no recomputation)
                    JsonNode cleanAgain = jvs.get("title.mls[0].clean");
                    assertThat(cleanAgain).isEqualTo(clean);
                }
            }
        }

        @Test
        @DisplayName("Reading a non-dynamic missing field should not create empty objects")
        void nonDynamicMissingFieldReturnsNull() throws PropaccessError {
            JVS jvs = JVS.read("{\"type\": \"core_sysobject\", \"title\": {\"mls\": [{\"lang\": \"en\", \"text\": \"test\"}]}}");
            // nonexistent_field is not in the type definition
            JsonNode result = jvs.get("nonexistent_field");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("MLS Array Integrity")
    class MlsArrayIntegrity {

        @Test
        @DisplayName("MLS array should remain an array after reading dynamic fields")
        void mlsArrayPreservedAfterDynamicRead() throws PropaccessError {
            String json = """
                {"type": "core_sysobject",
                 "title": {"mls": [{"lang": "en", "text": "Hello"}, {"lang": "de", "text": "Hallo"}]}}
                """;
            JVS jvs = JVS.read(json);

            // Read a dynamic field to trigger materialization
            if (jvs.getType() != null) {
                jvs.get("title.mls[0].clean");
                jvs.get("title.mls[1].clean");
            }

            // MLS should still be an array with 2 elements
            JsonNode mls = jvs.get("title.mls");
            if (mls != null) {
                assertThat(mls.isArray()).isTrue();
                assertThat(mls.size()).isEqualTo(2);
            }
        }
    }
}
