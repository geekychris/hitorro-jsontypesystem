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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.jsontypesystem.projections.EmbeddingProvider;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

/**
 * Feed the value at each projected path into
 * {@link ProjectionContext#embeddingProvider} and store the resulting float vector on the
 * target document under {@code <path>_vector}. Downstream systems (search indexers, dedup
 * pipelines) consume the vector for semantic operations.
 *
 * <p>For scalar text values, the string form of the value is embedded directly. For MLS
 * envelopes, the projection language's entry is selected using the same preferred → English →
 * first-valid fallback as {@link I18nAction#pickText}. Callers who need per-language vectors
 * run this projection once per output language. Non-string values are skipped silently.
 *
 * <p>The returned vector's length is validated against
 * {@link EmbeddingProvider#dimensions()}; a mismatch is rejected at this boundary (nothing is
 * written; a warning is logged) so downstream storage can rely on a consistent shape.
 */
public class VectorizeAction implements ExecutorAction<ExecutionBuilder> {

    public VectorizeAction(final Field field, Group group, final Propaccess path) {
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        if (pc.embeddingProvider == null) return;
        try {
            JsonNode val = pc.source.get(path);
            String text = extractText(val, lang);
            if (text == null || text.isEmpty()) return;

            float[] vector = pc.embeddingProvider.embed(text);
            if (vector == null) return;

            int expected = pc.embeddingProvider.dimensions();
            if (vector.length != expected) {
                Log.util.warn("VectorizeAction: provider returned vector of length %d, " +
                        "expected %d (from dimensions()); skipping path %s",
                        vector.length, expected, path);
                return;
            }

            ArrayNode arr = JsonNodeFactory.instance.arrayNode(vector.length);
            for (float f : vector) arr.add(f);

            String vectorPath = path.toString() + "_vector";
            pc.target.set(vectorPath, arr);
        } catch (PropaccessError e) {
            Log.util.error("VectorizeAction failed for path %s: %s", path, e.getMessage());
        }
    }

    /** Language-aware text extraction. Reuses {@link I18nAction#pickText}'s fallback for MLS. */
    static String extractText(JsonNode val, String lang) {
        if (val == null || val.isNull()) return null;
        if (val.isTextual()) return val.textValue();
        if (val.isObject()) {
            JsonNode mls = val.get("mls");
            if (mls != null && mls.isArray() && !mls.isEmpty()) {
                return I18nAction.pickText(mls, lang);
            }
        }
        return null;
    }
}
