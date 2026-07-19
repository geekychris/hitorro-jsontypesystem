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
 * SPI for producing a vector embedding of text. Used by
 * {@link com.hitorro.jsontypesystem.executors.VectorizeAction VectorizeAction} to derive per-field
 * vectors for semantic search / dedup / clustering.
 *
 * <p>Real backends (ONNX sentence-transformers, remote HTTP inference, etc.) live in caller
 * modules; a {@link HashingEmbeddingProvider} is provided for tests and as a stable default
 * when no real backend is configured.
 */
public interface EmbeddingProvider {

    /** Deterministic vector length for this provider. Callers use this to size storage. */
    int dimensions();

    /** Produce an embedding for {@code text}. Returning a zero vector is acceptable for empty input. */
    float[] embed(String text);
}
