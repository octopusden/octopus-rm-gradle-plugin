package org.octopusden.release.management.plugins.gradle

import org.octopusden.release.management.plugins.gradle.dto.VersionedComponent
import org.gradle.api.GradleException

import java.util.concurrent.ConcurrentHashMap


class ReleaseDependenciesConfiguration {
    private boolean autoRegistration = false

    private List<VersionedComponent> components = []
    private boolean fromDependencies = false
    private ReleaseDependenciesExtractingConfiguration extractingConfiguration = new ReleaseDependenciesExtractingConfiguration()
    private boolean touched

    private final Map<String, Object> properties = new ConcurrentHashMap<>()

    def fromDependencies() {
        fromDependencies = true
        touched = true
    }

    def fromDependencies(Closure fromDependenciesClosure) {
        fromDependencies()
        fromDependenciesClosure.delegate = extractingConfiguration
        fromDependenciesClosure()
        touched = true
    }

    def component(Map componentMap) {
        components.add(new VersionedComponent(componentMap))
        touched = true
    }

    def component(String componentDeclaration) {
        def items = componentDeclaration.split(":")
        if (items.size() != 2) {
            throw new GradleException("Incorrect component format for $componentDeclaration. Should be 'componentName:version'")
        }
        components.add(new VersionedComponent(id: items[0], version: items[1]))
        touched = true
    }

    List<VersionedComponent> getComponents() { return components }

    boolean isFromDependencies() {
        return fromDependencies
    }

    ReleaseDependenciesExtractingConfiguration getExtractingConfiguration() {
        return extractingConfiguration
    }

    boolean isTouched() {
        return this.touched || extractingConfiguration.isTouched()
    }

    void setProperty(String name, Object value) {
        properties.put(name, value)
        if (name == 'autoRegistration') {
            this.autoRegistration = value
        }
    }

    Object getProperty(String name) {
        return properties.get(name)
    }

    boolean getAutoRegistration() {
        return autoRegistration
    }

    void setAutoRegistration(boolean autoRegistration) {
        this.autoRegistration = autoRegistration
    }
}
