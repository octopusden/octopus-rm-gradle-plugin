package org.octopusden.release.management.plugins.gradle

import org.octopusden.release.management.plugins.gradle.dto.Component
import org.octopusden.release.management.plugins.gradle.dto.Module
import org.gradle.api.GradleException

class ReleaseDependenciesExtractingConfiguration {

    private final Collection<Module> excludeModules = new ArrayList<>()
    private final Collection<Module> includeModules = new ArrayList<>()
    private final Collection<Component> excludeComponents = new ArrayList<>()
    private boolean touched

    def exclude(Map module) {
        def unknownKeys = module.keySet().findAll {key -> !(key in ["component", "group", "module"])}
        if (!unknownKeys.empty) {
            throw new GradleException("Unknown properties in exclude: " + unknownKeys)
        }

        if (module.containsKey("component")) {
            excludeComponents.add(new Component(module))
        }
        if (module.containsKey("group") || module.containsKey("module")) {
            excludeModules.add(new Module(module))
        }
        touched = true
    }

    def include(Map module) {
        includeModules.add(new Module(module))
        touched = true
    }

    Collection<Module> getExcludeModules() {
        return excludeModules
    }

    Collection<Module> getIncludeModules() {
        return includeModules
    }

    Collection<Component> getExcludeComponents() {
        return excludeComponents
    }

    boolean isTouched() {
        return touched
    }
}
