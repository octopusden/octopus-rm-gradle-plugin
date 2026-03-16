package org.octopusden.release.management.plugins.gradle.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactComponentsDTO {
    private final Collection<ArtifactComponentDTO> artifactComponents;

    @JsonCreator
    public ArtifactComponentsDTO(@JsonProperty("artifactComponents") Collection<ArtifactComponentDTO> artifactComponents) {
        this.artifactComponents = artifactComponents;
    }

    public Collection<ArtifactComponentDTO> getArtifactComponents() {
        return artifactComponents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactComponentsDTO that = (ArtifactComponentsDTO) o;
        return artifactComponents != null ? artifactComponents.equals(that.artifactComponents) : that.artifactComponents == null;
    }

    @Override
    public int hashCode() {
        return artifactComponents != null ? artifactComponents.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ArtifactComponentsDTO{artifactComponents=" + artifactComponents + '}';
    }
}