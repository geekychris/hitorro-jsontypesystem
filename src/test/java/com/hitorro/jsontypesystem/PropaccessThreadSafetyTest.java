package com.hitorro.jsontypesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Thread safety tests for Propaccess and projection pipeline.
 * Verifies that path navigation doesn't corrupt state under concurrent access.
 */
@DisplayName("Propaccess Thread Safety")
class PropaccessThreadSafetyTest {

    @Test
    @DisplayName("Propaccess length should not be corrupted by getSet with different depths")
    void lengthNotCorruptedByGetSet() throws PropaccessError {
        // Verify fix for the length mutation bug in getSet()
        JVS jvs = JVS.read("{\"a\": {\"b\": {\"c\": \"value\"}}}");

        // First read at full depth
        Propaccess pa = new Propaccess("a.b.c");
        assertThat(pa.length()).isEqualTo(3);

        jvs.get(pa);
        // After get(), length should still be 3 (not mutated)
        assertThat(pa.length()).isEqualTo(3);
    }

    @RepeatedTest(5)
    @DisplayName("Concurrent JVS reads should not corrupt Propaccess state")
    void concurrentReadsDoNotCorruptState() throws Exception {
        String json = "{\"title\": {\"mls\": [{\"lang\": \"en\", \"text\": \"Hello\"}]}, \"body\": {\"mls\": [{\"lang\": \"en\", \"text\": \"World\"}]}}";

        int threadCount = 8;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // Ensure all threads start at the same time
                    for (int i = 0; i < iterationsPerThread; i++) {
                        // Each thread creates its own JVS (shared type system, own JSON)
                        JVS jvs = JVS.read(json);
                        try {
                            JsonNode title = (JsonNode) jvs.get("title.mls[0].text");
                            if (title == null || !"Hello".equals(title.textValue())) {
                                errors.incrementAndGet();
                            }
                            JsonNode body = (JsonNode) jvs.get("body.mls[0].text");
                            if (body == null || !"World".equals(body.textValue())) {
                                errors.incrementAndGet();
                            }
                        } catch (PropaccessError e) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors.get()).as("Concurrent read errors").isZero();
    }

    @Test
    @DisplayName("Propaccess clone should be independent of original")
    void cloneIsIndependent() {
        Propaccess original = new Propaccess("a.b.c");
        Propaccess clone = original.clone();

        assertThat(clone.length()).isEqualTo(original.length());

        // Modifying clone should not affect original
        clone.pop();
        assertThat(clone.length()).isEqualTo(2);
        assertThat(original.length()).isEqualTo(3);
    }
}
