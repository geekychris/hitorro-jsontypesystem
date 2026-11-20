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
package com.hitorro.jsontypesystem;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.hitorro.util.json.JsonValues;

/**
 * JSON-Schema-style constraint checkers, read directly from a field definition JsonNode.
 * A field's type-def JSON can carry any of:
 *
 * <ul>
 *   <li>{@code minLength} / {@code maxLength} — string bounds</li>
 *   <li>{@code pattern} — regex the string must match (full match)</li>
 *   <li>{@code enum} — array of allowed literal values</li>
 *   <li>{@code minimum} / {@code maximum} — number bounds (inclusive)</li>
 *   <li>{@code format} — one of {@code email}, {@code date-time}, {@code uri}, {@code uuid}</li>
 * </ul>
 *
 * Callers (currently {@link JVSValidator}) invoke {@link #check} which appends any violations to
 * the provided list. Absent constraint keys are silently ignored.
 */
public final class FieldConstraints {

    // Lightweight built-in format checkers. RFC-strict compliance is out of scope — these catch
    // the obvious "not even close" cases without dragging in a schema-validator dep.
    private static final Pattern EMAIL_RE =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern DATETIME_RE =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$");
    private static final Pattern UUID_RE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private FieldConstraints() {}

    /**
     * Check {@code value} against any constraints declared on {@code fieldDef} and append
     * violations to {@code out}. No-op if the field def has no constraint keys.
     */
    public static void check(String path,
                             JsonNode value,
                             JsonNode fieldDef,
                             java.util.List<JVSValidator.Violation> out) {
        if (value == null || value.isNull()) return; // "missing field" is a separate concern
        checkString(path, value, fieldDef, out);
        checkNumber(path, value, fieldDef, out);
        checkEnum(path, value, fieldDef, out);
        checkFormat(path, value, fieldDef, out);
    }

    private static void checkString(String path, JsonNode value, JsonNode fieldDef,
                                    java.util.List<JVSValidator.Violation> out) {
        if (!value.isString()) return;
        String s = JsonValues.textValue(value);
        if (fieldDef.hasNonNull("minLength")) {
            int min = JsonValues.asInt(fieldDef.get("minLength"));
            if (s.length() < min) {
                out.add(err(path, "value length " + s.length() + " is below minLength " + min));
            }
        }
        if (fieldDef.hasNonNull("maxLength")) {
            int max = JsonValues.asInt(fieldDef.get("maxLength"));
            if (s.length() > max) {
                out.add(err(path, "value length " + s.length() + " exceeds maxLength " + max));
            }
        }
        if (fieldDef.hasNonNull("pattern")) {
            String regex = JsonValues.asText(fieldDef.get("pattern"));
            try {
                if (!Pattern.matches(regex, s)) {
                    out.add(err(path, "value does not match pattern /" + regex + "/"));
                }
            } catch (PatternSyntaxException e) {
                out.add(err(path, "invalid pattern in type definition: " + e.getMessage()));
            }
        }
    }

    private static void checkNumber(String path, JsonNode value, JsonNode fieldDef,
                                    java.util.List<JVSValidator.Violation> out) {
        if (!value.isNumber()) return;
        double n = JsonValues.doubleValue(value);
        if (fieldDef.hasNonNull("minimum")) {
            double min = JsonValues.doubleValue(fieldDef.get("minimum"));
            if (n < min) {
                out.add(err(path, "value " + n + " is below minimum " + min));
            }
        }
        if (fieldDef.hasNonNull("maximum")) {
            double max = JsonValues.doubleValue(fieldDef.get("maximum"));
            if (n > max) {
                out.add(err(path, "value " + n + " exceeds maximum " + max));
            }
        }
    }

    private static void checkEnum(String path, JsonNode value, JsonNode fieldDef,
                                  java.util.List<JVSValidator.Violation> out) {
        JsonNode enumNode = fieldDef.get("enum");
        if (enumNode == null || !enumNode.isArray()) return;
        for (JsonNode allowed : enumNode) {
            if (allowed.equals(value)) return;
        }
        out.add(err(path, "value is not one of the allowed enum values"));
    }

    private static void checkFormat(String path, JsonNode value, JsonNode fieldDef,
                                    java.util.List<JVSValidator.Violation> out) {
        if (!value.isString()) return;
        JsonNode fmtNode = fieldDef.get("format");
        if (fmtNode == null || !fmtNode.isString()) return;
        String s = JsonValues.textValue(value);
        String fmt = JsonValues.asText(fmtNode);
        switch (fmt) {
            case "email" -> {
                if (!EMAIL_RE.matcher(s).matches()) {
                    out.add(err(path, "value is not a valid email"));
                }
            }
            case "date-time" -> {
                if (!DATETIME_RE.matcher(s).matches()) {
                    out.add(err(path, "value is not a valid ISO-8601 date-time"));
                }
            }
            case "uuid" -> {
                if (!UUID_RE.matcher(s).matches()) {
                    out.add(err(path, "value is not a valid UUID"));
                }
            }
            case "uri" -> {
                try {
                    URI u = new URI(s);
                    if (u.getScheme() == null) {
                        out.add(err(path, "value is not a valid absolute URI (missing scheme)"));
                    }
                } catch (URISyntaxException e) {
                    out.add(err(path, "value is not a valid URI: " + e.getMessage()));
                }
            }
            // Unknown format is not itself an error — schemas may use custom formats
            // that this validator doesn't understand. Downstream tools can layer on more.
            default -> { /* unknown format, ignore */ }
        }
    }

    private static JVSValidator.Violation err(String path, String message) {
        return new JVSValidator.Violation(path, message, JVSValidator.Level.ERROR);
    }
}
