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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.language.Iso639Table;
import com.hitorro.language.IsoLanguage;
import com.hitorro.language.LemmatizerModelSingleton;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;

/**
 * Dynamic field mapper that produces an array of lemma strings from token + POS-tag pairs.
 *
 * <p>Type-def usage (three input fields, in this order):
 * <pre>{@code
 * {
 *   "name": "lemmas",
 *   "type": "core_string",
 *   "vector": true,
 *   "dynamic": "com.hitorro.jsontypesystem.dynamic.LemmatizerMapper",
 *   "fields": [".lang", ".tokens", ".posTags"]
 * }
 * }</pre>
 *
 * <p>Inputs:
 * <ul>
 *   <li><b>lang</b> — ISO-639 code (2- or 3-letter). Selects the per-language OpenNLP model.</li>
 *   <li><b>tokens</b> — array of token strings.</li>
 *   <li><b>posTags</b> — array of PennTreebank POS tags, same length as tokens.</li>
 * </ul>
 *
 * <p>Returns an {@link ArrayNode} of lemmas, or {@code null} if:
 * <ul>
 *   <li>the language model is not installed for {@code lang},</li>
 *   <li>either input array is missing or non-array,</li>
 *   <li>tokens and posTags have mismatched lengths (would produce garbage lemmas).</li>
 * </ul>
 *
 * <p>Callers who don't have per-language {@code {lang}-lemmatizer.bin} models installed can
 * ignore this mapper — the module still ships with the existing Snowball stemmer for the
 * cases where a stem is good enough.
 */
public class LemmatizerMapper extends DynamicFieldMapper {

    @Override
    public JsonNode map(JVS jvs, Propaccess pa, int depth) {
        JsonNode[] arr = getValues(jvs, pa, depth);
        if (arr.length < 3) return null;

        JsonNode langNode = arr[0];
        JsonNode tokensNode = arr[1];
        JsonNode tagsNode = arr[2];

        if (langNode == null || !langNode.isTextual()) return null;
        if (tokensNode == null || !tokensNode.isArray()) return null;
        if (tagsNode == null || !tagsNode.isArray()) return null;

        int n = tokensNode.size();
        if (tagsNode.size() != n) return null; // mismatched arrays — refuse rather than mislemmatise
        if (n == 0) return JsonNodeFactory.instance.arrayNode();

        IsoLanguage lang = Iso639Table.getInstance().getRow(langNode.textValue());
        if (lang == null) return null;

        LemmatizerModel model = LemmatizerModelSingleton.singleton.get(lang);
        if (model == null) return null;
        LemmatizerME lemmatizer = new LemmatizerME(model);

        String[] tokens = new String[n];
        String[] tags = new String[n];
        for (int i = 0; i < n; i++) {
            tokens[i] = tokensNode.get(i).textValue();
            tags[i] = tagsNode.get(i).textValue();
        }
        String[] lemmas = lemmatizer.lemmatize(tokens, tags);

        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (String lemma : lemmas) {
            out.add(lemma);
        }
        return out;
    }
}
