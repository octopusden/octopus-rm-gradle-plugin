package org.octopusden.release.management.plugins.gradle.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
    private final String errorMessage;

    @JsonCreator
    public ErrorResponse(@JsonProperty("errorMessage") String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
