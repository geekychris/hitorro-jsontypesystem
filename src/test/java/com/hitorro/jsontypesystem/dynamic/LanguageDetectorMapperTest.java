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
package com.hitorro.jsontypesystem.dynamic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LanguageDetectorMapper}. The mapper's contract is that it returns
 * {@code null} whenever detection is not possible (short input, missing model, missing config,
 * unknown language). Tests here exercise that contract at the fringes without requiring the
 * OpenNLP langdetect model or a fully-initialised {@code Iso639Table}.
 */
@DisplayName("LanguageDetectorMapper")
class LanguageDetectorMapperTest {

    @Test
    @DisplayName("detect() returns null for null input")
    void detectNull() {
        assertThat(LanguageDetectorMapper.detect(null)).isNull();
    }

    @Test
    @DisplayName("detect() returns null for text below MIN_TEXT_LENGTH")
    void detectShort() {
        assertThat(LanguageDetectorMapper.detect("hi")).isNull();
        assertThat(LanguageDetectorMapper.detect("short text")).isNull();
    }

    @Test
    @DisplayName("detect() never throws even when models/table are unavailable")
    void detectNeverThrows() {
        // 40 chars of English — long enough to pass the length check. The result may be a
        // language code (if detection is available) or null (if not). Either is acceptable;
        // the contract is only that the call does not throw.
        String result = LanguageDetectorMapper.detect(
                "The quick brown fox jumps over the lazy dog. Pack my box.");
        assertThat(result == null || result.length() >= 2).isTrue();
    }

    @Test
    @DisplayName("toTwoLetter(null) returns null")
    void toTwoLetterNull() {
        assertThat(LanguageDetectorMapper.toTwoLetter(null)).isNull();
    }

    @Test
    @DisplayName("toTwoLetter of an unknown code falls through to the raw input")
    void toTwoLetterUnknown() {
        String raw = "zzz-not-a-language";
        assertThat(LanguageDetectorMapper.toTwoLetter(raw)).isEqualTo(raw);
    }
}
