/*
 * Copyright (c) 2006-2025 Chris Collins
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
import com.hitorro.language.Models;
import com.hitorro.language.OnnxNerFinder;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import java.util.ArrayList;
import java.util.List;

/**
 * NER markup dynamic field mapper using OpenNLP NameFinderME directly.
 * Takes language and segmented sentences, runs person/organization/location
 * NER finders, and produces markup text with entity annotations.
 */
public class NERMarkupMapper extends DynamicFieldMapper {

    @Override
    public JsonNode map(final JVS jvs, final Propaccess pa, final int depth) {
        JsonNode[] arr = getValues(jvs, pa, depth);
        if (arr.length > 1) {
            if (arr[0] == null || arr[1] == null) {
                return null;
            }
            String langText = arr[0].textValue();
            IsoLanguage lang = Iso639Table.getInstance().getRow(langText);
            JsonNode valIn = arr[1];
            return apply(valIn, lang);
        }
        return null;
    }

    public ArrayNode apply(JsonNode arrayNode, IsoLanguage lang) {
        if (lang == null || arrayNode == null || !arrayNode.isArray()) {
            return null;
        }

        // Load NER models for this language
        List<NameFinderEntry> finders = loadFinders(lang);
        if (finders.isEmpty()) {
            return null;
        }

        // Get tokenizer for this language
        String[] tokenize;
        TokenizerModel tokModel = Models.tokenizerSingleton.get(lang);

        ArrayNode ret = JsonNodeFactory.instance.arrayNode();
        for (JsonNode elem : arrayNode) {
            String sentence = elem.textValue();
            if (sentence == null || sentence.isEmpty()) {
                ret.add(JsonNodeFactory.instance.textNode(""));
                continue;
            }

            // Tokenize
            String[] tokens;
            if (tokModel != null) {
                tokens = new TokenizerME(tokModel).tokenize(sentence);
            } else {
                tokens = SimpleTokenizer.INSTANCE.tokenize(sentence);
            }

            // Run all NER finders and collect spans
            List<TaggedSpan> allSpans = new ArrayList<>();
            for (NameFinderEntry entry : finders) {
                Span[] spans = entry.find(tokens);
                for (Span span : spans) {
                    allSpans.add(new TaggedSpan(span, entry.labelFor(span)));
                }
            }

            // Build markup string
            String markup = buildMarkup(tokens, allSpans);
            ret.add(JsonNodeFactory.instance.textNode(markup));
        }
        return ret;
    }

    private List<NameFinderEntry> loadFinders(IsoLanguage lang) {
        List<NameFinderEntry> finders = new ArrayList<>();
        addFinder(finders, Models.person.get(lang), "person");
        addFinder(finders, Models.organization.get(lang), "organization");
        addFinder(finders, Models.location.get(lang), "location");
        addFinder(finders, Models.date.get(lang), "date");
        addFinder(finders, Models.money.get(lang), "money");

        // Fallback: if no .bin NER models for this language, try ONNX DL model
        if (finders.isEmpty()) {
            OnnxNerFinder onnx = OnnxNerFinder.getInstance();
            if (onnx != null) {
                // DL model handles all entity types in one pass — Span.getType() carries the label
                finders.add(new NameFinderEntry(onnx, null));
            }
        }

        return finders;
    }

    private void addFinder(List<NameFinderEntry> finders, TokenNameFinderModel model, String label) {
        if (model != null) {
            finders.add(new NameFinderEntry(new NameFinderME(model), label));
        }
    }

    private String buildMarkup(String[] tokens, List<TaggedSpan> spans) {
        StringBuilder sb = new StringBuilder();
        // Sort spans by start position
        spans.sort((a, b) -> Integer.compare(a.span.getStart(), b.span.getStart()));

        int pos = 0;
        for (TaggedSpan ts : spans) {
            // Add tokens before this span
            for (int i = pos; i < ts.span.getStart(); i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(tokens[i]);
            }
            // Add tagged entity
            if (sb.length() > 0) sb.append(" ");
            sb.append("<").append(ts.label).append(">");
            for (int i = ts.span.getStart(); i < ts.span.getEnd(); i++) {
                if (i > ts.span.getStart()) sb.append(" ");
                sb.append(tokens[i]);
            }
            sb.append("</").append(ts.label).append(">");
            pos = ts.span.getEnd();
        }
        // Add remaining tokens
        for (int i = pos; i < tokens.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    /**
     * Wraps either a NameFinderME (.bin model, per-entity-type) or an OnnxNerFinder (DL, all types).
     * When using the DL model, label is null and the entity type comes from Span.getType().
     */
    private static class NameFinderEntry {
        final NameFinderME meFinder;
        final OnnxNerFinder dlFinder;
        final String label;

        NameFinderEntry(NameFinderME finder, String label) {
            this.meFinder = finder;
            this.dlFinder = null;
            this.label = label;
        }

        NameFinderEntry(OnnxNerFinder finder, String label) {
            this.meFinder = null;
            this.dlFinder = finder;
            this.label = label;
        }

        Span[] find(String[] tokens) {
            if (meFinder != null) return meFinder.find(tokens);
            if (dlFinder != null) return dlFinder.find(tokens);
            return new Span[0];
        }

        /**
         * Get the label for a span. For ME finders, use the fixed label.
         * For DL finders, the label comes from the Span's type field.
         */
        String labelFor(Span span) {
            if (label != null) return label;
            return span.getType() != null ? span.getType() : "misc";
        }
    }

    private static class TaggedSpan {
        final Span span;
        final String label;

        TaggedSpan(Span span, String label) {
            this.span = span;
            this.label = label;
        }
    }
}
