plugins {
    id("org.octopusden.octopus-release-management")
}

releaseManagement {
    releaseDependencies(
        mapOf("name" to "deployer", "version" to project.properties["deployer.version"]),
        mapOf("name" to "deployerDSL", "version" to project.properties["deployer.dsl.version"]),
    )
}

tasks.create<DefaultTask>("run") {
    doLast {
        println("Tht [${project.name}] running.")
    }
}
defaultTasks("run")