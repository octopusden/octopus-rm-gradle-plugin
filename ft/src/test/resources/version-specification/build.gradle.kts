plugins {
    id("org.octopusden.release-management")
}

group = "org.octopusden.f1-base-services.service-registry"

tasks.register("assemble") {
    doLast {
        System.out.println("Assemble version: ${project.version}")
    }
}

System.out.println("Project version is $version")
 