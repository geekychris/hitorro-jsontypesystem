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
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Replace a field's value with a masked stand-in. Unlike {@link RemoveAction}, redact preserves
 * the KEY at the destination so downstream consumers can see the field existed while the value
 * itself is hidden — the pattern used for PII / GDPR-style pipelines that must prove non-leakage.
 *
 * <p>Mode is read from the field's group metadata via the {@code method} attribute:
 * <ul>
 *   <li>{@code mask} (default) — replace with {@code "***"}.</li>
 *   <li>{@code null} — write JSON null (keeps the key, drops the value).</li>
 *   <li>{@code hash} — SHA-256 hex of the string form. <b>Unsalted.</b> Deterministic across
 *       any two documents in the world, so it is <em>not</em> a defence against linkability
 *       attacks — it only proves a value once existed. Use {@code hmac} when linkability
 *       across records matters.</li>
 *   <li>{@code hmac} — HMAC-SHA-256 hex of the value using {@link ProjectionContext#redactionKey}.
 *       Deterministic within a single key domain (so records under the same tenant / cohort
 *       still link, allowing aggregation) but opaque across key domains and to attackers who
 *       don't hold the key. Wire the key from your secret-management layer; the projection
 *       fails closed (write nothing, log a warning) if the key is absent.</li>
 * </ul>
 *
 * <p>Failure mode: unlike the read-only projections, redact <b>fails closed</b>. If writing the
 * masked value fails (a propaccess error, unwritable target, missing hmac key), the projection
 * throws so callers can't accidentally ship a PII-bearing doc. Errors surface as a runtime
 * exception at the projection boundary.
 */
public class RedactAction implements ExecutorAction<ExecutionBuilder> {

    private static final String HMAC_ALG = "HmacSHA256";

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
                case "hmac" -> JsonNodeFactory.instance.textNode(hmacSha256Hex(val.toString(), pc.redactionKey));
                case "null" -> JsonNodeFactory.instance.nullNode();
                default     -> JsonNodeFactory.instance.textNode("***");
            };
            pc.source.set(path, replacement);
        } catch (PropaccessError e) {
            // Fail closed. A silent redact-skip is a security bug — surface it instead of just logging.
            throw new RedactionFailedException("Failed to redact field at " + path, e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — defensive only.
            throw new RedactionFailedException("SHA-256 unavailable", e);
        }
    }

    private static String hmacSha256Hex(String s, SecretKey key) {
        if (key == null) {
            // Fail closed: refuse to fall back to unsalted SHA-256 silently.
            throw new RedactionFailedException(
                    "hmac redaction mode requires a SecretKey on ProjectionContext.redactionKey");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new RedactionFailedException("HMAC-SHA-256 failed", e);
        }
    }

    /** Thrown when redaction cannot complete safely; callers must treat it as a hard failure. */
    public static final class RedactionFailedException extends RuntimeException {
        public RedactionFailedException(String message) { super(message); }
        public RedactionFailedException(String message, Throwable cause) { super(message, cause); }
    }
}
