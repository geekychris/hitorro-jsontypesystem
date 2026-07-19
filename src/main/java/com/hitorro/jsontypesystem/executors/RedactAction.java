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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Replace a field's value with a masked stand-in. Unlike {@link RemoveAction}, redact preserves
 * the KEY at the destination so downstream consumers can see the field existed while the value
 * itself is hidden — the pattern used for PII / GDPR-style pipelines that must prove non-leakage.
 *
 * <p>Mode is read from the field's group metadata via the {@code mode} attribute:
 * <ul>
 *   <li>{@code mask} (default) — replace with {@code "***"}</li>
 *   <li>{@code hash} — SHA-256 hex of the string form of the value</li>
 *   <li>{@code null} — write JSON null (keeps the key, drops the value)</li>
 * </ul>
 */
public class RedactAction implements ExecutorAction<ExecutionBuilder> {

    private final String mode;

    public RedactAction(final Field field, Group group, final Propaccess path) {
        String method = group == null ? null : group.getMethod();
        this.mode = method == null || method.isEmpty() ? "mask" : method;
    }

    @Override
    public void project(final ProjectionContext pc, final Propaccess path,
                        final boolean isMulti, final String lang) {
        try {
            JsonNode val = pc.source.get(path);
            if (val == null || val.isNull()) {
                return; // nothing to redact — matches RemoveAction's leave-empty behaviour
            }
            JsonNode replacement = switch (mode) {
                case "hash" -> JsonNodeFactory.instance.textNode(sha256Hex(val.toString()));
                case "null" -> JsonNodeFactory.instance.nullNode();
                default     -> JsonNodeFactory.instance.textNode("***");
            };
            pc.source.set(path, replacement);
        } catch (PropaccessError e) {
            Log.util.error("RedactAction projection failed for path %s: %s", path, e.getMessage());
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — this branch is defensive only.
            return "***";
        }
    }
}
