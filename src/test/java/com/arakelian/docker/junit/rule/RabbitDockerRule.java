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

package com.arakelian.docker.junit.rule;

import com.arakelian.docker.junit.DockerRule;
import com.arakelian.docker.junit.model.ImmutableDockerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports.Binding;

/**
 * Test rule that starts Rabbit MQ and waits until unit test can connect to it.
 *
 * @author Greg Arakelian
 */
public class RabbitDockerRule extends DockerRule {
    public static final ExposedPort RABBITMQ_PORT = ExposedPort.tcp(5672);

    public RabbitDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .image("rabbitmq:management") //
                .addCreateContainerConfigurer(create -> {
                    create.withExposedPorts(RABBITMQ_PORT);
                }) //
                .addHostConfigConfigurer(hostConfig -> {
                    hostConfig.withAutoRemove(true);
                    hostConfig.withPortBindings(new PortBinding(Binding.empty(), RABBITMQ_PORT));
                }) //
                .addStartedListener(container -> {
                    container.waitForPort(RABBITMQ_PORT);
                    container.waitForLog("Server startup complete");
                }).build());
    }
}
