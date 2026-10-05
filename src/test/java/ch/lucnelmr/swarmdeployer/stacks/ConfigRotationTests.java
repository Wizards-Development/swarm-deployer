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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author lucien
 */
class ConfigRotationTests {

	private static final String DIGEST = "22e8650d2783";

	@TempDir
	Path stack;

	@BeforeEach
	void setUp() throws IOException {
		Files.writeString(this.stack.resolve("config.yaml"), "healthy: true\n");
	}

	@Test
	void appendsTheDigestToTheNameAlreadyThere() throws IOException {
		assertThat(stamp("""
				configs:
				  gatus_config:
				    name: gatus_config_v1
				    file: ./config.yaml
				""")).isEqualTo("""
				configs:
				  gatus_config:
				    name: gatus_config_v1-%s
				    file: ./config.yaml
				""".formatted(DIGEST));
	}

	@Test
	void namesADeclarationThatHasNoName() throws IOException {
		assertThat(stamp("""
				secrets:
				  api_token:
				    file: ./config.yaml
				""")).isEqualTo("""
				secrets:
				  api_token:
				    name: api_token-%s
				    file: ./config.yaml
				""".formatted(DIGEST));
	}

	@Test
	void movesTheNameWhenTheFileChanges() throws IOException {
		Files.writeString(this.stack.resolve("config.yaml"), "healthy: false\n");

		assertThat(stamp(gatus())).doesNotContain(DIGEST).containsPattern("name: gatus_config_v1-[0-9a-f]{12}");
	}

	@Test
	void leavesAloneWhatDeclaresNoFile() throws IOException {
		String source = """
				configs:
				  shared:
				    external: true
				""";

		assertThat(stamp(source)).isEqualTo(source);
	}

	@Test
	void leavesEveryOtherByteWhereItWas() throws IOException {
		String stamped = stamp("""
				# Ce commentaire doit survivre.
				services:
				  gatus:
				    ports:
				      - 8080:8080
				    environment:
				      QUIET: no
				""" + gatus());

		assertThat(stamped).contains("# Ce commentaire doit survivre.")
			.contains("      - 8080:8080")
			.contains("      QUIET: no")
			.contains("name: gatus_config_v1-" + DIGEST);
	}

	@Test
	void refusesADeclarationWhoseFileIsMissing() {
		assertThatThrownBy(() -> stamp("""
				configs:
				  gatus_config:
				    file: ./absent.yaml
				""")).isInstanceOf(IllegalStateException.class).hasMessageContaining("./absent.yaml");
	}

	private String stamp(String compose) throws IOException {
		Path composeFile = this.stack.resolve("stack.yaml");
		Files.writeString(composeFile, compose);
		ConfigRotation.stamp(composeFile);

		return Files.readString(composeFile);
	}

	private static String gatus() {
		return """
				configs:
				  gatus_config:
				    name: gatus_config_v1
				    file: ./config.yaml
				""";
	}

}
