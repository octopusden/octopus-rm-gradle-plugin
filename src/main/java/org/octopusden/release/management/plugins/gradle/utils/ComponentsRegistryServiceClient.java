package org.octopusden.release.management.plugins.gradle.utils;

import feign.Headers;
import feign.RequestLine;
import org.octopusden.release.management.plugins.gradle.dto.ArtifactDependency;
import org.octopusden.release.management.plugins.gradle.dto.ArtifactComponentsDTO;

import java.util.Set;

public interface ComponentsRegistryServiceClient {

    @RequestLine("POST rest/api/3/components/find-by-artifacts")
    @Headers("Content-Type: application/json")
    ArtifactComponentsDTO findArtifactComponentsByArtifacts(Set<ArtifactDependency> artifacts);

    @RequestLine("GET rest/api/2/common/supported-groups")
    Set<String> getSupportedGroupIds();
}
