/*
 * Copyright 2019-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.lucnelmr.swarmdeployer.stacks;

import ch.lucnelmr.swarmdeployer.encryption.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Copies a stack folder into a temporary one, substituting {{key}} and opening
 * {cipher}…{cipher} in every text file. The copy holds clear secrets: delete it once
 * deployed.
 *
 * <p>
 * A key that is unknown or blank is left as {{key}} and reported, rather than replaced by
 * nothing: what to do about it is the caller's decision.
 *
 * @author lucien
 */
public class StackRenderer {

	private static final Logger log = LoggerFactory.getLogger(StackRenderer.class);

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(.*?)}}");

	private static final int SNIFF_LENGTH = 8192;

	private final EncryptionService encryptionService;

	public StackRenderer(EncryptionService encryptionService) {
		this.encryptionService = encryptionService;
	}

	public Rendered render(Path source, String name, Map<String, String> values) throws IOException {
		Path target = Files.createTempDirectory("swarm-stack-" + name + "-");
		List<String> unresolved = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				Path destination = target.resolve(source.relativize(path).toString());

				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
					continue;
				}

				Files.createDirectories(destination.getParent());
				byte[] bytes = Files.readAllBytes(path);

				if (isBinary(bytes)) {
					Files.write(destination, bytes);
					continue;
				}

				try {
					String content = substitute(new String(bytes, StandardCharsets.UTF_8), values, path, unresolved);
					Files.writeString(destination, this.encryptionService.decryptContent(content),
							StandardCharsets.UTF_8);
				}
				catch (RuntimeException ex) {
					throw new IllegalStateException(
							"Cannot render " + source.relativize(path) + " of stack " + name + ": " + ex.getMessage(),
							ex);
				}
			}
		}

		return new Rendered(target, List.copyOf(unresolved));
	}

	public static void delete(Path root) {
		if (root == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
		}
		catch (IOException | UncheckedIOException ex) {
			log.error("Could not clean up {} — it holds decrypted values", root, ex);
		}
	}

	public record Rendered(Path directory, List<String> unresolved) {
	}

	private static String substitute(String content, Map<String, String> values, Path path, List<String> unresolved) {
		Matcher matcher = PLACEHOLDER.matcher(content);
		StringBuilder result = new StringBuilder();

		Set<String> unknown = new LinkedHashSet<>();
		Set<String> blank = new LinkedHashSet<>();

		while (matcher.find()) {
			String key = matcher.group(1).trim();

			if (key.startsWith(".")) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
				continue;
			}

			String value = values.get(key);

			if (!values.containsKey(key)) {
				unknown.add(key);
			}
			else if (value == null || value.isBlank()) {
				blank.add(key);
			}
			else {
				matcher.appendReplacement(result, Matcher.quoteReplacement(value));
				continue;
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
		}
		matcher.appendTail(result);

		if (!unknown.isEmpty() || !blank.isEmpty()) {
			unresolved.add(report(unknown, blank, path));
		}

		return result.toString();
	}

	private static String report(Set<String> unknown, Set<String> blank, Path path) {
		StringBuilder message = new StringBuilder(path.getFileName().toString());

		if (!unknown.isEmpty()) {
			message.append(" — inconnus : ").append(quote(unknown));
		}
		if (!blank.isEmpty()) {
			message.append(" — sans valeur : ").append(quote(blank));
		}

		return message.toString();
	}

	private static String quote(Set<String> keys) {
		return keys.stream().map(key -> "{{" + key + "}}").collect(Collectors.joining(", "));
	}

	private static boolean isBinary(byte[] bytes) {
		int limit = Math.min(bytes.length, SNIFF_LENGTH);
		for (int index = 0; index < limit; index++) {
			if (bytes[index] == 0) {
				return true;
			}
		}
		return false;
	}

}
