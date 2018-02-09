package com.arakelian.docker.junit;

public class DockerClientException extends RuntimeException {
    public DockerClientException(String message) {
        super(message);
    }

    public DockerClientException(Throwable cause) {
        super(cause);
    }

    public DockerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
