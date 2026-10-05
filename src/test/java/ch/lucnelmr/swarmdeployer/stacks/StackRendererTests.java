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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author lucien
 */
class StackRendererTests {

	@TempDir
	Path stack;

	private final EncryptionService encryptionService = new EncryptionService("test-cipher-key");

	private final StackRenderer renderer = new StackRenderer(this.encryptionService);

	@BeforeEach
	void setUp() throws IOException {
		Files.writeString(this.stack.resolve("stack.yaml"), """
				configs:
				  gatus_config:
				    name: gatus_config_v1
				    file: ./config.yaml
				""");
		Files.writeString(this.stack.resolve("config.yaml"), "url: https://status.{{domain}}\n");
	}

	@Test
	void movesTheNameWhenASubstitutionChanges() throws IOException {
		String before = stampedName(Map.of("domain", "dev.hortiprod.ch"));

		assertThat(stampedName(Map.of("domain", "hortiprod.ch"))).isNotEqualTo(before).startsWith("gatus_config_v1-");
	}

	@Test
	void leavesAnUnresolvedPlaceholderAloneInsteadOfFailing() throws IOException {
		Files.writeString(this.stack.resolve("README.md"), "Ce noeud est {{nodename}}.\n");

		StackRenderer.Rendered rendered = this.renderer.render(this.stack, "gatus", Map.of("domain", "hortiprod.ch"));

		assertThat(Files.readString(rendered.directory().resolve("README.md"))).contains("{{nodename}}");
		assertThat(rendered.unresolved()).hasSize(1);
		assertThat(rendered.unresolved().get(0)).contains("README.md", "{{nodename}}");
	}

	@Test
	void opensSealedValuesAndLeavesSwarmTemplatesAlone() throws IOException {
		String sealed = this.encryptionService.wrap(this.encryptionService.encrypt("hunter2"));
		Files.writeString(this.stack.resolve("stack.yaml"),
				"hostname: \"{{.Node.Hostname}}\"\npassword: '" + sealed + "'\n");

		StackRenderer.Rendered rendered = this.renderer.render(this.stack, "gatus", Map.of("domain", "hortiprod.ch"));

		assertThat(Files.readString(rendered.directory().resolve("stack.yaml")))
			.isEqualTo("hostname: \"{{.Node.Hostname}}\"\npassword: 'hunter2'\n");
		assertThat(rendered.unresolved()).isEmpty();
	}

	@Test
	void refusesToRenderAValueItCannotOpen() throws IOException {
		Files.writeString(this.stack.resolve("stack.yaml"), "password: '{cipher}v2.not-sealed-here{cipher}'\n");

		assertThatThrownBy(() -> this.renderer.render(this.stack, "gatus", Map.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("stack.yaml");
	}

	@Test
	void copiesBinaryFilesUntouched() throws IOException {
		byte[] blob = { 1, 0, '{', '{', 'd', 'o', 'm', 'a', 'i', 'n', '}', '}' };
		Files.write(this.stack.resolve("blob.bin"), blob);

		StackRenderer.Rendered rendered = this.renderer.render(this.stack, "gatus", Map.of("domain", "hortiprod.ch"));

		assertThat(Files.readAllBytes(rendered.directory().resolve("blob.bin"))).isEqualTo(blob);
	}

	@Test
	void deletesTheRenderedCopy() throws IOException {
		StackRenderer.Rendered rendered = this.renderer.render(this.stack, "gatus", Map.of("domain", "hortiprod.ch"));

		StackRenderer.delete(rendered.directory());

		assertThat(rendered.directory()).doesNotExist();
	}

	private String stampedName(Map<String, String> values) throws IOException {
		Path composeFile = this.renderer.render(this.stack, "gatus", values).directory().resolve("stack.yaml");
		ConfigRotation.stamp(composeFile);

		return Files.readString(composeFile)
			.lines()
			.filter(line -> line.contains("name:"))
			.findFirst()
			.orElseThrow()
			.replace("name:", "")
			.trim();
	}

}
