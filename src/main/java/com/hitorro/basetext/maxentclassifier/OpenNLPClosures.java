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
package com.hitorro.basetext.maxentclassifier;

import com.hitorro.language.IsoLanguage;
import com.hitorro.language.PartOfSpeech;
import com.hitorro.language.PartOfSpeechSingletonMapper;
import com.hitorro.language.SentenceDetectorSingleton;
import com.hitorro.language.SentenceSegmenter;
import com.hitorro.util.core.events.cache.PoolContainer;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSTaggerME;

import java.util.function.Function;

public class OpenNLPClosures {
    public static <O> O chunkParser(IsoLanguage lang, Function<ChunkParser, O> func) {
        PoolContainer<IsoLanguage, PartOfSpeech> pc = PartOfSpeechSingletonMapper.singleton.get(lang);
        PartOfSpeech pos = null;
        try {
            pos = pc.get();
            ChunkerModel cm = pos.getChunker();
            POSTaggerME ptme = pos.getPOSTagger();
            if (cm == null || ptme == null) {
                return null;
            }
            ChunkParser parser = new ChunkParser(new ChunkerME(cm), ptme);
            return func.apply(parser);
        } finally {
            pc.returnIt(pos);
        }
    }

    public static <O> O sentenceSegmenter(IsoLanguage lang, Function<SentenceSegmenter, O> func) {
        PoolContainer<IsoLanguage, SentenceSegmenter> pool = SentenceDetectorSingleton.singleton.get(lang);
        SentenceSegmenter ss = null;
        try {
            ss = pool.get();
            return func.apply(ss);
        } finally {
            pool.returnIt(ss);
        }
    }

    public static <O> O sentencePOS(IsoLanguage lang, Function<PartOfSpeech, O> func) {
        PoolContainer<IsoLanguage, PartOfSpeech> posPool = PartOfSpeechSingletonMapper.singleton.get(lang);
        PartOfSpeech pos = null;
        try {
            pos = posPool.get();
            return func.apply(pos);
        } finally {
            posPool.returnIt(pos);
        }
    }
}
