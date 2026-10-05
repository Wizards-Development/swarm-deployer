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

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * @author lucien
 */

public final class ConfigRotation {

	private static final List<String> SECTIONS = List.of("configs", "secrets");

	private static final int DIGEST_LENGTH = 12;

	private ConfigRotation() {
	}

	public static void stamp(Path composeFile) throws IOException {
		String source = Files.readString(composeFile, StandardCharsets.UTF_8);
		List<Edit> edits = new ArrayList<>();

		if (parse(source, composeFile) instanceof MappingNode document) {
			for (NodeTuple section : document.getValue()) {
				if (SECTIONS.contains(text(section.getKeyNode()))
						&& section.getValueNode() instanceof MappingNode declarations) {
					declarations.getValue().forEach(declaration -> stamp(declaration, composeFile, edits));
				}
			}
		}

		if (!edits.isEmpty()) {
			StringBuilder stamped = new StringBuilder(source);
			edits.reversed().forEach(edit -> stamped.replace(edit.from(), edit.to(), edit.text()));
			Files.writeString(composeFile, stamped.toString(), StandardCharsets.UTF_8);
		}
	}

	private static void stamp(NodeTuple declaration, Path composeFile, List<Edit> edits) {
		if (!(declaration.getValueNode() instanceof MappingNode body)) {
			return;
		}

		String file = text(field(body, "file"));
		if (file == null) {
			return;
		}

		String suffix = "-" + digest(composeFile.getParent().resolve(file), file);
		ScalarNode name = field(body, "name");

		if (name != null) {
			edits.add(new Edit(name.getStartMark().getIndex(), name.getEndMark().getIndex(), name.getValue() + suffix));
		}
		else if (body.getFlowStyle() != DumperOptions.FlowStyle.FLOW) {
			Mark start = body.getStartMark();
			String line = "name: " + text(declaration.getKeyNode()) + suffix;

			edits.add(new Edit(start.getIndex(), start.getIndex(), line + "\n" + " ".repeat(start.getColumn())));
		}
	}

	private static ScalarNode field(MappingNode body, String name) {
		return body.getValue()
			.stream()
			.filter(field -> name.equals(text(field.getKeyNode())))
			.map(NodeTuple::getValueNode)
			.filter(ScalarNode.class::isInstance)
			.map(ScalarNode.class::cast)
			.findFirst()
			.orElse(null);
	}

	private static String digest(Path file, String declared) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
			return HexFormat.of().formatHex(hash).substring(0, DIGEST_LENGTH);
		}
		catch (IOException | NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Cannot read " + declared + ", declared by this stack: " + ex, ex);
		}
	}

	private static Node parse(String source, Path composeFile) {
		try {
			return new Yaml().compose(new StringReader(source));
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Cannot read " + composeFile.getFileName() + ": " + ex.getMessage(), ex);
		}
	}

	private static String text(Node node) {
		return node instanceof ScalarNode scalar ? scalar.getValue() : null;
	}

	private record Edit(int from, int to, String text) {
	}

}
