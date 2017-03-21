# docker-junit

A junit rule to run docker containers.

## Installation

The library is available on jcenter

### Maven

Add the following to your `pom.xml`:

```xml
<repositories>
    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>

...

<dependency>
    <groupId>com.arakelian</groupId>
    <artifactId>arakelian-docker-junit</artifactId>
    <version>1.1.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```groovy
repositories {
  jcenter()
}

dependencies {
  testCompile 'com.arakelian:arakelian-docker-junit:1.1.0'
}
```

## Licence

Apache Version 2.0 
