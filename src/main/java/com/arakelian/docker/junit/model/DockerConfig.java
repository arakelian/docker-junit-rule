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

import org.immutables.value.Value;

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
public interface DockerConfig {
    @Value.Default
    @Value.Auxiliary
    public default List<ContainerConfigurer> getContainerConfigurer() {
        return ImmutableList.of();
    }

    @Value.Default
    @Value.Auxiliary
    public default List<HostConfigurer> getHostConfigurer() {
        return ImmutableList.of();
    }

    /**
     * Returns the name of the docker image.
     *
     * @return name of the docker image
     */
    @Value.Auxiliary
    public String getImage();

    /**
     * Returns the container name (as would be displayed in "NAMES" column of "docker ps" command).
     *
     * @return the container name.
     */
    public String getName();

    /**
     * Returns the list of ports that this container will make public.
     *
     * @return list of ports that this container will make public.
     */
    @Value.Auxiliary
    public String[] getPorts();

    @Value.Default
    @Value.Auxiliary
    public default List<StartedListener> getStartedListener() {
        return ImmutableList.of();
    }

    @Value.Default
    @Value.Auxiliary
    public default boolean isAllowRunningBetweenUnitTests() {
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
    public default boolean isAlwaysPullLatestImage() {
        return false;
    }

    @Value.Default
    @Value.Auxiliary
    public default boolean isAlwaysRemoveContainer() {
        return false;
    }
}