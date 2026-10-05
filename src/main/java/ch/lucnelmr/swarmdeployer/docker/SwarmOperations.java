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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Config;
import com.github.dockerjava.api.model.ContainerSpec;
import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.ServiceSpec;
import com.github.dockerjava.api.model.TaskSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a deployer does to Swarm through its API rather than the CLI: drop the config
 * versions no service uses any more, restart a service, and point one at another image,
 * itself included.
 *
 * @author lucien
 */
public class SwarmOperations {

	private static final Logger log = LoggerFactory.getLogger(SwarmOperations.class);

	private static final Pattern ROTATED = Pattern.compile(".+-[0-9a-f]{12}");

	protected final DockerClient dockerClient;

	public SwarmOperations(DockerClient dockerClient) {
		this.dockerClient = dockerClient;
	}

	public Optional<Service> findService(String name) {
		try {
			return this.dockerClient.listServicesCmd()
				.exec()
				.stream()
				.filter(service -> service.getSpec() != null && name.equals(service.getSpec().getName()))
				.findFirst();
		}
		catch (Exception ex) {
			log.warn("Could not look up service {}: {}", name, ex.toString());
			return Optional.empty();
		}
	}

	/**
	 * Removes every config versioned by
	 * {@link ch.lucnelmr.swarmdeployer.stacks.ConfigRotation}. Docker refuses to remove
	 * one still in use: those stay until a later deploy.
	 */
	public void pruneRotatedConfigs() {
		List<Config> configs;

		try {
			configs = this.dockerClient.listConfigsCmd().exec();
		}
		catch (Exception ex) {
			log.warn("Could not list the Swarm configs: {}", ex.toString());
			return;
		}

		for (Config config : configs) {
			String name = config.getSpec() == null ? null : config.getSpec().getName();

			if (name == null || !ROTATED.matcher(name).matches()) {
				continue;
			}

			try {
				this.dockerClient.removeConfigCmd(config.getId()).exec();
				log.info("Removed {}, no longer used by any service", name);
			}
			catch (Exception ex) {
				log.debug("Kept {}: {}", name, ex.toString());
			}
		}
	}

	public void restartService(String name) {
		Service service = requireService(name);
		ServiceSpec spec = service.getSpec();
		TaskSpec taskSpec = requireTaskSpec(spec, name);

		int forceUpdate = taskSpec.getForceUpdate() == null ? 0 : taskSpec.getForceUpdate();
		taskSpec.withForceUpdate(forceUpdate + 1);

		log.info("Forcing a restart of {}", name);
		update(service, spec);
	}

	/**
	 * Points a service at another image. Called on the deployer's own service, it is how
	 * the deployer updates itself: Swarm replaces it, the process making the call
	 * included.
	 */
	public void updateServiceImage(String name, String image) {
		Service service = requireService(name);
		ServiceSpec spec = service.getSpec();
		ContainerSpec containerSpec = requireTaskSpec(spec, name).getContainerSpec();

		if (containerSpec == null) {
			throw new IllegalStateException("Service " + name + " has no container spec to update");
		}

		log.info("Pointing {} at {}", name, image);
		containerSpec.withImage(image);
		update(service, spec);
	}

	private void update(Service service, ServiceSpec spec) {
		Long version = service.getVersion() != null ? service.getVersion().getIndex() : null;
		this.dockerClient.updateServiceCmd(service.getId(), spec).withVersion(version).exec();
	}

	private Service requireService(String name) {
		return findService(name)
			.orElseThrow(() -> new IllegalStateException("No swarm service named " + name + " on this cluster"));
	}

	private static TaskSpec requireTaskSpec(ServiceSpec spec, String name) {
		if (spec == null || spec.getTaskTemplate() == null) {
			throw new IllegalStateException("Service " + name + " has no task template");
		}
		return spec.getTaskTemplate();
	}

}
