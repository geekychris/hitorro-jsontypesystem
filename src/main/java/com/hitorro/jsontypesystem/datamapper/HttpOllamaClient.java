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
package com.hitorro.jsontypesystem.datamapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Direct-HTTP {@link AIOperations} against a local Ollama endpoint.
 * Depends only on the JDK — no Spring, no Spring AI, no extra jars.
 *
 * <p>Same wire protocol as {@code ollama serve}'s
 * {@code /api/generate} and {@code /api/tags}. Suitable as the default
 * {@link AIOperations} implementation for standalone/non-Spring hosts;
 * Spring-integrated hosts should prefer {@code OllamaAIService} (Spring AI
 * ChatClient) so that streaming, function calling, and observability are
 * consistent with the rest of the Spring AI stack.</p>
 *
 * <p>All operations return {@code null} rather than throwing when the
 * endpoint is unreachable or returns non-2xx — callers get an at-most-once
 * best-effort semantic and pipelines survive when Ollama isn't running.</p>
 */
public final class HttpOllamaClient implements AIOperations {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final HttpClient http;
    private final Duration requestTimeout;

    public HttpOllamaClient(String baseUrl, String model) {
        this(baseUrl, model, Duration.ofSeconds(60));
    }

    public HttpOllamaClient(String baseUrl, String model, Duration requestTimeout) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl;
        this.model   = model   == null || model.isBlank()   ? "llama3.2"               : model;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public String baseUrl() { return baseUrl; }
    public String model()   { return model; }

    /**
     * Raw completion. Returns the model's response text with leading/trailing
     * whitespace stripped, or {@code null} on transport error / non-2xx.
     * All the {@link AIOperations} verbs build on top of this.
     */
    public String generate(String prompt) {
        try {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                            .timeout(requestTimeout)
                            .header("content-type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;
            JsonNode parsed = JSON.readTree(resp.body());
            JsonNode response = parsed.get("response");
            return response == null ? null : response.asText().trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                            .timeout(Duration.ofSeconds(3))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return text;
        return generate("Translate the following text from " + langName(sourceLang) + " to "
                + langName(targetLang) + ". Return ONLY the translated text, no commentary.\n\n"
                + "Text to translate:\n" + text);
    }

    @Override
    public String summarize(String text, int maxWords) {
        if (text == null || text.isBlank()) return text;
        return generate("Summarize the following text in approximately " + maxWords
                + " words or less. Focus on the key points and main ideas:\n\n" + text);
    }

    @Override
    public String ask(String text, String question) {
        if (text == null || question == null) return null;
        return generate("Based on the following text, answer this question: " + question
                + "\n\nText:\n" + text
                + "\n\nAnswer concisely and accurately. If the answer cannot be found in the text, say so.");
    }

    private static String langName(String code) {
        if (code == null) return "?";
        return switch (code) {
            case "en" -> "English"; case "es" -> "Spanish"; case "fr" -> "French";
            case "de" -> "German";  case "it" -> "Italian"; case "pt" -> "Portuguese";
            case "ja" -> "Japanese"; case "zh" -> "Chinese"; case "ko" -> "Korean";
            case "ar" -> "Arabic";  case "ru" -> "Russian"; case "hi" -> "Hindi";
            default   -> code;
        };
    }
}
