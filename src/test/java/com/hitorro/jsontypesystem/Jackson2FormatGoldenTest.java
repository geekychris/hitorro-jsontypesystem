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
package com.hitorro.jsontypesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins hitorro-jsontypesystem 4.0.0 to what 3.0.2 produced on Jackson 2: JVS accessors over
 * loosely typed JSON, set/append/merge with Java values, variable resolution, type loading and
 * JSON Schema conversion, the validator, TypeDiff/TypeVersion, the redact/fingerprint/validate
 * projections (fingerprint digests depend on the exact serialized form), the Groovy DSL, and
 * reading JVS/type JSON from strings, files and URLs, including invalid input.
 *
 * <p>{@link JsonTypeSystemGoldenProgram} runs in a forked JVM with HT_BIN pointing at a temp
 * copy of {@code golden-config} (hitorro-all's config/types, implementations.json and config.txt,
 * frozen for this test).
 * Node and exception class names are compared after mapping Jackson 3 names back
 * (StringNode = TextNode, StreamReadException = JsonParseException). {@link #CHANGED} lists the
 * only values that differ, and why.
 */
class Jackson2FormatGoldenTest {

	/** Values that are intentionally different in 4.0.0: key, 4.0.0 value. */
	static final Map<String, String> CHANGED = Map.of(
			// Untyped/typed addLangText on an empty doc fails in hitorro-core PAContext (ArrayNode.set(-1, ...)).
			// Jackson 2 threw IndexOutOfBoundsException there, Jackson 3 throws JsonNodeException. Both unchecked.
			"addLangText", "!JsonNodeException",
			"addLangText.typed", "!JsonNodeException",
			// TypeJsonLoader.load keeps "parse errors are IOExceptions". In Jackson 2 the parse error itself was
			// an IOException (JsonParseException); now it is an IOException whose cause is the StreamReadException.
			"typeJsonLoader.TMP/bad.json", "!IOException");

	@Test
	void matchesJackson2Output(@TempDir Path root) throws Exception {
		// HT_BIN/config: the type definitions plus implementations.json (dynamic mappers and index
		// seekers resolve through it) and config.txt (marks the config root)
		Path config = root.resolve("config");
		Path fixture = Paths.get(getClass().getResource("golden-config").toURI());
		try (Stream<Path> files = Files.walk(fixture)) {
			for (Path f : (Iterable<Path>) files::iterator) {
				Path to = config.resolve(fixture.relativize(f).toString());
				if (Files.isDirectory(f)) Files.createDirectories(to); else Files.copy(f, to);
			}
		}
		Path work = Files.createDirectories(root.resolve("work"));
		Path stdout = root.resolve("out.txt"); // the program writes its key=value lines here
		Process p = new ProcessBuilder(
				Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
				"-DHT_HOME=" + root, "-DHT_BIN=" + root,
				"-cp", System.getProperty("java.class.path"),
				JsonTypeSystemGoldenProgram.class.getName(), stdout.toString())
				.directory(work.toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		assertThat(p.waitFor(5, TimeUnit.MINUTES)).isTrue();
		assertThat(p.exitValue()).isZero();

		Map<String, String> expected = parse(getClass().getResourceAsStream("jackson2-jsontypesystem.txt").readAllBytes());
		expected.putAll(CHANGED);
		Map<String, String> actual = new LinkedHashMap<>();
		parse(Files.readAllBytes(stdout)).forEach((k, v) -> actual.put(k,
				v.replace("StringNode", "TextNode").replace("StreamReadException", "JsonParseException")));
		assertThat(expected).hasSize(789);
		assertThat(actual).containsExactlyEntriesOf(expected);
	}

	/** A malformed type file is still an IOException from TypeJsonLoader, with Jackson's parse error as cause. */
	@Test
	void typeJsonLoaderParseErrorIsIOException(@TempDir Path dir) throws Exception {
		Path bad = Files.writeString(dir.resolve("bad.json"), "{\"a\": ]");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> com.hitorro.jsontypesystem.resources.TypeJsonLoader.load(bad.toString()))
				.isExactlyInstanceOf(java.io.IOException.class)
				.hasCauseInstanceOf(tools.jackson.core.exc.StreamReadException.class);
	}

	private static Map<String, String> parse(byte[] bytes) {
		Map<String, String> m = new LinkedHashMap<>();
		for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
			if (line.isEmpty()) continue;
			int i = line.indexOf('=');
			m.put(line.substring(0, i), line.substring(i + 1));
		}
		return m;
	}
}
