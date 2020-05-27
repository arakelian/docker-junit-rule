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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arakelian.docker.junit.model.CreateContainerConfigurer;
import com.arakelian.docker.junit.model.DockerConfig;
import com.arakelian.docker.junit.model.HostConfigConfigurer;
import com.arakelian.docker.junit.model.StartedListener;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkHttpDockerCmdExecFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Handles life-cycle management of a Docker container, e.g. starting and stopping.
 *
 * @author Greg Arakelian
 */
public class Container {
    public static class ContainerLogger extends ResultCallback.Adapter<Frame> {
        private final List<String> capture = Lists.newArrayList();
        private final List<WaitState> waiting = Lists.newArrayList();

        private boolean complete;

        public boolean isComplete() {
            return complete;
        }

        @Override
        public void onComplete() {
            LOGGER.debug("Container logging terminated.");
            complete = true;
        }

        @Override
        public void onNext(final Frame frame) {
            // remove trailing whitespace
            final String log = StringUtils
                    .stripEnd(new String(frame.getPayload(), StandardCharsets.UTF_8), null);
            LOGGER.debug(log);

            synchronized (this) {
                capture.add(log);
            }
        }

        public void waitFor(final int timeout, final TimeUnit unit, final String... messages)
                throws InterruptedException {
            final WaitState wait = new WaitState(timeout, unit, messages);
            synchronized (this) {
                for (final String log : capture) {
                    wait.process(log);
                }
            }

            waiting.add(wait);
            try {
                while (!isComplete() && wait.isDone()) {
                    if (Thread.interrupted()) {
                        // manual check for thread being terminated
                        throw new InterruptedException();
                    }

                    Thread.sleep(1000);

                    if (wait.startTime > wait.timeoutTimeMillis) {
                        throw new IllegalStateException(
                                "Timeout waiting for log \"" + wait.getNextMessage() + "\"");
                    }
                }
            } finally {
                waiting.remove(wait);
            }
        }
    }

    @Value.Immutable
    public interface SimpleBinding {
        public static SimpleBinding of(final Binding binding) {
            return ImmutableSimpleBinding.builder() //
                    .host(binding.getHostIp()) //
                    .port(Integer.parseInt(binding.getHostPortSpec())) //
                    .build();
        }

        public String getHost();

        public int getPort();
    }

    private static class WaitState {
        private int n = 0;
        private final long startTime;
        private final long timeoutTimeMillis;
        private final String[] messages;

        public WaitState(final int timeout, final TimeUnit unit, final String[] messages) {
            Preconditions
                    .checkArgument(messages != null && messages.length != 0, "message must be non-empty");
            this.messages = messages;
            startTime = System.currentTimeMillis();
            timeoutTimeMillis = startTime + TimeUnit.MILLISECONDS.convert(timeout, unit);
            LOGGER.info("Tailing logs for \"{}\"", messages[0]);
        }

        public String getNextMessage() {
            return messages[n];
        }

        public boolean isDone() {
            return n == messages.length;
        }

        public void process(final String log) {
            if (isDone()) {
                return;
            }

            final String message = getNextMessage();
            if (log.contains(message)) {
                LOGGER.info(
                        "Successfully received \"{}\" after {}ms",
                        message,
                        System.currentTimeMillis() - startTime);
                if (++n < messages.length) {
                    LOGGER.info("Tailing logs for \"{}\"", getNextMessage());
                }
            }
        }
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

    /** Docker container configuration **/
    private final DockerConfig config;

    /** Access to docker client API **/
    private DockerClient client;

    /** Docker container information **/
    private InspectContainerResponse inspect;

    /** Container id **/
    private String containerId;

    /** Reference to the JVM shutdown hook, if registered */
    private Thread shutdownHook;

    /**
     * Arbitrary data that can be associated with this container.
     */
    private final Map<String, Object> context = Maps.newLinkedHashMap();

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

    private ContainerLogger containerLogger;

    /**
     * Construct a container.
     *
     * @param config
     *            docker container configuration
     */
    public Container(final DockerConfig config) {
        this.config = Preconditions.checkNotNull(config);
    }

    public final int addRef() {
        return refCount.incrementAndGet();
    }

    private void assertStarted() {
        synchronized (this.startStopMonitor) {
            if (!started.get()) {
                throw new IllegalStateException("Docker container not started: " + config.getImage());
            }
        }
    }

    private String createContainer() {
        final long startTime = System.currentTimeMillis();

        // equivalent to "docker run"
        try {
            final CreateContainerCmd command = client.createContainerCmd(config.getImage());

            final HostConfig hostConfig = new HostConfig();
            for (final HostConfigConfigurer configurer : config.getHostConfigConfigurer()) {
                configurer.configure(hostConfig);
            }

            command.withHostConfig(hostConfig);
            for (final CreateContainerConfigurer configurer : config.getCreateContainerConfigurer()) {
                configurer.configure(command);
            }

            final CreateContainerResponse response = command.exec();
            final String id = response.getId();
            return id;
        } catch (final DockerException e) {
            throw new IllegalStateException("Unable to create container " + config.getImage(), e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Created container {} in {}ms", config.getImage(), elapsedMillis);
        }
    }

    protected DockerClient createDockerClient() throws DockerClientException {
        try {
            final DockerClient dockerClient = DockerClientImpl //
                    .getInstance(config.getDockerClientConfig()) //
                    .withDockerCmdExecFactory(new OkHttpDockerCmdExecFactory());
            return dockerClient;
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new DockerClientException("Unable to create docker client", e);
        }
    }

    private void doStop() {
        if (this.started.get() && this.stopped.compareAndSet(false, true)) {
            LOGGER.info("Stopping image {}", config.getImage());
            this.started.set(false);

            if (containerId != null && containerId.length() != 0) {
                stopContainerQuietly(containerId);
                removeContainerQuietly(containerId);
            }

            if (client != null) {
                try {
                    client.close();
                } catch (final IOException e) {
                    // log exception and continue
                    LOGGER.warn("Unable to close Docker client", e);
                }
            }
        }
    }

    /**
     * Returns the host and port information for the given Docker port name.
     *
     * @param exposedPort
     *            exposed port
     * @return the host and port information
     * @throws IllegalStateException
     *             if the container has not been started
     * @throws IllegalArgumentException
     *             the given port name does not exist
     */
    public final Binding getBinding(final ExposedPort exposedPort)
            throws IllegalStateException, IllegalArgumentException {
        assertStarted();

        final NetworkSettings networkSettings = inspect.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Binding[] bindings = ports.getBindings().get(exposedPort);
        if (bindings == null || bindings.length == 0) {
            throw new IllegalArgumentException("Unknown port binding: " + exposedPort);
        }

        final Binding portBinding = bindings[0];
        return portBinding;
    }

    public final DockerClient getClient() {
        return client;
    }

    public DockerConfig getConfig() {
        return config;
    }

    public final String getContainerId() {
        assertStarted();
        return containerId;
    }

    public <T> T getData(final String name, final Class<T> clazz) {
        final Object value = context.get(name);
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        throw new IllegalStateException(name + " must be non-null");
    }

    public final InspectContainerResponse getInfo() {
        assertStarted();
        return inspect;
    }

    public final int getRefCount() {
        return refCount.get();
    }

    public final SimpleBinding getSimpleBinding(final ExposedPort exposedPort) {
        return SimpleBinding.of(getBinding(exposedPort));
    }

    /**
     * Returns true if container has been started.
     *
     * @return true if container has been started.
     */
    public boolean isStarted() {
        return started.get();
    }

    private void pullImage() throws DockerException, InterruptedException {
        final long startTime = System.currentTimeMillis();
        final String image = config.getImage();
        try {
            boolean found = false;
            try {
                final InspectImageResponse exec = client.inspectImageCmd(image).exec();
                if (exec != null) {
                    LOGGER.info("Docker image already exists: {}", exec);
                    found = true;
                }
            } catch (final NotFoundException e) {
                found = false;
            }

            if (config.isAlwaysPullLatestImage() || !found) {
                // equivalent to "docker pull"
                LOGGER.info("Pulling docker image: {}", image);
                client.pullImageCmd(image).start().awaitCompletion();
                final long elapsedMillis = System.currentTimeMillis() - startTime;
                LOGGER.info("Docker image {} pulled in {}ms", image, elapsedMillis);
            }
        } catch (final DockerException e) {
            throw new DockerException("Unable to pull docker image " + image, e.getHttpStatus(), e);
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

    public final int releaseRef() {
        return refCount.decrementAndGet();
    }

    private void removeContainerQuietly(final String id) {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Removing docker container {} with id {}", config.getImage(), id);
            client.removeContainerCmd(id).withRemoveVolumes(true).exec();
        } catch (final DockerException e) {
            // log error and ignore exception
            LOGGER.warn("Unable to remove docker container {} with id {}", config.getImage(), id, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Container {} with id {} removed in {}ms", config.getImage(), id, elapsedMillis);
        }
    }

    public void setData(final String name, final Object value) {
        context.put(name, value);
    }

    public void start() throws Exception {
        synchronized (this.startStopMonitor) {
            if (this.started.get()) {
                Preconditions.checkState(client != null, "client must be non-null");
                final InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
                final ContainerState state = inspect.getState();
                if (!state.getRunning().booleanValue()) {
                    throw new IllegalStateException(
                            "Container " + containerId + " is not running (check exit code!): " + state);
                }

                // already running
                return;
            }

            LOGGER.info("Starting image {}", config.getImage());
            this.stopped.set(false);
            this.started.set(true);

            registerShutdownHook();
            try {
                client = createDockerClient();
            } catch (final Exception e) {
                stop();
                throw e;
            }

            pullImage();
            containerId = createContainer();

            startContainer();

            inspect = client.inspectContainerCmd(containerId).exec();
            final String name = inspect.getName();
            LOGGER.info("Container {} has id {}", name, containerId);

            final Map<ExposedPort, Binding[]> portBindings = inspect //
                    .getNetworkSettings() //
                    .getPorts() //
                    .getBindings();

            for (final ExposedPort exposedPort : portBindings.keySet()) {
                final Binding[] bindings = portBindings.get(exposedPort);
                if (bindings != null && bindings.length != 0) {
                    for (final Binding binding : bindings) {
                        LOGGER.info(
                                "{} {} bound to host {} port {}",
                                name,
                                exposedPort,
                                binding.getHostIp(),
                                binding.getHostPortSpec());
                    }
                }
            }

            // make sure logger is going
            containerLogger = new ContainerLogger();
            client.logContainerCmd(containerId) //
                    .withFollowStream(true) //
                    .withStdOut(true) //
                    .withStdErr(true) //
                    .exec(containerLogger);

            // notify listener than container has been started
            for (final StartedListener listener : config.getStartedListener()) {
                listener.onStarted(this);
            }
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
            LOGGER.info("Starting container {} with id {}", config.getImage(), containerId);
            client.startContainerCmd(containerId).exec();
        } catch (final DockerException e) {
            throw new DockerException(
                    "Unable to start container " + config.getImage() + " with id " + containerId,
                    e.getHttpStatus(), e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info(
                    "Container {} started in {}ms (id:{})",
                    config.getImage(),
                    elapsedMillis,
                    containerId);
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
     * Utility method to stop a container with the given id or name.
     *
     * @param id
     *            container id or name
     */
    private void stopContainerQuietly(final String id) {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Killing docker container {} with id {}", config.getImage(), id);
            final int secondsToWaitBeforeKilling = 10;
            client.stopContainerCmd(containerId) //
                    .withTimeout(secondsToWaitBeforeKilling) //
                    .exec();
        } catch (final DockerException e) {
            // log error and ignore exception
            LOGGER.warn("Unable to kill docker container {} with id", config.getImage(), id, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info(
                    "Docker container {} with id {} killed in {}ms",
                    config.getImage(),
                    id,
                    elapsedMillis);
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
        containerLogger.waitFor(timeout, unit, messages);
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

    public final void waitForPort(final Binding binding) {
        final SimpleBinding simple = SimpleBinding.of(binding);
        waitForPort(simple.getHost(), simple.getPort());
    }

    public final void waitForPort(final Binding binding, final int timeout, final TimeUnit unit)
            throws IllegalArgumentException {
        final SimpleBinding simple = SimpleBinding.of(binding);
        waitForPort(simple.getHost(), simple.getPort(), timeout, unit);
    }

    /**
     * Wait for the specified port to accept socket connection.
     *
     * @param exposedPort
     *            protocol and port number
     */
    public final void waitForPort(final ExposedPort exposedPort) {
        final Binding binding = getBinding(exposedPort);
        waitForPort(binding);
    }

    /**
     * Wait for the specified port to accept socket connection within a given time frame.
     *
     * @param exposedPort
     *            protocol and port information, e.g. 9200/tcp
     * @param timeout
     *            timeout value
     * @param unit
     *            timeout units
     */
    public final void waitForPort(final ExposedPort exposedPort, final int timeout, final TimeUnit unit) {
        final Binding binding = getBinding(exposedPort);
        waitForPort(binding, timeout, unit);
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

        LOGGER.info("Waiting {} {} for connection to host {} port {}", timeout, unit, host, port);
        final SocketAddress address = new InetSocketAddress(host, port);
        for (;;) {
            if (isSocketAlive(address, 2000)) {
                LOGGER.info(
                        "Successfully connected to host {} port {} after {}ms",
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
}
