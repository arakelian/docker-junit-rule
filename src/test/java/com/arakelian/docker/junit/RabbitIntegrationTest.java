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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.ClassRule;
import org.junit.Test;

import com.arakelian.docker.junit.Container;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Tests that we can launch RabbitMQ inside Docker container as part of JUnit test.
 *
 * @author Greg Arakelian
 */
public class RabbitIntegrationTest {
    @ClassRule
    public static RabbitDockerRule rabbitmq = new RabbitDockerRule();

    @Test
    public void testConnectsToDocker() throws ExecutionException, RetryException {
        final Retryer<Void> retryer = RetryerBuilder.<Void> newBuilder() //
                .retryIfException() //
                .withStopStrategy(StopStrategies.stopAfterDelay(1, TimeUnit.MINUTES)) //
                .withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS)) //
                .build();

        // wait for elastic
        retryer.call(() -> {
            final ConnectionFactory factory = new ConnectionFactory();
            final Container container = rabbitmq.getContainer();
            factory.setHost(container.getHost());
            factory.setPort(container.getPort("5672/tcp"));
            final Connection connection = factory.newConnection();
            connection.close();
            return null;
        });
    }
}
