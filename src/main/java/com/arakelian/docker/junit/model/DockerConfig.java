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

package com.arakelian.docker.junit.model;

import java.util.List;

import javax.annotation.Nullable;

import org.immutables.value.Value;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.collect.ImmutableList;

/**
 * Docker configuration.
 *
 * We use <a href="immutables.github.io">Immutables</a> to generate immutable Java beans, via Java
 * Annotation Processing.
 *
 * @author Greg Arakelian
 */
@Value.Immutable(copy = false)
public abstract class DockerConfig {
    @Override
    public boolean equals(@Nullable final Object another) {
        return this == another;
    }

    @Value.Default
    @Value.Auxiliary
    public List<CreateContainerConfigurer> getCreateContainerConfigurer() {
        return ImmutableList.of();
    }

    /**
     * Returns a key that can be used to share instances of this container between unit tests.
     *
     * @return a key that can be used to share instances of this container between unit tests.
     */
    @Value.Default
    @Value.Auxiliary
    public String getDockerRuleKey() {
        return getImage();
    }

    @Value.Default
    @Value.Auxiliary
    public List<HostConfigConfigurer> getHostConfigConfigurer() {
        return ImmutableList.of();
    }

    /**
     * Returns the name of the docker image.
     *
     * @return name of the docker image
     */
    public abstract String getImage();

    @Value.Default
    @Value.Auxiliary
    public DockerClientConfig getDockerClientConfig() {
        return DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    }

    /**
     * Returns a list of listeners which are called when the container is started.
     *
     * @return list of listeners which are called when the container is started
     */
    @Value.Default
    @Value.Auxiliary
    public List<StartedListener> getStartedListener() {
        return ImmutableList.of();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Returns true if container is allowed to continue running between separate JUnit tests. See
     * {@link #getDockerRuleKey()}.
     *
     * Note that all containers are automatically stopped when the JVM is exited (e.g. when Gradle
     * tests complete, or when JUnit tests complete inside Eclipse). This is a performance
     * enhancement that allows individual unit tests to complete much faster.
     *
     * @return true if container is allowed to continue running between separate JUnit tests
     */
    @Value.Default
    @Value.Auxiliary
    public boolean isAllowRunningBetweenUnitTests() {
        return true;
    }

    /**
     * Returns true if we should always pull the latest image, even if we already have a copy
     * locally.
     *
     * @return true if we should always pull the latest image
     */
    @Value.Default
    @Value.Auxiliary
    public boolean isAlwaysPullLatestImage() {
        return false;
    }
}
