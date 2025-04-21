plugins {
    id("org.octopusden.octopus-release-management")
}

releaseManagement {
    releaseDependencies(
        mapOf("name" to "ReleaseManagementService", "version" to project.properties["ReleaseManagementService.version"]),
        mapOf("name" to "ReleaseManagementService", "version" to project.properties["ReleaseManagementService.not.exist.version"]),
    )
}

tasks.create<DefaultTask>("run") {
    doLast {
        println("Tht [${project.name}] running.")
    }
}
defaultTasks("run")