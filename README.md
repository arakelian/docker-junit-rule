# docker-junit-rule

A junit rule to run docker containers.

## Features

* `DockerRule` will automatically start and stop your Docker container.
* `DockerRule` will share your container across multiple JUnit tests; it will not start and stop your container
  for each test, allowing your test suites to run much faster.

## Requirements

* Version 2.x is compatible with Java 8+
* Version 3.x is compatible with Java 11+

## Usage

Starting a Docker container as part of your unit test is as simple as including a `ClassRule` that looks like this:

```java
@ClassRule
public static RabbitMqDockerRule rabbitmq = new RabbitMqDockerRule();
```

To configure your own rule, you'll extend from `DockerRule`:

```java
public class RabbitDockerRule extends DockerRule {
    public RabbitDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .name("docker-test") //
                .image("rabbitmq:management") //
                .ports("5672") //
                .addStartedListener(container -> {
                    container.waitForPort("5672/tcp");
                    container.waitForLog("Server startup complete");
                }).build());
    }
}
```

A couple of things to note:
* Your custom `ClassRule` will extend from `DockerRule`
* Your constructor will provide `DockerRule` with an immutable configuration that describes the Docker container
  that needs to be created.
* The configuration you provide includes a user-defined callback for determining when the container has started. A few helper methods are
  provided to help you do this.


## Customizing Docker Container

DockerRule provides two callback for customizing the Docker container, `addHostConfigurer` and `addContainerConfigurer`.  In the
example below we'll use these callback to configure an Elasticsearch container.

```java
public class ElasticDockerRule extends DockerRule {
    public ElasticDockerRule() {
        super(ImmutableDockerConfig.builder() //
                .name(name) //
                .image(image) //
                .ports("9200") //
                .addHostConfigurer(HostConfigurers.noUlimits()) //
                .addContainerConfigurer(builder -> {
                    builder.env(
                            "http.host=0.0.0.0", //
                            "transport.host=127.0.0.1", //
                            "xpack.security.enabled=false", //
                            "xpack.monitoring.enabled=false", //
                            "xpack.graph.enabled=false", //
                            "xpack.watcher.enabled=false", //
                            "ES_JAVA_OPTS=-Xms512m -Xmx512m");
                }) //
                .build());
    }
}
```

Notice that we used `addHostConfigurer` to remove ulimits and `addContainerConfigurer` to set environment variables.


## Installation

The library is available on [Maven Central](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.arakelian%22%20AND%20a%3A%22docker-junit-rule%22).

### Maven

Add the following to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>central</id>
        <name>Central Repository</name>
        <url>http://repo.maven.apache.org/maven2</url>
        <releases>
            <enabled>true</enabled>
        </releases>
    </repository>
</repositories>

...

<dependency>
    <groupId>com.arakelian</groupId>
    <artifactId>docker-junit-rule</artifactId>
    <version>3.2.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```groovy
repositories {
  mavenCentral()
}

dependencies {
  testCompile 'com.arakelian:docker-junit-rule:3.2.0'
}
```

## Licence

Apache Version 2.0
