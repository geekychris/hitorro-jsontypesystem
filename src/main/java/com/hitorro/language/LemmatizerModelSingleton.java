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
import com.hitorro.util.core.events.cache.HashCache;
import com.hitorro.util.core.iterator.mappers.BaseMapper;
import com.hitorro.util.core.string.Fmt;
import opennlp.tools.lemmatizer.LemmatizerModel;

import java.io.File;
import java.io.IOException;

/**
 * Per-language OpenNLP {@link LemmatizerModel} loader — mirrors {@link POSModelSingleton}.
 *
 * <p>Model file convention: {@code {two}-lemmatizer.bin} under
 * {@link IsoLanguage#OpenNLPRootPath}. For English that resolves to {@code en-lemmatizer.bin}.
 * Returns {@code null} when the model file is not present so callers can skip lemmatisation
 * rather than fail; missing models are logged once at INFO level.
 */
public class LemmatizerModelSingleton extends BaseMapper<IsoLanguage, LemmatizerModel> {

    public static final HashCache<IsoLanguage, LemmatizerModel> singleton =
            new HashCache<>("lemmatizermodel", new LemmatizerModelSingleton());

    public LemmatizerModelSingleton() {}

    @Override
    public LemmatizerModel apply(IsoLanguage key) {
        if (key == null) return null;
        try {
            File root = IsoLanguage.OpenNLPRootPath.apply();
            if (root == null) return null;
            File modelFile = new File(root, Fmt.S("%s-lemmatizer.bin", key.getTwo()));
            if (!modelFile.exists()) {
                Log.type.info("Lemmatizer disabled for %s — model file not found at %s. " +
                        "Download the OpenNLP lemmatizer model to enable.", key.getTwo(),
                        modelFile.getAbsolutePath());
                return null;
            }
            return new LemmatizerModel(modelFile);
        } catch (IOException | RuntimeException e) {
            Log.type.info("Lemmatizer unavailable for %s: %s",
                    key.getTwo(), e.getMessage());
            return null;
        }
    }
}
