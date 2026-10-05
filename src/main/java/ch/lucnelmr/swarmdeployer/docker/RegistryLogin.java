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

package ch.lucnelmr.swarmdeployer.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * docker login into a DOCKER_CONFIG of the deployer's own, never the host's: the
 * credentials come from the deployer's configuration, which keeps it movable without
 * preparing the host. Every docker command that must carry them runs with
 * {@link #commandEnvironment()}.
 *
 * @author lucien
 */
public class RegistryLogin {

	private static final Logger log = LoggerFactory.getLogger(RegistryLogin.class);

	private static final int LOGIN_TIMEOUT_SECONDS = 60;

	private final Path configDirectory;

	private volatile List<String> loggedInWith;

	public RegistryLogin(Path configDirectory) {
		this.configDirectory = configDirectory;
	}

	public Map<String, String> commandEnvironment() {
		return Map.of("DOCKER_CONFIG", this.configDirectory.toAbsolutePath().toString());
	}

	/**
	 * Logs in again when the credentials change, and on every call as long as it has not
	 * worked: they can then be fixed or rotated without a restart.
	 * @return whether the deployer is logged in with these credentials
	 */
	public synchronized boolean ensureLoggedIn(String registry, String user, String token) {
		if (blank(registry) || blank(user) || blank(token)) {
			log.warn("No credentials for {}: private images will be rejected on the nodes that have to pull them",
					registry);
			this.loggedInWith = null;
			return false;
		}

		List<String> credentials = List.of(registry, user, token);

		if (credentials.equals(this.loggedInWith)) {
			return true;
		}

		this.loggedInWith = login(registry, user, token) ? credentials : null;
		return this.loggedInWith != null;
	}

	private boolean login(String registry, String user, String token) {
		StringBuilder output = new StringBuilder();
		Process process;

		try {
			Files.createDirectories(this.configDirectory);

			ProcessBuilder builder = new ProcessBuilder("docker", "login", registry, "-u", user, "--password-stdin")
				.redirectErrorStream(true);
			builder.environment().putAll(commandEnvironment());
			process = builder.start();

			try (OutputStream stdin = process.getOutputStream()) {
				stdin.write(token.getBytes(StandardCharsets.UTF_8));
			}

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append(System.lineSeparator());
				}
			}
		}
		catch (IOException ex) {
			log.warn("Could not run docker login for {}: {}", registry, ex.getMessage());
			return false;
		}

		try {
			if (!process.waitFor(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				log.warn("docker login to {} timed out", registry);
				return false;
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("docker login to {} was interrupted", registry);
			return false;
		}

		if (process.exitValue() != 0) {
			log.warn("docker login to {} failed as {} (exit {}): {}", registry, user, process.exitValue(),
					output.toString().trim());
			return false;
		}

		log.info("Logged into {} as {} — deployments will carry the registry auth", registry, user);
		return true;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
