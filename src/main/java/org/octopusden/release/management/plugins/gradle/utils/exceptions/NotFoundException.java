package org.octopusden.release.management.plugins.gradle.utils.exceptions;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
