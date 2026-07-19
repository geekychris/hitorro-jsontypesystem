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
 * picks the matching entry's {@code text} and overwrites the field at {@code path} with that
 * plain string.
 *
 * <p>Fallback order when the requested language isn't found: {@code en}, then the first entry
 * in the array. Non-MLS values (already a plain string, missing, wrong shape) are left alone.
 *
 * <p>Callers run this projection once per output language: set {@code pc.lang} equivalent, or
 * invoke {@code builder.getExecutor().project(pc, "fr")}. Running it repeatedly with different
 * langs is how you produce one flat document per language.
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
            if (text != null) {
                pc.source.set(path, new TextNode(text));
            }
        } catch (PropaccessError e) {
            Log.util.error("I18nAction failed for path %s: %s", path, e.getMessage());
        }
    }

    static String pickText(JsonNode mlsArr, String preferredLang) {
        JsonNode preferred = null, english = null;
        for (JsonNode entry : mlsArr) {
            if (!entry.isObject()) continue;
            JsonNode entryLang = entry.get("lang");
            String l = entryLang != null && entryLang.isTextual() ? entryLang.textValue() : null;
            if (preferredLang != null && preferredLang.equalsIgnoreCase(l)) {
                preferred = entry;
                break;
            }
            if ("en".equalsIgnoreCase(l) && english == null) english = entry;
        }
        JsonNode chosen = preferred != null ? preferred : (english != null ? english : mlsArr.get(0));
        JsonNode textNode = chosen == null ? null : chosen.get("text");
        return textNode != null && textNode.isTextual() ? textNode.textValue() : null;
    }
}
