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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link DocumentStore} for tests and small-scale caches.
 *
 * <p><b>Ownership:</b> the store defensively deep-copies documents on both {@link #put} and
 * {@link #getDocument} so callers can never share mutable {@link JsonNode} instances with the
 * internal cache. Mutating a doc after putting it does <em>not</em> mutate the stored copy;
 * mutating a doc returned from {@code getDocument} does <em>not</em> corrupt subsequent reads.
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private final ConcurrentHashMap<String, JsonNode> byId = new ConcurrentHashMap<>();

    public InMemoryDocumentStore put(String id, JsonNode doc) {
        if (id != null && doc != null) byId.put(id, doc.deepCopy());
        return this;
    }

    @Override
    public JsonNode getDocument(String id) {
        if (id == null) return null;
        JsonNode hit = byId.get(id);
        return hit == null ? null : hit.deepCopy();
    }

    public int size() {
        return byId.size();
    }
}
