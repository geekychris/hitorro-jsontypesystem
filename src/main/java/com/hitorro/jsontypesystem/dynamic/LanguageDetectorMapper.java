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
package com.hitorro.jsontypesystem.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.language.Iso639Table;
import com.hitorro.language.IsoLanguage;
import com.hitorro.language.LanguageDetectorSingleton;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;

/**
 * Dynamic field mapper that infers the language of a text field and produces an ISO-639-1
 * two-letter code (e.g. {@code "en"}, {@code "fr"}).
 *
 * <p>Type-def usage:
 * <pre>{@code
 * {
 *   "name": "lang",
 *   "type": "core_string",
 *   "dynamic": "com.hitorro.jsontypesystem.dynamic.LanguageDetectorMapper",
 *   "fields": [".text"]
 * }
 * }</pre>
 *
 * <p>Detection strategy — tries in order:
 * <ol>
 *   <li>OpenNLP's {@link LanguageDetectorME} if {@code langdetect-183.bin} is installed under
 *       the OpenNLP model directory. Most accurate, covers ~103 languages.</li>
 *   <li>The module's existing n-gram-profile detector via
 *       {@link Iso639Table#getLanguageFromContent(String)}. Ships preloaded with the module;
 *       covers the languages listed under {@code data/text/langid/}.</li>
 * </ol>
 * Returns {@code null} if neither path yields a result. Downstream mappers (segmenter/POS/NER)
 * then fall back to their own default language rather than silently mispredicting.
 *
 * <p>Below {@link #MIN_TEXT_LENGTH} characters, detection is skipped — short strings are too
 * ambiguous to score reliably and returning {@code null} is safer than guessing.
 *
 * <p>Wire this mapper into the {@code index} projection phase BEFORE segmentation/POS/NER so
 * the language value is populated by the time those mappers look it up.
 */
public class LanguageDetectorMapper extends DynamicFieldMapper {

    /** Below this length, langdetect is unreliable and we prefer {@code null} over a wrong guess. */
    static final int MIN_TEXT_LENGTH = 20;

    @Override
    public JsonNode map(JVS jvs, Propaccess pa, int depth) {
        JsonNode[] arr = getValues(jvs, pa, depth);
        if (arr.length == 0 || arr[0] == null || !arr[0].isTextual()) return null;
        String text = arr[0].textValue();
        if (text == null || text.length() < MIN_TEXT_LENGTH) return null;

        String code = detect(text);
        return code == null ? null : new TextNode(code);
    }

    /** Package-private for unit-testing without going through DynamicFieldMapper's field wiring. */
    static String detect(String text) {
        if (text == null || text.length() < MIN_TEXT_LENGTH) return null;
        try {
            LanguageDetectorME detector = LanguageDetectorSingleton.get();
            if (detector != null) {
                Language best = detector.predictLanguage(text);
                if (best != null && best.getLang() != null) {
                    return toTwoLetter(best.getLang());
                }
            }
            IsoLanguage fromNgram = Iso639Table.getInstance().getLanguageFromContent(text);
            return fromNgram == null ? null : fromNgram.getTwo();
        } catch (RuntimeException | LinkageError e) {
            // Iso639Table or the detector may not be initialisable in every environment
            // (e.g. missing config dir). Fail closed by returning null — the mapper's contract
            // already says "null means downstream mappers fall back to their default language".
            return null;
        }
    }

    /**
     * Convert OpenNLP's ISO-639-3 code to ISO-639-1 (2-letter) via {@link Iso639Table}, which is
     * keyed on both forms. Unknown languages return the raw 3-letter code so callers still get
     * something useful for logging. If the table itself can't initialise, the raw code is also
     * returned — this keeps the mapper usable in stripped-down environments.
     */
    static String toTwoLetter(String openNlpCode) {
        if (openNlpCode == null) return null;
        try {
            IsoLanguage lang = Iso639Table.getInstance().getRow(openNlpCode);
            if (lang != null && lang.getTwo() != null && !lang.getTwo().isEmpty()) {
                return lang.getTwo();
            }
        } catch (RuntimeException | LinkageError e) {
            // fall through to raw
        }
        return openNlpCode;
    }
}
