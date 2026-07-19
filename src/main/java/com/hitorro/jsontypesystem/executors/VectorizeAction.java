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
 * envelopes, the {@code text} of the first entry is embedded — callers who need per-language
 * vectors should run {@link I18nAction i18n} first to flatten MLS, then vectorize. Non-string
 * values are skipped (a warning is logged; consumers can wrap them in a text projection first).
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
            String text = extractText(val);
            if (text == null || text.isEmpty()) return;

            float[] vector = pc.embeddingProvider.embed(text);
            if (vector == null) return;

            ArrayNode arr = JsonNodeFactory.instance.arrayNode(vector.length);
            for (float f : vector) arr.add(f);

            String vectorPath = path.toString() + "_vector";
            pc.target.set(vectorPath, arr);
        } catch (PropaccessError e) {
            Log.util.error("VectorizeAction failed for path %s: %s", path, e.getMessage());
        }
    }

    private static String extractText(JsonNode val) {
        if (val == null || val.isNull()) return null;
        if (val.isTextual()) return val.textValue();
        if (val.isObject()) {
            // MLS envelope shorthand — take the first entry's text.
            JsonNode mls = val.get("mls");
            if (mls != null && mls.isArray() && !mls.isEmpty()) {
                JsonNode first = mls.get(0);
                if (first != null && first.isObject()) {
                    JsonNode t = first.get("text");
                    if (t != null && t.isTextual()) return t.textValue();
                }
            }
        }
        return null;
    }
}
