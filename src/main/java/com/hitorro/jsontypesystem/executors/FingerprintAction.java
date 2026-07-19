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

import java.nio.charset.StandardCharsets;

/**
 * Feed the value at each projected path into {@link ProjectionContext#fingerprint}. The caller
 * initialises the {@code MessageDigest} before projection and reads the finalised digest bytes
 * afterward.
 *
 * <p>Value contribution shape: {@code "<path>=<value>\0"}. Concatenating with a NUL delimiter is
 * ambiguity-safe (path and value can each contain any character, but not the NUL byte in
 * printable JSON), so re-ordering the fields in the type definition changes the hash — which
 * is desired: schema changes must invalidate cached fingerprints.
 *
 * <p>Null / missing values contribute {@code "<path>=\0"}, so absence is distinguishable from
 * empty-string presence.
 */
public class FingerprintAction implements ExecutorAction<ExecutionBuilder> {

    public FingerprintAction(final Field field, Group group, final Propaccess path) {
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        if (pc.fingerprint == null) return;
        try {
            JsonNode val = pc.source.get(path);
            String pathStr = path.toString();
            pc.fingerprint.update(pathStr.getBytes(StandardCharsets.UTF_8));
            pc.fingerprint.update((byte) '=');
            if (val != null && !val.isNull()) {
                // Use asText() when scalar, toString() for structured values — canonical enough
                // for a fingerprint without a full canonicalizer.
                String s = val.isValueNode() ? val.asText() : val.toString();
                pc.fingerprint.update(s.getBytes(StandardCharsets.UTF_8));
            }
            pc.fingerprint.update((byte) 0);
        } catch (PropaccessError e) {
            Log.util.error("FingerprintAction failed for path %s: %s", path, e.getMessage());
        }
    }
}
