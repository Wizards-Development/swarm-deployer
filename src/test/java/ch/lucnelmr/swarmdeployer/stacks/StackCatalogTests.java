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
class StackCatalogTests {

	@TempDir
	Path repo;

	@Test
	void readsTheDeclaredStacksInTheirFileOrder() throws IOException {
		Files.writeString(this.repo.resolve("stacks.yaml"), """
				proxy:
				  priority: 10
				  failOnDeploymentError: true
				postgres:
				  priority: 20
				esphome:
				""");

		assertThat(new StackCatalog(this.repo).declared().values()).containsExactly(new Stack("proxy", 10, true),
				new Stack("postgres", 20, false), new Stack("esphome", 0, false));
	}

	@Test
	void findsTheFoldersHoldingAStackFile() throws IOException {
		stack("proxy", "stack.yaml");
		stack("legacy", "stack.yml");
		Files.createDirectories(this.repo.resolve("provisioning"));
		stack(".github", "stack.yaml");

		StackCatalog catalog = new StackCatalog(this.repo);

		assertThat(catalog.folders()).containsExactly("legacy", "proxy");
		assertThat(catalog.composeFile(catalog.directory("legacy")).getFileName()).hasToString("stack.yml");
		assertThatThrownBy(() -> catalog.directory("provisioning")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsAnEmptyCatalogOnlyWhenThereIsNone() throws IOException {
		StackCatalog catalog = new StackCatalog(this.repo);

		assertThat(catalog.createIfMissing()).contains("stacks.yaml");
		assertThat(catalog.createIfMissing()).isEmpty();
		assertThat(catalog.declared()).isEmpty();
	}

	private void stack(String folder, String file) throws IOException {
		Files.createDirectories(this.repo.resolve(folder));
		Files.writeString(this.repo.resolve(folder).resolve(file), "services: {}\n");
	}

}
