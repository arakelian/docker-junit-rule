/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arakelian.docker.junit.model.DockerConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * <p>
 * JUnit rule starting a docker container before a test and killing it afterwards.
 *
 * This rule allows a container to keep running between different unit tests to improve performance.
 * It is smart enough to shutdown the containers when all of the unit tests complete.
 * </p>
 * <p>
 * NOTES: Originally adapted from https://github.com/geowarin/docker-junit-rule before making a wide
 * variety of changes.
 * </p>
 *
 * @author Greg Arakelian
 */
public class DockerRule implements TestRule {
    public class StatementWithDockerRule extends Statement {
        private final Statement statement;

        public StatementWithDockerRule(final Statement statement) {
            this.statement = statement;
        }

        @Override
        public void evaluate() throws Throwable {
            if (configs.size() == 0) {
                // not using docker at all
                statement.evaluate();
                return;
            }

            for (final DockerConfig config : configs) {
                final Container container = register(config);

                // while this rule is executing, we don't want the container stopped
                container.addRef();
                try {
                    DockerRule.this.container = container;
                    container.start();
                    statement.evaluate();
                } catch (final Exception e) {
                    container.stop();
                    throw new RuntimeException("Unable to start docker container: " + config, e);
                } finally {
                    // release lock on container
                    container.releaseRef();

                    DockerRule.this.container = null;
                    if (!config.isAllowRunningBetweenUnitTests()) {
                        container.stop();
                    }
                }
            }
        }
    }

    /** Logging **/
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRule.class);

    /** Cache of docker contexts **/
    private static final Map<DockerConfig, Container> CONTAINERS = new ConcurrentHashMap<>();

    /** Synchronization lock **/
    private static final transient Lock CONTAINERS_LOCK = new ReentrantLock();

    /**
     * Returns a list of registered containers.
     *
     * @return a list of registered containers.
     */
    public static List<Container> getRegisteredContainers() {
        CONTAINERS_LOCK.lock();
        try {
            return ImmutableList.copyOf(CONTAINERS.values());
        } finally {
            CONTAINERS_LOCK.unlock();
        }
    }

    /**
     * Returns a {@link Container} for the given {@link DockerConfig}. If the container has not been
     * created yet, this method will create it; otherwise, it will return the previously created
     * container. There is a single <code>Container</code> associated with any given
     * <code>DockerConfig</code>.
     *
     * @param config
     *            container configuration.
     * @return a {@link Container} for the given {@link DockerConfig}.
     */
    protected static Container register(final DockerConfig config) {
        CONTAINERS_LOCK.lock();
        try {
            Container container = CONTAINERS.get(config);
            if (container == null) {
                container = new Container(config);
                CONTAINERS.put(config, container);
            }
            LOGGER.info("Registered docker configuration: {}", config);
            return container;
        } finally {
            CONTAINERS_LOCK.unlock();
        }
    }

    /**
     * Starts a docker container with the given configuration.
     *
     * This method is particularly useful when you want to start a container dynamically.
     *
     * @param config
     *            container configuration
     * @param stopOthers
     *            true to stop other running containers; useful to limit the amount of memory that a
     *            series of unit tests will consume.
     * @return a {@link Container} representing the started container.
     * @throws Exception
     *             if the container cannot be started
     */
    public static Container start(final DockerConfig config, final boolean stopOthers) throws Exception {
        Preconditions.checkArgument(config != null, "config must be non-null");

        if (stopOthers) {
            getRegisteredContainers().stream() //
                    .filter(container -> {
                        return container.isStarted() //
                                && container.getRefCount() == 0 //
                                && config.equals(container.getConfig());
                    }) //
                    .forEach(container -> container.stop());
        }

        final Container container = register(config);
        container.start();
        return container;
    }

    /** Mapping of configurations **/
    private final List<DockerConfig> configs = Lists.newArrayList();

    /** Docker container **/
    private Container container;

    public DockerRule() {
    }

    public DockerRule(final DockerConfig... configs) {
        for (final DockerConfig config : configs) {
            addDockerConfig(config);
        }
    }

    protected final void addDockerConfig(final DockerConfig config) {
        Preconditions.checkState(!hasConfig(config), "Container %s already defined");
        this.configs.add(config);
    }

    @Override
    public final Statement apply(final Statement base, final Description description) {
        return new StatementWithDockerRule(base);
    }

    /**
     * Returns the {@link Container} being used by the current test.
     *
     * @return the {@link Container} being used by the current test.
     */
    public final Container getContainer() {
        return container;
    }

    public final boolean hasConfig(final DockerConfig config) {
        return this.configs.contains(config);
    }
}
