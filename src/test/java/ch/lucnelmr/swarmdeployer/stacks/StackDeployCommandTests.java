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

import ch.lucnelmr.swarmdeployer.stacks.StackDeployCommand.Convergence;
import ch.lucnelmr.swarmdeployer.stacks.StackDeployCommand.RemovedServices;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lucien
 */
class StackDeployCommandTests {

	private final Path composeFile = Path.of("stacks", "proxy", "stack.yaml").toAbsolutePath();

	@Test
	void keepsRemovedServicesAndReturnsAtOnce() {
		assertThat(command(Convergence.DETACHED, RemovedServices.KEPT)).containsExactly("docker", "stack", "deploy",
				"--with-registry-auth", "--detach", "-c", this.composeFile.toString(), "proxy");
	}

	@Test
	void prunesRemovedServicesAndAwaitsConvergence() {
		assertThat(command(Convergence.AWAITED, RemovedServices.PRUNED)).containsExactly("docker", "stack", "deploy",
				"--with-registry-auth", "--detach=false", "--prune", "-c", this.composeFile.toString(), "proxy");
	}

	private List<String> command(Convergence convergence, RemovedServices removedServices) {
		return new StackDeployCommand(convergence, removedServices, Duration.ofMinutes(5)).command(this.composeFile,
				"proxy");
	}

}
