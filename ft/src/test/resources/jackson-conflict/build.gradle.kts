import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

buildscript {
    dependencies {
        classpath(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
        classpath("com.fasterxml.jackson.core:jackson-databind")
        classpath("com.fasterxml.jackson.core:jackson-core")
        classpath("com.fasterxml.jackson.core:jackson-annotations")
    }
}

plugins {
    id("org.octopusden.octopus-release-management")
    id("org.jetbrains.kotlin.jvm") version (project.properties["kotlin.version"] as String)
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}

tasks.create<DefaultTask>("run") {
    doLast {
        println("That [${project.name}] running.")
    }
}
defaultTasks("run")
