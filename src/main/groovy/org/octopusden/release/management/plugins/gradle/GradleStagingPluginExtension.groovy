package org.octopusden.release.management.plugins.gradle

import org.gradle.internal.impldep.org.eclipse.jgit.errors.NotSupportedException

/**
 * Uses only for backward compatibility with publishing in nexus repository
 */
@Deprecated
class GradleStagingPluginExtension {

    String profileId

    /* Should not be modified outside NexusStagingPlugin */
    String repositoryId

    String getRepositoryURI() {
        throw new NotSupportedException("NotSupported")
    }
}
