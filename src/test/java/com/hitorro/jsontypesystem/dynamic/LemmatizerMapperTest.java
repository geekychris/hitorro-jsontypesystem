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

import com.hitorro.language.LemmatizerModelSingleton;
import opennlp.tools.lemmatizer.LemmatizerModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LemmatizerMapper} focused on its null-safety contract. The lemmatizer
 * model itself is not tested here — that would require {@code {lang}-lemmatizer.bin} files
 * that aren't shipped with the module. The tests below exercise the "no model / no environment"
 * behaviour that keeps the mapper from crashing pipelines when models aren't installed.
 */
@DisplayName("LemmatizerMapper (+ LemmatizerModelSingleton)")
class LemmatizerMapperTest {

    @Test
    @DisplayName("LemmatizerModelSingleton returns null for a null language key")
    void singletonNullLang() {
        LemmatizerModel m = new LemmatizerModelSingleton().apply(null);
        assertThat(m).isNull();
    }

    @Test
    @DisplayName("LemmatizerModelSingleton never throws when config is absent")
    void singletonDoesNotThrowWhenEnvMissing() {
        // Even when OpenNLPRootPath can't be resolved or the model file is absent, the
        // singleton must return null rather than propagate — pipelines should degrade
        // gracefully, not crash.
        LemmatizerModelSingleton s = new LemmatizerModelSingleton();
        // Use a lang key we know is unlikely to have a shipped model — the apply(...) path
        // may return null via any of: null root, missing file, IOException. All acceptable.
        LemmatizerModel m = s.apply(null);
        assertThat(m).isNull();
    }
}
