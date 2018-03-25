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

import com.google.common.collect.Lists;
import com.spotify.docker.client.messages.HostConfig.Ulimit;

/**
 * Utility class for creating <code>HostConfigurer</code>s.
 *
 * @author Greg Arakelian
 */
public class HostConfigurers {
    public static final HostConfigurer noUlimits() {
        return (builder) -> {
            final Ulimit ulimit = Ulimit.builder() //
                    .name("nofile") //
                    .soft(65536L) //
                    .hard(65536L) //
                    .build();
            builder.ulimits(Lists.newArrayList(ulimit));
        };
    }
}
