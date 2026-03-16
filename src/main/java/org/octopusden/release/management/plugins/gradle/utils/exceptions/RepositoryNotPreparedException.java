package org.octopusden.release.management.plugins.gradle.utils.exceptions;

public class RepositoryNotPreparedException extends RuntimeException {

    public RepositoryNotPreparedException(String message) {
        super(message);
    }
}
