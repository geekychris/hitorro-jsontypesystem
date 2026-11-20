/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.jsontypesystem.datamapper;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The request body sent to Ollama is compact JSON in declaration order, as on 3.x. */
class HttpOllamaClientBodyTest {

    @Test
    void sendsCompactJsonBody() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/generate", ex -> {
            received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"response\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        srv.start();
        try {
            String r = new HttpOllamaClient("http://127.0.0.1:" + srv.getAddress().getPort(), "m1")
                    .generate("say \"hi\" é \n\u0001");
            assertThat(received.get()).isEqualTo("{\"model\":\"m1\",\"prompt\":\"say \\\"hi\\\" é \\n\\u0001\",\"stream\":false}");
            assertThat(r).isEqualTo("ok");
        } finally {
            srv.stop(0);
        }
    }
}
