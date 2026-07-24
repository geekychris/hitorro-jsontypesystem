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
 * <p>Framing (injective encoding):
 * <pre>
 *   [4-byte BE path length] [path UTF-8] [4-byte BE value length or -1 for null] [value UTF-8]
 * </pre>
 * Length-prefixed rather than delimiter-separated so a value containing arbitrary bytes
 * (including escaped NULs or `=` signs) cannot be confused with a field boundary. The value is
 * serialised via {@link JsonNode#toString()} to preserve type distinctions (string {@code "42"}
 * vs number {@code 42}) — earlier drafts used {@code asText()} which erased them. A missing
 * value is written as a {@code -1} length; empty-string value is length 0 — so absence stays
 * distinguishable from empty-string presence.
 *
 * <p>Field order in the type definition still influences the hash — schema changes therefore
 * invalidate cached fingerprints, which is desired.
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
            byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
            writeInt(pc.fingerprint, pathBytes.length);
            pc.fingerprint.update(pathBytes);
            if (val == null || val.isNull()) {
                // Sentinel for missing — distinguishable from empty string (length 0).
                writeInt(pc.fingerprint, -1);
            } else {
                // toString() preserves type (quotes for strings, bare digits for numbers, etc.).
                byte[] valBytes = val.toString().getBytes(StandardCharsets.UTF_8);
                writeInt(pc.fingerprint, valBytes.length);
                pc.fingerprint.update(valBytes);
            }
        } catch (PropaccessError e) {
            Log.util.error("FingerprintAction failed for path %s: %s", path, e.getMessage());
        }
    }

    private static void writeInt(java.security.MessageDigest md, int v) {
        md.update((byte) ((v >>> 24) & 0xff));
        md.update((byte) ((v >>> 16) & 0xff));
        md.update((byte) ((v >>>  8) & 0xff));
        md.update((byte) ( v         & 0xff));
    }
}
