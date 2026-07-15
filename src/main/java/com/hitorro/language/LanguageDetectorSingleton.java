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
package com.hitorro.language;

import com.hitorro.util.core.Log;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lazy loader for OpenNLP's {@link LanguageDetectorME} model. Unlike other model singletons
 * in this package, language detection is a single global model (not keyed on language), so
 * this class exposes a simple {@link #get()} that returns a detector or {@code null} if the
 * model file is missing.
 *
 * <p>Model file: {@code langdetect-183.bin} under {@link IsoLanguage#OpenNLPRootPath} (default
 * {@code ${ht_bin}/data/opennlpmodels1.5/}). The Apache OpenNLP project publishes this file at
 * <a href="https://opennlp.apache.org/models.html">opennlp.apache.org/models</a> — download and
 * drop it into the model directory to enable detection. Callers should tolerate {@code null}
 * (no model → skip detection and let downstream mappers fall back to their default language).
 */
public final class LanguageDetectorSingleton {

    /** Canonical OpenNLP language-detection model filename. */
    public static final String MODEL_FILENAME = "langdetect-183.bin";

    private static final AtomicReference<LanguageDetectorME> CACHED = new AtomicReference<>();
    private static volatile boolean loadAttempted = false;

    private LanguageDetectorSingleton() {}

    /**
     * Returns a shared detector instance, or {@code null} if the model file is not present.
     * Load is attempted at most once per JVM — a missing model is logged once at INFO level
     * (not repeated on every call) and subsequent calls short-circuit to {@code null}.
     */
    public static LanguageDetectorME get() {
        LanguageDetectorME existing = CACHED.get();
        if (existing != null) return existing;
        if (loadAttempted) return null;
        synchronized (LanguageDetectorSingleton.class) {
            if (loadAttempted) return CACHED.get();
            loadAttempted = true;
            LanguageDetectorME loaded = tryLoad();
            if (loaded != null) CACHED.set(loaded);
            return loaded;
        }
    }

    private static LanguageDetectorME tryLoad() {
        try {
            File root = IsoLanguage.OpenNLPRootPath.apply();
            if (root == null) return null; // env not configured — treat as no model
            File modelFile = new File(root, MODEL_FILENAME);
            if (!modelFile.exists()) {
                Log.type.info("Language detection disabled — model file not found at %s. " +
                        "Download %s from opennlp.apache.org and place it in the model directory " +
                        "to enable detection.", modelFile.getAbsolutePath(), MODEL_FILENAME);
                return null;
            }
            LanguageDetectorModel model = new LanguageDetectorModel(modelFile);
            return new LanguageDetectorME(model);
        } catch (IOException | RuntimeException e) {
            // Includes NPEs / missing-env / missing-config failures — all mean "no detector".
            Log.type.info("Language detection unavailable: %s", e.getMessage());
            return null;
        }
    }

    /** Reset for tests. Package-private on purpose — production code should never call this. */
    static void resetForTests() {
        synchronized (LanguageDetectorSingleton.class) {
            CACHED.set(null);
            loadAttempted = false;
        }
    }

    /** Package-private test hook — inject a preloaded detector without touching the file system. */
    static void setForTests(LanguageDetectorME detector) {
        synchronized (LanguageDetectorSingleton.class) {
            CACHED.set(detector);
            loadAttempted = true;
        }
    }
}
