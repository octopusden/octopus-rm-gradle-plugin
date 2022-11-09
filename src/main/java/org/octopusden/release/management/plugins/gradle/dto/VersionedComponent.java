package org.octopusden.release.management.plugins.gradle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VersionedComponent extends Component {
    @JsonProperty
    private String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
