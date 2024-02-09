package org.octopusden.release.management.plugins.gradle

class ReleaseManagementDependenciesExtension {
    private def releaseDependenciesConfiguration = new ReleaseDependenciesConfiguration()

    def releaseDependencies(final Closure releaseDependenciesClosure) {
        releaseDependenciesClosure.delegate = releaseDependenciesConfiguration
        releaseDependenciesClosure.call()
    }

    def releaseDependencies(Map... components) {
        components.each {
            releaseDependenciesConfiguration.component(it)
        }
    }

    def releaseDependencies(String... components) {
        components.each {
            releaseDependenciesConfiguration.component(it)
        }
    }

    def getReleaseDependenciesConfiguration() {
        return releaseDependenciesConfiguration
    }
}
