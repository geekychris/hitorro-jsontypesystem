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
import com.fasterxml.jackson.databind.node.TextNode;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

/**
 * Flatten a {@code core_mls}-shaped multi-language envelope into a scalar string for the
 * projection's target language.
 *
 * <p>MLS envelope shape (see {@code core_mls.json}): an object with an {@code mls} array whose
 * elements are {@code {"text": "...", "lang": "en"}}. For {@code lang = "en"}, this action
 * picks the matching entry's {@code text} and writes it as a plain string at {@code path} on
 * the projection {@code target}.
 *
 * <p><b>Writes go to {@link ProjectionContext#target}</b>, not {@code source}. The source
 * envelope is preserved so callers can run the projection multiple times against the same
 * source document with different target languages — producing one flat per-language doc per
 * run — without losing the other languages.
 *
 * <p>Fallback order when the requested language isn't found: {@code en}, then the first
 * <em>valid</em> entry (one where both {@code lang} and {@code text} are textual). Malformed
 * entries — missing/wrong-type {@code lang} or {@code text} — are skipped rather than
 * short-circuited on. Non-MLS values (already a plain string, missing, wrong shape) are ignored.
 */
public class I18nAction implements ExecutorAction<ExecutionBuilder> {

    public I18nAction(final Field field, Group group, final Propaccess path) {
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        try {
            JsonNode val = pc.source.get(path);
            if (val == null || val.isNull() || !val.isObject()) return;
            JsonNode mlsArr = val.get("mls");
            if (mlsArr == null || !mlsArr.isArray() || mlsArr.isEmpty()) return;

            String text = pickText(mlsArr, lang);
            if (text != null && pc.target != null) {
                pc.target.set(path, new TextNode(text));
            }
        } catch (PropaccessError e) {
            Log.util.error("I18nAction failed for path %s: %s", path, e.getMessage());
        }
    }

    /**
     * Choose the best text from an MLS array. Iterates ALL entries collecting the first valid
     * preferred-lang match, first valid English match, and first valid any-lang match. An entry
     * is "valid" iff both {@code lang} and {@code text} are textual — malformed entries are
     * silently skipped, they don't short-circuit the search.
     */
    static String pickText(JsonNode mlsArr, String preferredLang) {
        String preferredText = null, englishText = null, firstValidText = null;
        for (JsonNode entry : mlsArr) {
            if (entry == null || !entry.isObject()) continue;
            JsonNode langNode = entry.get("lang");
            JsonNode textNode = entry.get("text");
            if (langNode == null || !langNode.isTextual()) continue;
            if (textNode == null || !textNode.isTextual()) continue;
            String l = langNode.textValue();
            String t = textNode.textValue();
            if (preferredLang != null && preferredLang.equalsIgnoreCase(l) && preferredText == null) {
                preferredText = t;
            }
            if ("en".equalsIgnoreCase(l) && englishText == null) {
                englishText = t;
            }
            if (firstValidText == null) firstValidText = t;
        }
        if (preferredText != null) return preferredText;
        if (englishText != null)   return englishText;
        return firstValidText;
    }
}
