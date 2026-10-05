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

import ch.lucnelmr.swarmdeployer.yaml.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stacks of a repository: the folders holding a stack file, and their order in
 * stacks.yaml. A stack missing from stacks.yaml is not deployed automatically.
 *
 * @author lucien
 */
public class StackCatalog {

	private static final Logger log = LoggerFactory.getLogger(StackCatalog.class);

	private final Path repoDir;

	public StackCatalog(Path repoDir) {
		this.repoDir = repoDir;
	}

	public Optional<String> createIfMissing() throws IOException {
		if (YamlFile.exists(this.repoDir, YamlFile.STACKS)) {
			return Optional.empty();
		}
		Path file = YamlFile.resolve(this.repoDir, YamlFile.STACKS);
		Files.writeString(file, "{}\n", StandardCharsets.UTF_8);
		log.info("Created {} in the repository", file.getFileName());
		return Optional.of(file.getFileName().toString());
	}

	public Map<String, Stack> declared() {
		Optional<Path> file = YamlFile.find(this.repoDir, YamlFile.STACKS);
		if (file.isEmpty()) {
			return Map.of();
		}

		try (InputStream input = Files.newInputStream(file.get())) {
			Map<String, Map<String, Object>> document = new Yaml().load(input);
			if (document == null) {
				return Map.of();
			}

			Map<String, Stack> stacks = new LinkedHashMap<>();
			document.forEach((name, props) -> {
				Map<String, Object> values = props == null ? Map.of() : props;
				stacks.put(name, new Stack(name, integer(values.get("priority")),
						Boolean.TRUE.equals(values.get("failOnDeploymentError"))));
			});
			return stacks;
		}
		catch (Exception ex) {
			log.error("Failed to read stack definitions", ex);
			return Map.of();
		}
	}

	public List<String> folders() {
		try (var children = Files.list(this.repoDir)) {
			return children.filter(Files::isDirectory)
				.filter(path -> !path.getFileName().toString().startsWith("."))
				.filter(path -> YamlFile.exists(path, YamlFile.STACK))
				.map(path -> path.getFileName().toString())
				.sorted()
				.toList();
		}
		catch (IOException ex) {
			log.warn("Could not list stack folders: {}", ex.toString());
			return List.of();
		}
	}

	public Path directory(String name) {
		if (!folders().contains(name)) {
			throw new IllegalArgumentException("No stack folder named " + name + " in the repository");
		}
		return this.repoDir.resolve(name);
	}

	public Path composeFile(Path directory) {
		return YamlFile.find(directory, YamlFile.STACK)
			.orElseThrow(() -> new IllegalStateException("No stack file in " + directory.getFileName()));
	}

	private static int integer(Object value) {
		return value instanceof Number number ? number.intValue() : 0;
	}

}
