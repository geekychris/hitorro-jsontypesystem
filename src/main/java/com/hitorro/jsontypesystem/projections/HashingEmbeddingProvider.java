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
package com.hitorro.jsontypesystem.projections;

/**
 * Deterministic feature-hashing {@link EmbeddingProvider}. Not a real language model — it
 * hashes tokens into fixed-dimensional space. Useful as a stable default in tests and as a
 * fallback when no real embedding backend is wired up; the vectors are consistent for the same
 * input and different for different inputs, which is enough for downstream cosine-similarity /
 * dedup smoke tests.
 */
public final class HashingEmbeddingProvider implements EmbeddingProvider {

    private final int dims;

    public HashingEmbeddingProvider() {
        this(128);
    }

    public HashingEmbeddingProvider(int dims) {
        if (dims <= 0) throw new IllegalArgumentException("dims must be > 0");
        this.dims = dims;
    }

    @Override
    public int dimensions() {
        return dims;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dims];
        if (text == null || text.isEmpty()) return v;
        // Simple whitespace tokenization + Murmur-style mix into dimension buckets.
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || Character.isWhitespace(text.charAt(i))) {
                if (i > start) {
                    int h = hash(text, start, i);
                    int bucket = Math.floorMod(h, dims);
                    // Sign bit gives the token a stable ±1 contribution — reduces collisions.
                    v[bucket] += ((h >>> 31) == 0) ? 1.0f : -1.0f;
                }
                start = i + 1;
            }
        }
        // L2-normalize so cosine similarity is bounded to [-1, 1].
        double norm = 0.0;
        for (float x : v) norm += x * x;
        if (norm > 0.0) {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < v.length; i++) v[i] *= inv;
        }
        return v;
    }

    private static int hash(CharSequence s, int from, int to) {
        int h = 0x811c9dc5; // FNV-1a offset
        for (int i = from; i < to; i++) {
            h ^= Character.toLowerCase(s.charAt(i));
            h *= 0x01000193;
        }
        return h;
    }
}
