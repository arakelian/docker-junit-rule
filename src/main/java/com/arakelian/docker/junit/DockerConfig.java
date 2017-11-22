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

import org.immutables.value.Value;

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
    public String getImage();

    public String getName();

    public String[] getPorts();

    @Value.Default
    public default boolean isAlwaysRemoveContainer() {
        return false;
    }
}
