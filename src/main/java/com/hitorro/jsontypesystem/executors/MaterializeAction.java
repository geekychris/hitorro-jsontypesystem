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
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

/**
 * Dereference a reference-shaped field via the {@link ProjectionContext#documentStore} SPI and
 * replace the reference with the full target document at the same path.
 *
 * <p>Reference shapes recognised (in priority order):
 * <ol>
 *   <li>A scalar string — treated as the document ID directly.</li>
 *   <li>A {@code core_id}-shaped object with its computed {@code id} field already populated
 *       (the {@code multivalue-merger} dynamic field defined on {@code core_id}) — that value is
 *       the unique lookup key.</li>
 *   <li>A {@code core_id}-shaped object with only {@code domain} and {@code did} populated (the
 *       {@code id} dynamic hasn't fired yet) — the key is synthesised as
 *       {@code domain + ":" + did}, matching the default {@link com.hitorro.jsontypesystem.dynamic.MultiValueMergerDM
 *       MultiValueMergerDM} join. This is the unique-across-domains form; {@code did} alone is
 *       <b>not</b> unique because two different domains can share the same {@code did}.</li>
 * </ol>
 * Anything else is left in place. When the store returns {@code null} the reference is left
 * untouched — materialize does not silently drop unresolvable IDs.
 */
public class MaterializeAction implements ExecutorAction<ExecutionBuilder> {

    public MaterializeAction(final Field field, Group group, final Propaccess path) {
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        if (pc.documentStore == null) return;
        try {
            JsonNode val = pc.source.get(path);
            if (val == null || val.isNull()) return;
            String id = extractId(val);
            if (id == null) return;
            JsonNode resolved = pc.documentStore.getDocument(id);
            if (resolved != null) {
                pc.source.set(path, resolved);
            }
        } catch (PropaccessError e) {
            Log.util.error("MaterializeAction failed for path %s: %s", path, e.getMessage());
        }
    }

    /** Default separator used by {@code core_id}'s {@code multivalue-merger} — see MultiValueMergerDM. */
    private static final String CORE_ID_SEPARATOR = ":";

    /** Pull an ID string out of common reference shapes. See class Javadoc for priority order. */
    static String extractId(JsonNode ref) {
        if (ref == null || ref.isNull()) return null;
        if (ref.isTextual()) return ref.textValue();
        if (!ref.isObject()) return null;

        // 1. Already-computed core_id.id (the multivalue-merger dynamic result).
        JsonNode id = ref.get("id");
        if (id != null) {
            if (id.isTextual() && !id.textValue().isEmpty()) return id.textValue();
            if (id.isNumber()) return id.asText();
        }
        // 2. core_id in the pre-dynamic state: synthesise domain:did.
        JsonNode domain = ref.get("domain");
        JsonNode did = ref.get("did");
        boolean hasDomain = domain != null && domain.isTextual();
        boolean hasDid    = did != null    && did.isTextual();
        if (hasDomain && hasDid) {
            return domain.textValue() + CORE_ID_SEPARATOR + did.textValue();
        }
        // Not a shape we understand.
        return null;
    }
}
