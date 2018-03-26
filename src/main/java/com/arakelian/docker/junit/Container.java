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

import static com.spotify.docker.client.DockerClient.LogsParam.follow;
import static com.spotify.docker.client.DockerClient.LogsParam.stderr;
import static com.spotify.docker.client.DockerClient.LogsParam.stdout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arakelian.docker.junit.model.ContainerConfigurer;
import com.arakelian.docker.junit.model.DockerConfig;
import com.arakelian.docker.junit.model.HostConfigurer;
import com.arakelian.docker.junit.model.StartedListener;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;

/**
 * Handles life-cycle management of a Docker container, e.g. starting and stopping.
 *
 * @author Greg Arakelian
 */
public class Container {
    @Value.Immutable
    public interface Binding {
        public String getHost();

        public int getPort();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRule.class);

    /**
     * Returns true if a connection can be established to the given socket address within the
     * timeout provided.
     *
     * @param socketAddress
     *            socket address
     * @param timeoutMsecs
     *            timeout
     * @return true if a connection can be established to the given socket address
     */
    public static boolean isSocketAlive(final SocketAddress socketAddress, final int timeoutMsecs) {
        final Socket socket = new Socket();
        try {
            socket.connect(socketAddress, timeoutMsecs);
            socket.close();
            return true;
        } catch (final SocketTimeoutException exception) {
            return false;
        } catch (final IOException exception) {
            return false;
        }
    }

    private final ContainerConfig containerConfig;

    /**
     * The docker container name. Same value that you would see if you ran "docker ps -a" in the
     * NAMES column.
     */
    private final String name;

    /** Docker container configuration **/
    private final DockerConfig config;

    /** Access to docker client API **/
    private DockerClient client;

    /** Docker container information **/
    private ContainerInfo info;

    /** Container id **/
    private String containerId;

    /** Reference to the JVM shutdown hook, if registered */
    private Thread shutdownHook;

    /**
     * Arbitrary data that can be associated with this container.
     */
    private Map<String, Object> context = Maps.newLinkedHashMap();

    /** Synchronization monitor for the "refresh" and "destroy" */
    private final Object startStopMonitor = new Object();

    /** Flag that indicates whether the container has been started */
    private final AtomicBoolean started = new AtomicBoolean();

    /** Flag that indicates whether this container has been stopped already */
    private final AtomicBoolean stopped = new AtomicBoolean();

    /**
     * When reference count is non-zero, we cannot close this container because it is required as
     * part of a unit test.
     **/
    private final AtomicInteger refCount = new AtomicInteger();

    /**
     * Construct a container.
     *
     * @param config
     *            docker container configuration
     */
    public Container(final DockerConfig config) {
        this.name = config.getName();
        this.config = config;
        this.containerConfig = createContainerConfig(config).build();
    }

    public final int addRef() {
        return refCount.incrementAndGet();
    }

    public final DockerClient getClient() {
        return client;
    }

    public DockerConfig getConfig() {
        return config;
    }

    public <T> T getData(String name, Class<T> clazz) {
        final Object value = context.get(name);
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        throw new IllegalStateException(name + " must be non-null");
    }

    public void setData(String name, Object value) {
        context.put(name, value);
    }

    public final String getContainerId() {
        assertStarted();
        return containerId;
    }

    public final ContainerInfo getInfo() {
        assertStarted();
        return info;
    }

    /**
     * Returns the host and port information for the given Docker port name.
     *
     * @param portName
     *            Docker port name, e.g. '9200/tcp'
     * @return the host and port information
     * @throws IllegalStateException
     *             if the container has not been started
     * @throws IllegalArgumentException
     *             the given port name does not exist
     */
    public final Binding getPortBinding(final String portName)
            throws IllegalStateException, IllegalArgumentException {
        assertStarted();

        final Map<String, List<PortBinding>> ports = info.networkSettings().ports();
        final List<PortBinding> portBindings = ports.get(portName);
        if (portBindings == null || portBindings.isEmpty()) {
            throw new IllegalArgumentException("Unknown port binding: " + portName);
        }

        final PortBinding portBinding = portBindings.get(0);
        return ImmutableBinding.builder() //
                .host(portBinding.hostIp()) //
                .port(Integer.parseInt(portBinding.hostPort())) //
                .build();
    }

    public final int getRefCount() {
        return refCount.get();
    }

    /**
     * Returns true if container has been started.
     *
     * @return true if container has been started.
     */
    public boolean isStarted() {
        return started.get();
    }

    public final int releaseRef() {
        return refCount.decrementAndGet();
    }

    public void start() throws Exception {
        synchronized (this.startStopMonitor) {
            if (this.started.get()) {
                Preconditions.checkState(client != null, "client must be non-null");
                final ContainerInfo inspect = client.inspectContainer(name);
                final ContainerState state = inspect.state();
                if (!state.running()) {
                    throw new IllegalStateException(
                            "Container " + name + " is not running (check exit code!): " + state);
                }

                // already running
                return;
            }

            LOGGER.info("Starting container {} with {}", containerConfig.image(), containerConfig);
            this.stopped.set(false);
            this.started.set(true);

            registerShutdownHook();
            try {
                client = createDockerClient();
            } catch (final Exception e) {
                stop();
                throw e;
            }

            boolean created = false;
            boolean running = false;
            if (name != null) {
                try {
                    final ContainerInfo existing = client.inspectContainer(name);
                    final ContainerState state = existing.state();

                    final String image = containerConfig.image();
                    if (!image.equals(existing.config().image())) {
                        // existing container has different image
                        if (state != null && state.running() && state.running().booleanValue()) {
                            stopContainerQuietly(image, existing.id());
                        }
                        removeContainerQuietly(existing.id());
                    } else {
                        created = true;
                        running = state != null && state.running() && state.running().booleanValue();
                        containerId = existing.id();
                        info = existing;
                    }
                } catch (final DockerException e) {
                    // we could not get information about container, fall through
                }
            }

            if (!created) {
                pullImage();
                containerId = createContainer();
            }
            if (!running) {
                startContainer();
                info = client.inspectContainer(containerId);
            }

            // notify listener than container has been started
            for (final StartedListener listener : config.getStartedListener()) {
                listener.onStarted(this);
            }
        }
    }

    /**
     * Instructs Docker to stop the container
     */
    public void stop() {
        synchronized (this.startStopMonitor) {
            doStop();

            // If we registered a JVM shutdown hook, we don't need it anymore now:
            // We've already explicitly closed the context.
            if (this.shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                } catch (final IllegalStateException ex) {
                    // ignore - VM is already shutting down
                }
            }
        }
    }

    /**
     * Wait for nth occurrence of message to appear in docker logs within the specified timeframe.
     *
     * @param timeout
     *            timeout value
     * @param unit
     *            timeout units
     * @param messages
     *            The sequence of messages to wait for
     * @throws DockerException
     *             if docker throws exception while tailing logs
     * @throws InterruptedException
     *             if thread interrupted while waiting for timeout
     */
    public final void waitForLog(final int timeout, final TimeUnit unit, final String... messages)
            throws DockerException, InterruptedException {
        final long startTime = System.currentTimeMillis();
        final long timeoutTimeMillis = startTime + TimeUnit.MILLISECONDS.convert(timeout, unit);

        int n = 0;
        LOGGER.info("Tailing logs for \"{}\"", messages[n]);
        final LogStream logs = client.logs(containerId, follow(), stdout(), stderr());
        while (logs.hasNext()) {
            if (Thread.interrupted()) {
                // manual check for thread being terminated
                throw new InterruptedException();
            }
            final String message = messages[n];
            final String log = StandardCharsets.UTF_8.decode(logs.next().content()).toString();
            if (log.contains(message)) {
                LOGGER.info(
                        "Successfully received \"{}\" after {}ms",
                        message,
                        System.currentTimeMillis() - startTime);
                if (++n >= messages.length) {
                    return;
                }
            }
            if (startTime > timeoutTimeMillis) {
                throw new IllegalStateException("Timeout waiting for log \"" + message + "\"");
            }
        }
    }

    /**
     * Wait for a sequence of messages to appear in docker logs.
     *
     * @param messages
     *            The sequence of messages to wait for
     * @throws DockerException
     *             if docker throws exception while tailing logs
     * @throws InterruptedException
     *             if thread interrupted while waiting for timeout
     */
    public final void waitForLog(final String... messages) throws DockerException, InterruptedException {
        waitForLog(30, TimeUnit.SECONDS, messages);
    }

    /**
     * Wait for the specified port to accept socket connection.
     *
     * @param portName
     *            Docker port name, e.g. '9200/tcp'
     */
    public final void waitForPort(final String portName) {
        final Binding binding = getPortBinding(portName);
        waitForPort(binding.getHost(), binding.getPort());
    }

    /**
     * Wait for the specified port to accept socket connection.
     *
     * @param host
     *            host name
     * @param port
     *            port number
     */
    public final void waitForPort(final String host, final int port) {
        waitForPort(host, port, 30, TimeUnit.SECONDS);
    }

    /**
     * Wait for the specified port to accept socket connection within a given time frame.
     *
     * @param host
     *            target host name
     * @param port
     *            target port number
     * @param timeout
     *            timeout value
     * @param unit
     *            timeout units
     * @throws IllegalArgumentException
     *             if invalid arguments are passed to method
     */
    public final void waitForPort(final String host, final int port, final int timeout, final TimeUnit unit)
            throws IllegalArgumentException {
        Preconditions.checkArgument(host != null, "host must be non-null");
        Preconditions.checkArgument(port > 0, "port must be positive integer");
        Preconditions.checkArgument(unit != null, "unit must be non-null");

        final long startTime = System.currentTimeMillis();
        final long timeoutTime = startTime + TimeUnit.MILLISECONDS.convert(timeout, unit);

        LOGGER.info("Waiting for docker container at {}:{} for {} {}", host, port, timeout, unit);
        final SocketAddress address = new InetSocketAddress(host, port);
        for (;;) {
            if (isSocketAlive(address, 2000)) {
                LOGGER.info(
                        "Successfully connected to container at {}:{} after {}ms",
                        host,
                        port,
                        System.currentTimeMillis() - startTime);
                return;
            }
            try {
                Thread.sleep(1000);
                LOGGER.info("Waiting for container at {}:{}", host, port);
                if (System.currentTimeMillis() > timeoutTime) {
                    LOGGER.error("Failed to connect with container at {}:{}", host, port);
                    throw new IllegalStateException(
                            "Timeout waiting for socket connection to " + host + ":" + port);
                }
            } catch (final InterruptedException ie) {
                throw new IllegalStateException(ie);
            }
        }
    }

    /**
     * Wait for the specified port to accept socket connection within a given time frame.
     *
     * @param portName
     *            docker port name, e.g. 9200/tcp
     * @param timeout
     *            timeout value
     * @param unit
     *            timeout units
     */
    public final void waitForPort(final String portName, final int timeout, final TimeUnit unit) {
        final Binding binding = getPortBinding(portName);
        waitForPort(binding.getHost(), binding.getPort(), timeout, unit);
    }

    private void assertStarted() {
        synchronized (this.startStopMonitor) {
            if (!started.get()) {
                throw new IllegalStateException("Docker container not started: " + containerConfig);
            }
        }
    }

    private String createContainer() throws InterruptedException {
        final long startTime = System.currentTimeMillis();

        // equivalent to "docker run"
        final ContainerCreation container;
        try {
            if (name == null) {
                container = client.createContainer(containerConfig);
            } else {
                container = client.createContainer(containerConfig, name);
            }
            return container.id();
        } catch (final DockerException e) {
            throw new IllegalStateException("Unable to create container using " + containerConfig, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Created container {} in {}ms", containerConfig.image(), elapsedMillis);
        }
    }

    private void doStop() {
        if (this.started.get() && this.stopped.compareAndSet(false, true)) {
            LOGGER.info("Stopping {} container", config.getName());
            this.started.set(false);

            if (containerId != null && containerId.length() != 0) {
                stopContainerQuietly(containerConfig.image(), containerId);
                if (name == null || config.isAlwaysRemoveContainer()) {
                    removeContainerQuietly(containerId);
                }
            }

            if (client != null) {
                client.close();
            }
        }
    }

    private void pullImage() throws DockerException, InterruptedException {
        // equivalent to "docker pull"
        final long startTime = System.currentTimeMillis();
        final String image = containerConfig.image();
        try {
            boolean found = false;
            try {
                client.inspectImage(image);
                LOGGER.info("Docker image already exists: {}", image);
                found = true;
            } catch (final ImageNotFoundException e) {
                found = false;
            }

            if (config.isAlwaysPullLatestImage() || !found) {
                LOGGER.info("Pulling docker image: {}", image);
                client.pull(image);
            }
        } catch (final DockerException e) {
            throw new DockerException("Unable to pull docker image " + image, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Docker image {} pulled in {}ms", image, elapsedMillis);
        }
    }

    /**
     * Register a shutdown hook with the JVM runtime, closing this context on JVM shutdown unless it
     * has already been closed at that time.
     * <p>
     * Delegates to {@code doClose()} for the actual closing procedure.
     *
     * @see Runtime#addShutdownHook
     * @see #close()
     * @see #doClose()
     */
    private void registerShutdownHook() {
        if (this.shutdownHook == null) {
            // No shutdown hook registered yet.
            this.shutdownHook = new Thread() {
                @Override
                public void run() {
                    synchronized (startStopMonitor) {
                        LOGGER.info("JVM shutting down");
                        Container.this.doStop();
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        }
    }

    private void removeContainerQuietly(final String idOrName) {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Removing docker container {} with id {}", containerConfig.image(), idOrName);
            client.removeContainer(idOrName, RemoveContainerParam.removeVolumes(true));
        } catch (DockerException | InterruptedException e) {
            // log error and ignore exception
            LOGGER.warn(
                    "Unable to remove docker container {} with id {}",
                    containerConfig.image(),
                    idOrName,
                    e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info(
                    "Container {} with id {} removed in {}ms",
                    containerConfig.image(),
                    idOrName,
                    elapsedMillis);
        }
    }

    /**
     * Instructs Docker to start the container
     *
     * @throws DockerException
     *             if docker cannot start the container
     */
    private void startContainer() throws DockerException {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Starting container {} with id {}", containerConfig.image(), containerId);
            client.startContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            throw new DockerException(
                    "Unable to start container " + containerConfig.image() + " with id " + containerId, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Container {} started in {}ms", containerConfig.image(), elapsedMillis);
        }
    }

    /**
     * Utility method to stop a container with the given image name and id/name.
     *
     * @param image
     *            image name
     * @param idOrName
     *            container id or name
     */
    private void stopContainerQuietly(final String image, final String idOrName) {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Killing docker container {} with id {}", image, idOrName);
            final int secondsToWaitBeforeKilling = 10;
            client.stopContainer(containerId, secondsToWaitBeforeKilling);
        } catch (DockerException | InterruptedException e) {
            // log error and ignore exception
            LOGGER.warn("Unable to kill docker container {} with id", image, idOrName, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Docker container {} with id {} killed in {}ms", image, idOrName, elapsedMillis);
        }
    }

    /**
     * Returns a new {@link ContainerConfig.Builder} based upon the given configuration.
     *
     * Descendant classes can override this method to customize the configuration of the Docker
     * container beyond what is allowed by {@link DockerConfig}.
     *
     * @param config
     *            docker container configuration
     * @return a new {@link ContainerConfig.Builder}
     */
    protected ContainerConfig.Builder createContainerConfig(final DockerConfig config) {
        final ContainerConfig.Builder builder = ContainerConfig.builder() //
                .hostConfig(createHostConfig(config).build()) //
                .image(config.getImage()) //
                .networkDisabled(false) //
                .exposedPorts(config.getPorts());
        for (final ContainerConfigurer configurer : config.getContainerConfigurer()) {
            configurer.configureContainer(builder);
        }
        return builder;
    }

    protected DockerClient createDockerClient() throws DockerClientException {
        try {
            return DefaultDockerClient.fromEnv() //
                    .connectTimeoutMillis(5000) //
                    .readTimeoutMillis(20000) //
                    .build();
        } catch (final IllegalStateException | IllegalArgumentException | DockerCertificateException e) {
            throw new DockerClientException("Unable to create docker client", e);
        }
    }

    /**
     * Returns a new {@link HostConfig.Builder} based upon the given configuration.
     *
     * Descendant classes can override this method to customize the configuration of the Docker
     * container's host beyond what is allowed by {@link DockerConfig}.
     *
     * @param config
     *            docker container configuration
     * @return a new {@link HostConfig.Builder}
     */
    protected HostConfig.Builder createHostConfig(final DockerConfig config) {
        final HostConfig.Builder builder = HostConfig.builder() //
                .portBindings(createPortBindings(config.getPorts()));
        for (final HostConfigurer configurer : config.getHostConfigurer()) {
            configurer.configureHost(builder);
        }
        return builder;
    }

    protected Map<String, List<PortBinding>> createPortBindings(final String[] exposedPorts) {
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
}
