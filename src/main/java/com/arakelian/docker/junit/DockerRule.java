/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arakelian.docker.junit;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;

/**
 * <p>
 * JUnit rule starting a docker container before the test and killing it afterwards.
 * </p>
 * <p>
 * Uses spotify/docker-client. Adapted from https://github.com/geowarin/docker-junit-rule
 * </p>
 * 
 * @auth Greg Arakelian
 */
public abstract class DockerRule implements TestRule, ContainerListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(DockerRule.class);

	/** Cache of docker contexts **/
	private static final Map<String, Container> CONTAINER_CACHE = new ConcurrentHashMap<>();

	/** Docker rule configuration **/
	final DockerConfig config;

	/** Docker container **/
	private Container container;

	public DockerRule(final DockerConfig config) {
		this.config = config;
	}

	@Override
	public final Statement apply(final Statement base, final Description description) {
		// share or create container context
		synchronized (CONTAINER_CACHE) {
			final String name = config.getName();
			Container container = CONTAINER_CACHE.get(name);
			if (container == null) {
				container = createContainer();
				CONTAINER_CACHE.put(name, container);
			}
			this.container = container;
		}

		try {
			container.start();
		} catch (final Exception e) {
			container.stop();
			throw new RuntimeException("Unable to start docker container", e);
		}

		LOGGER.debug("Applying {} to test class [{}]", this.getClass().getSimpleName(),
				description.getTestClass().getName());
		return base;
	}

	protected abstract void configureContainer(final ContainerConfig.Builder builder);

	protected abstract void configureHost(final HostConfig.Builder builder);

	protected Container createContainer() {
		// host configuration
		final HostConfig.Builder hostConfigBuilder = HostConfig.builder() //
				.portBindings(createPortBindings(config.getPorts()));
		configureHost(hostConfigBuilder);
		final HostConfig hostConfig = hostConfigBuilder.build();

		// container configuration
		final ContainerConfig.Builder configBuilder = ContainerConfig.builder() //
				.hostConfig(hostConfig) //
				.image(config.getImage()) //
				.networkDisabled(false) //
				.exposedPorts(config.getPorts());
		configureContainer(configBuilder);
		final ContainerConfig containerConfig = configBuilder.build();

		return new Container(config.getName(), containerConfig, config.isAlwaysRemoveContainer(), this);
	}

	private Map<String, List<PortBinding>> createPortBindings(final String[] exposedPorts) {
		final Map<String, List<PortBinding>> portBindings = new HashMap<>();
		if (exposedPorts != null) {
			for (final String port : exposedPorts) {
				final List<PortBinding> hostPorts = Collections
						.singletonList(PortBinding.randomPort("0.0.0.0"));
				portBindings.put(port, hostPorts);
			}
		}
		return portBindings;
	}

	public final Container getContainer() {
		return container;
	}
}
