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

public class WireMockDockerRule extends DockerRule {
    private static final ExposedPort HTTP_PORT = ExposedPort.tcp(8080);
    private static final ExposedPort HTTPS_PORT = ExposedPort.tcp(8081);

    public WireMockDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .image("rodolpheche/wiremock:2.14.0-alpine") //
                .addCreateContainerConfigurer(create -> {
                    create.withExposedPorts(HTTP_PORT, ExposedPort.tcp(8081));
                    create.withCmd("-verbose", "--print-all-network-traffic", "--https-port=8081");
                }) //
                .addHostConfigConfigurer(hostConfig -> {
                    hostConfig.withAutoRemove(true);
                    hostConfig.withPortBindings(
                            new PortBinding(Binding.empty(), HTTP_PORT),
                            new PortBinding(Binding.empty(), HTTPS_PORT));
                }) //
                .addStartedListener(container -> {
                    container.waitForPort(HTTP_PORT);
                    container.waitForPort(HTTPS_PORT);
                }).build());
    }
}
