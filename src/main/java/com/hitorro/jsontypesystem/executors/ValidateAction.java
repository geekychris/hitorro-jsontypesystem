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
import com.hitorro.jsontypesystem.FieldConstraints;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

/**
 * Run {@link FieldConstraints} against the value at the projected path and collect any
 * violations into {@link ProjectionContext#violations}. Complements {@link com.hitorro.jsontypesystem.JVSValidator
 * JVSValidator}'s whole-document validation by scoping the check to a specific projection group
 * — e.g. "the fields that ship with the index projection must all pass their constraints before
 * we index this doc".
 *
 * <p>Callers inspect {@code pc.violations} after {@code project(pc)} returns; a non-empty list
 * means the projection surfaced constraint failures.
 */
public class ValidateAction implements ExecutorAction<ExecutionBuilder> {

    private final JsonNode fieldMeta;

    public ValidateAction(final Field field, Group group, final Propaccess path) {
        // BaseT.getMetaNode() returns the raw JsonNode for the field — this is where the
        // constraint keys (minLength, pattern, etc.) live. Kept null-safe for defensiveness.
        this.fieldMeta = field == null ? null : field.getMetaNode();
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        if (fieldMeta == null || pc.violations == null) return;
        try {
            JsonNode val = pc.source.get(path);
            FieldConstraints.check(path.toString(), val, fieldMeta, pc.violations);
        } catch (PropaccessError e) {
            Log.util.error("ValidateAction failed for path %s: %s", path, e.getMessage());
        }
    }
}
