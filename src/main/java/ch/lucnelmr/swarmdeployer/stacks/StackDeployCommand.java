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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs docker stack deploy on a rendered stack, always with --with-registry-auth: the
 * services carry the registry credentials to the nodes that pull their images.
 *
 * @author lucien
 */
public class StackDeployCommand {

	private static final Logger log = LoggerFactory.getLogger(StackDeployCommand.class);

	private final Convergence convergence;

	private final RemovedServices removedServices;

	private final Duration timeout;

	/**
	 * @param convergence {@link Convergence#DETACHED} returns once Swarm has the new
	 * spec, {@link Convergence#AWAITED} once every service has converged, health checks
	 * included
	 * @param removedServices what happens to a service still running in the stack but
	 * gone from its file
	 * @param timeout how long the command may run
	 */
	public StackDeployCommand(Convergence convergence, RemovedServices removedServices, Duration timeout) {
		this.convergence = convergence;
		this.removedServices = removedServices;
		this.timeout = timeout;
	}

	public void deploy(Path composeFile, String name, Map<String, String> environment) throws InterruptedException {
		log.info("Deploying stack: {}", name);

		StringBuilder output = new StringBuilder();
		Process process;

		try {
			ProcessBuilder builder = new ProcessBuilder(command(composeFile, name))
				.directory(composeFile.getParent().toFile())
				.redirectErrorStream(true);
			builder.environment().putAll(environment);
			process = builder.start();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					log.info("[{}] {}", name, line);
					output.append(line).append(System.lineSeparator());
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("docker stack deploy failed for " + name + ": " + ex.getMessage(), ex);
		}

		if (!process.waitFor(this.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("Deploy of stack " + name + " timed out");
		}
		if (process.exitValue() != 0) {
			throw new IllegalStateException(
					"docker stack deploy failed for " + name + " (exit " + process.exitValue() + ")\n" + output);
		}
	}

	List<String> command(Path composeFile, String name) {
		List<String> command = new ArrayList<>(
				List.of("docker", "stack", "deploy", "--with-registry-auth", this.convergence.flag));
		if (this.removedServices == RemovedServices.PRUNED) {
			command.add("--prune");
		}
		command.addAll(List.of("-c", composeFile.toAbsolutePath().toString(), name));
		return command;
	}

	public enum Convergence {

		DETACHED("--detach"), AWAITED("--detach=false");

		private final String flag;

		Convergence(String flag) {
			this.flag = flag;
		}

	}

	public enum RemovedServices {

		/**
		 * Left running: a service deleted from the stack file outlives it until removed
		 * by hand.
		 */
		KEPT,

		/**
		 * Removed by the deploy (--prune): the stack is exactly what its file says.
		 */
		PRUNED

	}

}
