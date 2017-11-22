/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor 
 * license agreements.  See the NOTICE file distributed with this work for additional 
 * information regarding copyright ownership.  The ASF licenses this file to you under 
 * the Apache License, Version 2.0 (the "License"); you may not  use this file except 
 * in compliance with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.PortBinding;

/**
 * Represents a connection to a Docker container.
 *
 * @author Greg Arakelian
 */
public class Container {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRule.class);

    /** Pattern for parsing port/protocol pattern **/
    private static final Pattern PORT_PROTOCOL_PATTERN = Pattern.compile("(\\d+)(\\/[a-z]+)?");

    public static boolean isSocketAlive(final SocketAddress socketAddress, final int timeout) {
        final Socket socket = new Socket();
        try {
            socket.connect(socketAddress, timeout);
            socket.close();
            return true;
        } catch (final SocketTimeoutException exception) {
            return false;
        } catch (final IOException exception) {
            return false;
        }
    }

    private final ContainerListener listener;
    private final ContainerConfig config;

    private final String name;

    /** True to delete container after test **/
    private final boolean alwaysRemoveContainer;

    /** Access to docker client API **/
    private DockerClient client;

    /** Docker container **/
    private ContainerInfo info;

    /** Container id **/
    private String containerId;

    /** Container host **/
    private String host;

    /** True if running inside a sibling container **/
    private final boolean jenkins;

    /** Reference to the JVM shutdown hook, if registered */
    private Thread shutdownHook;

    /** Synchronization monitor for the "refresh" and "destroy" */
    private final Object startStopMonitor = new Object();

    /** Flag that indicates whether the container has been started */
    private final AtomicBoolean started = new AtomicBoolean();

    /** Flag that indicates whether this container has been stopped already */
    private final AtomicBoolean stopped = new AtomicBoolean();

    /**
     * Construct a container.
     *
     * @param name
     *            name to assign to container, or null to use default
     * @param config
     *            the container configuration
     * @param alwaysRemoveContainer
     *            true if we should always remove container, e.g. "docker rm" after unit tests; by
     *            default, the docker container is left around so that it can be reused again.
     * @param listener
     *            listener for container events, may be null
     */
    public Container(final String name, final ContainerConfig config, final boolean alwaysRemoveContainer,
            final ContainerListener listener) {
        this.name = name;
        this.config = config;
        this.alwaysRemoveContainer = alwaysRemoveContainer;
        this.listener = listener;
        this.jenkins = System.getenv("JENKINS_HOME") != null;
    }

    private void assertStarted() {
        synchronized (this.startStopMonitor) {
            if (!started.get()) {
                throw new IllegalStateException("Docker container not started: " + config);
            }
        }
    }

    private String createContainer() throws InterruptedException {
        final long startTime = System.currentTimeMillis();

        // equivalent to "docker run"
        final ContainerCreation container;
        try {
            if (name == null) {
                container = client.createContainer(config);
            } else {
                container = client.createContainer(config, name);
            }
            return container.id();
        } catch (final DockerException e) {
            throw new IllegalStateException("Unable to create container using " + config, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Created container {} in {}ms", config.image(), elapsedMillis);
        }
    }

    protected DockerClient createDockerClient() {
        try {
            return DefaultDockerClient.fromEnv() //
                    .connectTimeoutMillis(5000) //
                    .readTimeoutMillis(20000) //
                    .build();
        } catch (final DockerCertificateException e) {
            throw new RuntimeException("Unable to create docker client", e);
        }
    }

    private void doStop() {
        if (this.started.get() && this.stopped.compareAndSet(false, true)) {
            this.started.set(false);

            if (containerId != null && containerId.length() != 0) {
                stopContainerQuietly(config.image(), containerId);
                if (name == null || alwaysRemoveContainer) {
                    removeContainerQuietly(containerId);
                }
            }

            if (client != null) {
                client.close();
            }
        }
    }

    public final DockerClient getClient() {
        return client;
    }

    public final String getContainerId() {
        assertStarted();
        return containerId;
    }

    public final String getHost() {
        assertStarted();
        return host;
    }

    public final ContainerInfo getInfo() {
        assertStarted();
        return info;
    }

    public final int getPort(final String portName) {
        assertStarted();

        if (jenkins) {
            // when running inside a Jenkins container, we are a sibling to the other
            // container,
            // and should access it via the exposed ip
            final Matcher matcher = PORT_PROTOCOL_PATTERN.matcher(portName);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } else {
            // otherwise, the container has exposed the service on a unique port running on
            // the host
            final Map<String, List<PortBinding>> ports = info.networkSettings().ports();
            final List<PortBinding> portBindings = ports.get(portName);
            if (portBindings != null && !portBindings.isEmpty()) {
                final PortBinding firstBinding = portBindings.get(0);
                return Integer.parseInt(firstBinding.hostPort());
            }
        }

        return -1;
    }

    private void pullImage() throws DockerException, InterruptedException {
        // equivalent to "docker pull"
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Pulling docker image {}", config.image());
            client.pull(config.image());
        } catch (final DockerException e) {
            throw new DockerException("Unable to pull docker image " + config.image(), e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Docker image {} pulled in {}ms", config.image(), elapsedMillis);
        }
    }

    /**
     * Register a shutdown hook with the JVM runtime, closing this context on JVM shutdown unless it has
     * already been closed at that time.
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
            LOGGER.info("Removing docker container {} with id {}", config.image(), idOrName);
            client.removeContainer(idOrName, RemoveContainerParam.removeVolumes(true));
        } catch (DockerException | InterruptedException e) {
            // log error and ignore exception
            LOGGER.warn("Unable to remove docker container {} with id {}", config.image(), idOrName, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Container {} with id {} removed in {}ms", config.image(), idOrName, elapsedMillis);
        }
    }

    void start() throws Exception, InterruptedException {
        synchronized (this.startStopMonitor) {
            if (this.started.get()) {
                return;
            }
            LOGGER.info("Starting container with {}", config);
            this.stopped.set(false);
            this.started.set(true);

            registerShutdownHook();
            client = createDockerClient();

            boolean created = false;
            boolean running = false;
            if (name != null) {
                try {
                    final ContainerInfo existing = client.inspectContainer(name);
                    final ContainerState state = existing.state();

                    if (!config.image().equals(existing.config().image())) {
                        // existing container has different image, e.g. elastic:2.3.4 vs
                        // elastic:5.1.1
                        if (state != null && state.running() && state.running().booleanValue()) {
                            stopContainerQuietly(config.image(), existing.id());
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

            if (jenkins) {
                host = info.networkSettings().ipAddress();
            } else {
                host = client.getHost();
            }

            // notify listener than container has been started
            if (listener != null) {
                listener.onStarted(this);
            }
        }
    }

    private void startContainer() throws DockerException {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Starting container {} with id {}", config.image(), containerId);
            client.startContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            throw new DockerException(
                    "Unable to start container " + config.image() + " with id " + containerId, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Container {} started in {}ms", config.image(), elapsedMillis);
        }
    }

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

    private void stopContainerQuietly(final String image, final String idOrName) {
        final long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("Killing docker container {} with id {}", image, idOrName);
            client.stopContainer(containerId, 10);
        } catch (DockerException | InterruptedException e) {
            // log error and ignore exception
            LOGGER.warn("Unable to kill docker container {} with id", image, idOrName, e);
        } finally {
            final long elapsedMillis = System.currentTimeMillis() - startTime;
            LOGGER.info("Docker container {} with id {} killed in {}ms", image, idOrName, elapsedMillis);
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
                LOGGER.info("Successfully received \"{}\" after {}ms", message,
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
     * @param port
     *            target port number
     */
    public final void waitForPort(final int port) {
        waitForPort(port, 30, TimeUnit.SECONDS);
    }

    /**
     * Wait for the specified port to accept socket connection within a given timeframe.
     *
     * @param port
     *            target port number
     * @param timeout
     *            timeout value
     * @param unit
     *            timeout units
     */
    public final void waitForPort(final int port, final int timeout, final TimeUnit unit) {
        final long startTime = System.currentTimeMillis();
        final long timeoutTime = startTime + TimeUnit.MILLISECONDS.convert(timeout, unit);

        LOGGER.info("Waiting for docker container at {}:{} for {} {}", host, port, timeout, unit);
        final SocketAddress address = new InetSocketAddress(host, port);
        for (;;) {
            if (isSocketAlive(address, 2000)) {
                LOGGER.info("Successfully connected to container at {}:{} after {}ms", host, port,
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
