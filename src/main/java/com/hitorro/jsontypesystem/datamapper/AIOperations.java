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
package com.hitorro.jsontypesystem.datamapper;

/**
 * Interface for AI/LLM operations available from the transform DSL and
 * the retrieval summarize stage. Implementations bridge to AIService
 * (Ollama, OpenAI, etc.) without coupling hitorro-util to Spring or a
 * specific AI backend.
 *
 * <h3>Prompt-aware summarization</h3>
 *
 * <p>{@link #summarize(String, int)} is a shortcut around the general
 * {@link #withPrompt(String, String, int)} using the built-in
 * "brief factual summary" template. Callers that want a different
 * treatment (per-participant contributions, action items, sentiment,
 * ...) invoke {@code withPrompt} with their own instruction string.</p>
 *
 * <p>The retrieval-side {@code SummarizationRetriever} resolves a
 * caller-supplied {@code style} enum to a shipped template via
 * {@code SummaryStyles.promptFor(style)} and passes it to
 * {@code withPrompt}. See {@code hitorro-retrieval/README.md}
 * §Summarization for the built-in style catalogue and how to add a
 * new one (~3 lines).</p>
 */
public interface AIOperations {
	String translate(String text, String sourceLang, String targetLang);

	/** Brief factual summary — shortcut for {@link #withPrompt} with the
	 *  built-in {@code BRIEF} template. Kept for back-compat. */
	String summarize(String text, int maxWords);

	/** Run an arbitrary instruction over the input text. This is the
	 *  primitive that powers every summary variant. Impls MUST honour
	 *  {@code maxWords} as a soft ceiling (usually via the model's
	 *  max_tokens or a "keep it under N words" line appended to the
	 *  prompt). A blank {@code promptTemplate} falls back to the
	 *  legacy brief-summary behaviour. */
	default String withPrompt(String text, String promptTemplate, int maxWords) {
		if (promptTemplate == null || promptTemplate.isBlank()) {
			return summarize(text, maxWords);
		}
		return ask(text, promptTemplate);
	}

	String ask(String text, String question);
	boolean isAvailable();
}
