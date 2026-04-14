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
package com.hitorro.language;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.core.Env;
import com.hitorro.util.core.Log;
import com.hitorro.util.json.String2JsonMapper;
import opennlp.tools.util.Span;

import java.io.*;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ONNX-based multilingual NER using a HuggingFace transformer model (XLM-RoBERTa).
 * Handles ALL entity types (PER, ORG, LOC, DATE) via BIO tag decoding.
 * Uses DJL HuggingFaceTokenizer to correctly handle SentencePiece/BPE tokenization.
 *
 * Input/output contract matches NameFinderME: takes String[] tokens, returns Span[]
 * with token indices and entity type labels.
 *
 * Loaded lazily as a singleton from ${HT_BIN}/data/opennlpmodels-onnx/ner-multilingual/.
 */
public class OnnxNerFinder {

    private static volatile OnnxNerFinder instance;
    private static volatile boolean initAttempted = false;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Map<Integer, String> id2Labels;

    private OnnxNerFinder(File modelFile, File tokenizerFile, File labelsFile) throws Exception {
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        this.session = env.createSession(modelFile.getPath(), opts);
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath());
        this.id2Labels = loadLabels(labelsFile);
        Log.lang.info("OnnxNerFinder loaded: %d labels, model=%s", id2Labels.size(), modelFile.getName());
    }

    /**
     * Get the singleton instance. Returns null if ONNX model is not available.
     */
    public static OnnxNerFinder getInstance() {
        if (!initAttempted) {
            synchronized (OnnxNerFinder.class) {
                if (!initAttempted) {
                    initAttempted = true;
                    try {
                        File base = new File(Env.getBin(), "data/opennlpmodels-onnx/ner-multilingual");
                        File modelFile = new File(base, "model.onnx");
                        File tokenizerFile = new File(base, "tokenizer.json");
                        File labelsFile = new File(base, "id2labels.json");
                        if (modelFile.exists() && tokenizerFile.exists() && labelsFile.exists()) {
                            instance = new OnnxNerFinder(modelFile, tokenizerFile, labelsFile);
                        } else {
                            Log.lang.info("ONNX NER model not found at %s — DL NER disabled", base.getPath());
                        }
                    } catch (Exception e) {
                        Log.lang.error("Failed to load ONNX NER model: %s %e", e.getMessage(), e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Find named entities in pre-tokenized text. Returns Span[] with token indices
     * and entity type labels (e.g. "person", "organization", "location", "date").
     * Compatible with NameFinderME.find() output.
     */
    public Span[] find(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return new Span[0];
        }

        try {
            // Join tokens — the HuggingFace tokenizer handles full text
            String text = String.join(" ", tokens);

            // Tokenize using the HuggingFace tokenizer (SentencePiece BPE)
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            // Map each subword token back to the original word token index
            int[] subwordToOriginal = mapSubwordsToOriginalTokens(tokens, encoding);

            // Build input tensors
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", OnnxTensor.createTensor(env,
                    LongBuffer.wrap(inputIds), new long[]{1, inputIds.length}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env,
                    LongBuffer.wrap(attentionMask), new long[]{1, attentionMask.length}));

            // Run inference
            float[][][] output = (float[][][]) session.run(inputs).get(0).getValue();
            float[][] logits = output[0]; // [seq_len][num_labels]

            // Decode BIO tags and map back to original token spans
            return decodeBioTags(logits, subwordToOriginal, tokens.length);

        } catch (Exception e) {
            Log.lang.error("ONNX NER inference failed: %s", e.getMessage());
            return new Span[0];
        }
    }

    /**
     * Map subword tokens from the HuggingFace Encoding back to original word token indices.
     * Uses character offsets from the Encoding to determine which original token each subword belongs to.
     */
    private int[] mapSubwordsToOriginalTokens(String[] originalTokens, Encoding encoding) {
        long[] ids = encoding.getIds();
        int[] mapping = new int[ids.length];

        // Build character offset → original token index map
        // Original tokens are space-joined, so we know each token's char range
        int[] tokenStarts = new int[originalTokens.length];
        int[] tokenEnds = new int[originalTokens.length];
        int charPos = 0;
        for (int i = 0; i < originalTokens.length; i++) {
            tokenStarts[i] = charPos;
            tokenEnds[i] = charPos + originalTokens[i].length();
            charPos = tokenEnds[i] + 1; // +1 for space
        }

        // Use word IDs from the encoding to map subwords to original tokens
        long[] wordIds = encoding.getWordIds();
        for (int i = 0; i < ids.length; i++) {
            long wid = wordIds[i];
            mapping[i] = (wid >= 0 && wid < originalTokens.length) ? (int) wid : 0;
        }
        return mapping;
    }

    /**
     * Decode BIO-tagged logits into Span objects with original token indices.
     */
    private Span[] decodeBioTags(float[][] logits, int[] subwordToOriginal, int numOrigTokens) {
        List<Span> spans = new ArrayList<>();
        int spanStart = -1;
        String spanType = null;
        int lastOrigIdx = -1;

        for (int i = 0; i < logits.length; i++) {
            int maxIdx = argmax(logits[i]);
            String label = id2Labels.getOrDefault(maxIdx, "O");
            int origIdx = (i < subwordToOriginal.length) ? subwordToOriginal[i] : numOrigTokens - 1;

            if (label.startsWith("B-")) {
                // Close previous span
                if (spanStart >= 0) {
                    spans.add(new Span(spanStart, lastOrigIdx + 1, normalizeType(spanType)));
                }
                spanStart = origIdx;
                spanType = label.substring(2);
            } else if (label.startsWith("I-") && spanStart >= 0 && label.substring(2).equals(spanType)) {
                // Continue current span
            } else {
                // O tag or type mismatch — close previous span
                if (spanStart >= 0) {
                    spans.add(new Span(spanStart, lastOrigIdx + 1, normalizeType(spanType)));
                    spanStart = -1;
                    spanType = null;
                }
            }
            lastOrigIdx = origIdx;
        }
        // Close final span
        if (spanStart >= 0) {
            spans.add(new Span(spanStart, lastOrigIdx + 1, normalizeType(spanType)));
        }

        return deduplicateSpans(spans);
    }

    private String normalizeType(String type) {
        if (type == null) return "misc";
        return switch (type.toUpperCase()) {
            case "PER", "PERSON" -> "person";
            case "ORG", "ORGANIZATION" -> "organization";
            case "LOC", "LOCATION", "GPE" -> "location";
            case "DATE" -> "date";
            case "MONEY" -> "money";
            case "TIME" -> "time";
            case "PERCENT", "PERCENTAGE" -> "percentage";
            default -> "misc";
        };
    }

    private Span[] deduplicateSpans(List<Span> spans) {
        Set<String> seen = new HashSet<>();
        List<Span> unique = new ArrayList<>();
        for (Span s : spans) {
            String key = s.getStart() + ":" + s.getEnd() + ":" + s.getType();
            if (seen.add(key) && s.getStart() < s.getEnd()) {
                unique.add(s);
            }
        }
        return unique.toArray(new Span[0]);
    }

    private int argmax(float[] arr) {
        int maxIdx = 0;
        float maxVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxVal) {
                maxVal = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private Map<Integer, String> loadLabels(File labelsFile) throws Exception {
        String json = Files.readString(labelsFile.toPath());
        JsonNode node = new String2JsonMapper().apply(json);
        Map<Integer, String> labels = new HashMap<>();
        node.fields().forEachRemaining(e -> labels.put(Integer.parseInt(e.getKey()), e.getValue().asText()));
        return labels;
    }
}
