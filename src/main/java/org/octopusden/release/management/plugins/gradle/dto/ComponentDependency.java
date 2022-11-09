package org.octopusden.release.management.plugins.gradle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ComponentDependency extends Component {
    @JsonProperty("project-property")
    private String projectProperty;

    @JsonProperty("version-range")
    private String versionRange;

    @JsonProperty("pull-request")
    private boolean pullRequest;

    @JsonProperty("create-jira-issue")
    private boolean createJiraIssue;

    @JsonProperty("include-build")
    private boolean includeBuild;

    @JsonProperty("include-rc")
    private boolean includeRC;

    @JsonProperty("include-release")
    private boolean includeRelease = true;

    public String getProjectProperty() {
        return projectProperty;
    }

    public void setProjectProperty(String projectProperty) {
        this.projectProperty = projectProperty;
    }

    public String getVersionRange() {
        return versionRange;
    }

    //TODO Validate given version range
    public void setVersionRange(String versionRange) {
        this.versionRange = versionRange;
    }

    public boolean isPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(boolean pullRequest) {
        this.pullRequest = pullRequest;
    }

    public boolean isCreateJiraIssue() {
        return createJiraIssue;
    }

    public void setCreateJiraIssue(boolean createJiraIssue) {
        this.createJiraIssue = createJiraIssue;
    }

    public boolean isIncludeBuild() {
        return includeBuild;
    }

    public void setIncludeBuild(boolean includeBuild) {
        this.includeBuild = includeBuild;
    }

    public boolean isIncludeRC() {
        return includeRC;
    }

    public void setIncludeRC(boolean includeRC) {
        this.includeRC = includeRC;
    }

    public boolean isIncludeRelease() {
        return includeRelease;
    }

    public void setIncludeRelease(boolean includeRelease) {
        this.includeRelease = includeRelease;
    }
}
