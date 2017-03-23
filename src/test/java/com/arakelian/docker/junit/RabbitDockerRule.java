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

import com.spotify.docker.client.messages.ContainerConfig.Builder;

/**
 * Test rule that starts Rabbit MQ.
 *
 * @author Greg Arakelian
 */
public class RabbitDockerRule extends DockerRule {
    public RabbitDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .name("docker-test") //
                .image("rabbitmq:management") //
                .ports("5672") //
                .build());
    }

    @Override
    protected void configureContainer(final Builder builder) {
    }

    @Override
    protected void configureHost(final com.spotify.docker.client.messages.HostConfig.Builder builder) {
    }

    @Override
    public void onStarted(final Container container) throws Exception {
        final int port = container.getPort("5672/tcp");
        container.waitForPort(port);
        container.waitForLog("Server startup complete");
    }
}
