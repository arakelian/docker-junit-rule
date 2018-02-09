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

public class WireMockDockerRule extends DockerRule {
    private static final String HTTP_PORT = "8080/tcp";
    private static final String HTTPS_PORT = "8081/tcp";

    public WireMockDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .name("docker-test-wiremock") //
                .image("rodolpheche/wiremock:2.14.0-alpine") //
                .ports(HTTP_PORT, HTTPS_PORT) //
                .addContainerConfigurer(
                        c -> c.cmd("-verbose", "--print-all-network-traffic", "--https-port=8081")) //
                .alwaysRemoveContainer(true) //
                .addStartedListener(container -> {
                    container.waitForPort(HTTP_PORT);
                    container.waitForPort(HTTPS_PORT);
                }).build());
    }
}
