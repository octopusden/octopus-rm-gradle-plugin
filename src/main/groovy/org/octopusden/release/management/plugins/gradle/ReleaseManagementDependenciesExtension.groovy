package org.octopusden.release.management.plugins.gradle

class ReleaseManagementDependenciesExtension {
    private def releaseDependenciesConfiguration = new ReleaseDependenciesConfiguration()

    def releaseDependencies(final Closure releaseDependenciesClosure) {
        releaseDependenciesClosure.delegate = releaseDependenciesConfiguration
        releaseDependenciesClosure.call()
    }

    def getReleaseDependenciesConfiguration() {
        return releaseDependenciesConfiguration
    }
}
