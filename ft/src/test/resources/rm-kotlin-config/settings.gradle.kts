pluginManagement {
    plugins {
        id("org.octopusden.octopus-release-management") version (extra["octopus-release-management.version"] as String)
    }
}

rootProject.name = "rm-kotlin-config"
