# Gradle org.octopusden.octopus-release-management plugin

Plugin for integration continuous integration and release management in gradle projects.

Goal: Incapsulate all logic to work with Artifactory and Release Management.

How does it work:
1. Apply com.jfrog.artifactory plugin and configure it to work with Artifactory
2. Add tasks for backward compatibility (openStagingRepository, closeStagingRepository and etc)
3. Set project version passed via command line option buildVersion
4. Apply plugin com.platformlib.gradle-wrapper and configure it (to support build in docker)
5. Configure maven repositories for Escrow if needed
6. Add task and extension for Release Management (releaseManagement, autoUpdateDependencies)

Document publishing is a part of component build chain, module docs

## Build
The required parameters to build:\
*supportedGroupIds* - A list of parameters to filter dependencies by group
```shell
./gradlew build -PsupportedGroupIds=org.octopusden
```

## Test
The required parameters to test:\
*packageName* - The temporary parameter to be able to use package name which is set up externally
```shell
./gradlew test -PpackageName=org.octopusden
```

## Parameters

- <b>nexus</b> - The parameter to mark that it needs to publish two plugins or one(without it two plugins will be published) 
