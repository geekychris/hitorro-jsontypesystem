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
package com.hitorro.jsontypesystem.executors;

import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.JVSValidator;
import com.hitorro.jsontypesystem.projections.DocumentStore;
import com.hitorro.jsontypesystem.projections.EmbeddingProvider;
import com.hitorro.util.json.keys.propaccess.Propaccess;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ProjectionContext {
    public StringBuilder sb = new StringBuilder();
    public Propaccess path = new Propaccess("");
    public JVS source;
    public JVS target;
    /** Optional trace — set before calling project() to capture execution details. */
    public ExecutionTrace trace;

    // --- Optional per-projection state. Actions that need one of these read from here; callers
    //     wire them up before invoking project() and read back after. Unused actions ignore them.

    /** Collected constraint violations produced by ValidateAction. */
    public List<JVSValidator.Violation> violations = new ArrayList<>();
    /** Running hash produced by FingerprintAction; set by the caller before projection. */
    public MessageDigest fingerprint;
    /** Reference resolver used by MaterializeAction. */
    public DocumentStore documentStore;
    /** Embedding backend used by VectorizeAction. */
    public EmbeddingProvider embeddingProvider;
}
