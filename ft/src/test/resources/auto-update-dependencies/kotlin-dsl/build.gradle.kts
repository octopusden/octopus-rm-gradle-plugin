//
plugins {
    id("base")
    id("org.octopusden.octopus-release-management")
}

group = "org.octopusden.uds"

autoUpdateDependencies {
    pullRequest = false
    component(mapOf("name" to "platform-commons", "projectProperty" to "platform-commons.version"))
}

tasks {
    dumpAutoUpdateDependencies {
        outputFile = file("$buildDir/auto-update-dependencies-configuration.json")
    }
}

