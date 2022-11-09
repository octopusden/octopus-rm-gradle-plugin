package org.octopusden.release.management.plugins.gradle

import org.octopusden.release.management.plugins.gradle.dto.ComponentDependency

class AutoUpdateDependenciesExtension {
    private List<ComponentDependency> components = []
    boolean autoMapping = false
    boolean pullRequest = false
    boolean createJiraIssue = false

    def component(Map componentMap) {
        components.add(new ComponentDependency(getComponentDefaultValues() + componentMap))
    }

    def component(Closure componentDependencyClosure) {
        def ComponentDependencyConfiguration = new ComponentDependencyConfiguration(getComponentDefaultValues())
        componentDependencyClosure.delegate = ComponentDependencyConfiguration
        componentDependencyClosure.call()
        components.add(ComponentDependencyConfiguration)
    }

    List<ComponentDependency> getComponents() {
        return components
    }

    private def getComponentDefaultValues() {
        ["pullRequest" : pullRequest, "createJiraIssue" : createJiraIssue]
    }

    static class ComponentDependencyConfiguration extends ComponentDependency {
        def name(componentName) {
            setName(componentName)
        }

        def projectProperty(projectProperty) {
            setProjectProperty(projectProperty)
        }

        def versionRange(String versionRange) {
            setVersionRange(versionRange)
        }

        def build(boolean includeBuild) {
            setIncludeBuild(true)
        }

        def rc(boolean includeBuild) {
            setIncludeRC(true)
        }

        def release(boolean includeBuild) {
            setIncludeRelease(true)
        }
    }
}
