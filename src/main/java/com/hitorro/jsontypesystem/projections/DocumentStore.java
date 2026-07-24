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
 *
 * <p><b>Contract — synchronous, best-effort:</b>
 * <ul>
 *   <li>{@link #getDocument} is called from the projection thread and must return in bounded
 *       time. Adapters over slow / remote / possibly-hanging backends <em>must</em> enforce
 *       their own timeout and return {@code null} on timeout rather than block the projection.
 *       There is no cancellation channel wired through the projection engine today.</li>
 *   <li>Returning {@code null} means "unresolvable" — projection leaves the reference alone;
 *       it never throws.</li>
 *   <li>Implementations should treat authorization/RPC failures the same as
 *       "not found" — return {@code null}, log at the adapter, do not propagate the exception
 *       through the projection engine. Aborting a projection because one reference couldn't
 *       be resolved would be surprising.</li>
 *   <li>Repeated lookups of the same id within a single projection are common; adapters over
 *       expensive backends should cache within the adapter (per-request or per-projection).
 *       The projection engine does not deduplicate calls on the caller's behalf.</li>
 * </ul>
 *
 * <p>These sync-only, best-effort semantics are appropriate for read-time enrichment inside a
 * request handler. If you need bounded parallelism, batching, or async lookup for a
 * heavy-fan-out use case, extend this SPI with a new async variant rather than repurposing
 * this one — that keeps the request-path contract simple.
 */
public interface DocumentStore {

    /**
     * Return the document for {@code id}, or {@code null} if no document is registered. The
     * projection engine treats {@code null} as "unresolvable" and leaves the reference field
     * untouched — it does not throw. Implementations must not block indefinitely; see the
     * class Javadoc for the timeout / failure contract.
     */
    JsonNode getDocument(String id);
}
