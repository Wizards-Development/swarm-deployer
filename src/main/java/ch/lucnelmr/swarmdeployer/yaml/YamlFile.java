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

package ch.lucnelmr.swarmdeployer.yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class YamlFile {

	public static final String CONFIG = "config";

	public static final String STACKS = "stacks";

	public static final String STACK = "stack";

	private static final List<String> EXTENSIONS = List.of(".yaml", ".yml");

	private YamlFile() {
	}

	public static Optional<Path> find(Path directory, String name) {
		return EXTENSIONS.stream()
			.map(extension -> directory.resolve(name + extension))
			.filter(Files::isRegularFile)
			.findFirst();
	}

	public static Path resolve(Path directory, String name) {
		return find(directory, name).orElseGet(() -> directory.resolve(name + EXTENSIONS.getFirst()));
	}

	public static boolean exists(Path directory, String name) {
		return find(directory, name).isPresent();
	}

	public static boolean isNamed(String path, String name) {
		return EXTENSIONS.stream().anyMatch(extension -> path.endsWith(name + extension));
	}

}
