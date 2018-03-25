package com.arakelian.docker.junit;

public class DockerClientException extends RuntimeException {
    public DockerClientException(final String message) {
        super(message);
    }

    public DockerClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DockerClientException(final Throwable cause) {
        super(cause);
    }
}
