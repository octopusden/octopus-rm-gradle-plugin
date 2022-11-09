package org.octopusden.release.management.plugins.gradle

import com.fasterxml.jackson.annotation.JsonProperty

data class ComponentDependency(@JsonProperty("name") val name: String,
                               @JsonProperty("project-property") val projectProperty: String,
                               @JsonProperty("version-range") val versionRange: String?,
                               @JsonProperty("pull-request") val pullRequest: Boolean?,
                               @JsonProperty("create-jira-issue") val createJiraIssue: Boolean?,
                               @JsonProperty("include-build") val includeBuild: Boolean?,
                               @JsonProperty("include-rc") val includeRc: Boolean?,
                               @JsonProperty("include-release") val includeRelease: Boolean)

data class AutoDependenciesConfiguration(@JsonProperty("declared") val declared: Map<String, String>,
                                         @JsonProperty("configured") val configured: Collection<ComponentDependency>)