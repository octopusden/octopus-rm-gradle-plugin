# Gradle org.octopusden.octopus-release-management plugin

> **⚠️ Deprecated:** `org.octopusden.octopus-release-management` is deprecated and is not compatible with Gradle 9.
>
> Existing Gradle 8 projects may continue using this plugin. However, new projects and projects upgrading to Gradle 9 should use [`org.octopusden.octopus-platform`](https://github.com/octopusden/octopus-platform-gradle-plugin/blob/main/README.md) instead.

Plugin for integration continuous integration and release management in gradle projects.

Goal: Incapsulate all logic to work with Artifactory and Release Management.

How does it work:
1. Apply com.jfrog.artifactory plugin and configure it to work with Artifactory
2. Add tasks for backward compatibility (openStagingRepository, closeStagingRepository and etc)
3. Set project version && strategy of RM 2.0 dependency registration passed via command line options 'buildVersion' (String), 'includeAllDependencies' (Boolean)
4. Configure maven repositories for Escrow if needed
5. Add task and extension for Release Management (releaseManagement, autoUpdateDependencies)

Document publishing is a part of component build chain, module docs

## Build

## Test
The required parameters to test:\
*packageName* - The temporary parameter to be able to use package name which is set up externally
```shell
./gradlew test -PpackageName=octopusden
```

## Parameters

- <b>nexus</b> - The parameter to mark that it needs to publish two plugins or one(without it two plugins will be published) 
