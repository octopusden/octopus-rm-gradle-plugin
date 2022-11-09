package org.octopusden.release.management.plugins.gradle

import java.util.function.Supplier

class ContainerWrappedBuildExtension {
    String dockerImage
    Boolean useCurrentJava
    Supplier<Boolean> activateBy
    Collection<String> dockerOptions = new ArrayList<>()
}
