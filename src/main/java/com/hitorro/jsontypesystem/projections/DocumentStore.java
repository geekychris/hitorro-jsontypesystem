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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SPI for looking up referenced documents. The {@link com.hitorro.jsontypesystem.executors.MaterializeAction
 * MaterializeAction} projection uses this to dereference {@code core_id}-shaped fields into
 * their full target documents at read time.
 *
 * <p>Implementations are typically module-specific (RocksDB-backed, Solr-backed, HTTP-backed,
 * etc.); {@link InMemoryDocumentStore} is provided for tests and small-scale in-process use.
 */
public interface DocumentStore {

    /**
     * Return the document for {@code id}, or {@code null} if no document is registered. The
     * projection engine treats {@code null} as "unresolvable" and leaves the reference field
     * untouched — it does not throw.
     */
    JsonNode getDocument(String id);
}
