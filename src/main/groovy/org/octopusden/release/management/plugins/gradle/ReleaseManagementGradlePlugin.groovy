package org.octopusden.release.management.plugins.gradle

import org.octopusden.release.management.plugins.gradle.publish.MavenPomDependenciesUtility
import org.octopusden.release.management.plugins.gradle.tasks.AutoUpdateDependenciesDumpTask
import org.octopusden.release.management.plugins.gradle.tasks.ExportDependenciesToTeamcityTask
import com.platformlib.plugins.gradle.wrapper.task.DockerTask
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import util.ContainerUtilities

import java.nio.file.Files

class ReleaseManagementGradlePlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseManagementGradlePlugin.class)
    private static final String ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY = 'ARTIFACTORY_DEPLOYER_USERNAME'
    private static final String ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY = 'ARTIFACTORY_DEPLOYER_PASSWORD'
    private static final String ARTIFACTORY_DOCKER_USERNAME_PROPERTY = 'ARTIFACTORY_DOCKER_USERNAME'
    private static final String ARTIFACTORY_DOCKER_PASSWORD_PROPERTY = 'ARTIFACTORY_DOCKER_PASSWORD'
    private static final String ARTIFACTORY_PUBLISH_CONFIGS_PROPERTY = 'com.jfrog.artifactory.publishConfigs'
    private static final String PLUGIN_STATE_PROPERTY = "releaseManagementConfigurationState"
    private static final String ESCROW_PULL_IMAGES_PARAMETER_NAME = "escrow.build-phase"
    public static final String CYCLONE_DX_SKIP_PROPERTY = "cyclonedx.skip"

    @Override
    void apply(Project project) {

        LOGGER.info("Appling release management plugin to the project $project")

        if (project.getTasksByName("exportDependenciesToTeamcity", false).empty) {
            project.task("exportDependenciesToTeamcity", type: ExportDependenciesToTeamcityTask)
        }


        if (!project.extensions.findByName("releaseManagement")) {
            project.extensions.create("releaseManagement", ReleaseManagementDependenciesExtension)
        }


        if (project.rootProject.hasProperty(PLUGIN_STATE_PROPERTY)) {
            LOGGER.trace("The project $project has been already configured to use release management plugin")
            return
        }
        MavenPom.metaClass.declareDependencies = { configurations ->
            if (configurations instanceof Collection) {
                withXml(MavenPomDependenciesUtility.fromConfigurations(configurations))
            } else if (configurations instanceof Configuration) {
                withXml(MavenPomDependenciesUtility.fromConfiguration(configurations))
            }
        }
        //TODO Backward compatibility, remove it
        if (project.rootProject.extensions.findByName("nexusStaging") == null) {
            project.rootProject.extensions.create("nexusStaging", GradleStagingPluginExtension)
        }

        //TODO Remove deprecated tasks after migration all TeamCity build configurations to Artifactory
        project.rootProject.task("openStagingRepository") //Deprecated
        project.rootProject.task("useStagingRepository") //Deprecated
        project.rootProject.task("closeStagingRepository") //Deprecated
        project.rootProject.task("releaseStagingRepository") //Deprecated

        if (!project.rootProject.extensions.findByName("containerWrappedBuild")) {
            project.rootProject.extensions.create("containerWrappedBuild", ContainerWrappedBuildExtension)
        }

        if (!project.rootProject.extensions.findByName("autoUpdateDependencies")) {
            project.rootProject.extensions.create("autoUpdateDependencies", AutoUpdateDependenciesExtension)
            project.rootProject.task("dumpAutoUpdateDependencies", type: AutoUpdateDependenciesDumpTask)
        }

        project.rootProject.extensions.extraProperties.m2localPath = project.rootProject.hasProperty('m2_local') ? new File(project.rootProject['m2_local'] as String).toURI().toURL().toString().replaceAll(/^file:\//, 'file:///') : null
        project.rootProject.extensions.extraProperties.escrowBuild = project.rootProject.extensions.extraProperties.m2localPath != null

        if (GradleVersion.current() >= GradleVersion.version('6.0')) {
            setBuildVersion(project.rootProject)
            project.rootProject.subprojects { Project subProject ->
                setBuildVersion(subProject)
            }
        }

        project.rootProject.allprojects {Project subProject ->
            subProject.afterEvaluate {Project projectToConfigure ->
                def bootBuildImage = projectToConfigure.tasks.findByName("bootBuildImage")
                if (bootBuildImage?.class?.toString()?.contains("org.springframework.boot.gradle.tasks.bundling.BootBuildImage")) {
                    projectToConfigure.logger.lifecycle("Configure bootBuildImage task")
                    // Spring Boot plugin supports docker since 2.4.0
                    if (bootBuildImage.class.methods.any {method -> method.name == "docker" }) {
                        bootBuildImage.docker {
                            builderRegistry {
                                username = projectToConfigure.rootProject.findProperty(ARTIFACTORY_DOCKER_USERNAME_PROPERTY) ?: System.getenv().getOrDefault(ARTIFACTORY_DOCKER_USERNAME_PROPERTY, project.rootProject.findProperty('NEXUS_USER') as String)
                                password = projectToConfigure.rootProject.findProperty(ARTIFACTORY_DOCKER_PASSWORD_PROPERTY) ?: System.getenv().getOrDefault(ARTIFACTORY_DOCKER_PASSWORD_PROPERTY, project.rootProject.findProperty('NEXUS_PASSWORD') as String)
                                def dockerRegistry = System.getenv('DOCKER_REGISTRY') ?: project.properties['docker.registry']
                                if (dockerRegistry != null) {
                                    url = dockerRegistry
                                }
                            }
                        }
                    }
                }
            }
        }

        project.rootProject.afterEvaluate { Project rootProject ->
            setBuildVersion(rootProject)
            def containerTasks = rootProject.getAllTasks(true).values().flatten().findAll{ task -> task.hasProperty("dockerOptions")}
            if (!containerTasks.isEmpty() && ContainerUtilities.isPodmanSupported()) {
                containerTasks.forEach {containerTask ->
                    containerTask.containerCommand = "podman"
                    containerTask.dockerOptions.add("--userns=keep-id")
                }
            }

            ContainerWrappedBuildExtension containerWrappedBuildExtension = rootProject.getExtensions().findByType(ContainerWrappedBuildExtension.class)
            if (containerWrappedBuildExtension.activateBy?.get()) {
                LOGGER.info("Configure platformGradleWrapper extension")
                def dockerEnvFilePath = Files.createTempFile('docker-container-env-', '.tmp')
                //TODO Get artifactory credentials via method
                dockerEnvFilePath.toFile().text = ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY + "=" + (project.rootProject.findProperty(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY) ?: System.getProperty(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY, System.getenv(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY))) + "\n" + ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY + "=" + (project.rootProject.findProperty(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY) ?: System.getProperty(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY, System.getenv(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY)))
                project.rootProject.platformGradleWrapper {
                        docker {
                            octopus {
                                image = containerWrappedBuildExtension.dockerImage
                                bindLocalM2Repository()
                                mapProjectDir()
                                if (containerWrappedBuildExtension.useCurrentJava) {
                                    useCurrentJava = containerWrappedBuildExtension.useCurrentJava
                                }
                                dockerOptions.addAll(containerWrappedBuildExtension.dockerOptions + ["--env-file", dockerEnvFilePath.toString()])
                                activateBy = { true }
                                usePodman = System.getenv().getOrDefault("OS_TYPE", "NO").matches("(RHEL8.*)|(OL8.*)")
                            }
                        }
                }
            }
            //To validate auto-update dependencies configuration
            (rootProject.tasks.findByPath("dumpAutoUpdateDependencies") as AutoUpdateDependenciesDumpTask).getAutoUpdateDependenciesConfiguration()

            rootProject.allprojects { Project projectToConfigure ->
                projectToConfigure.version = rootProject.version
                if (rootProject.extensions.extraProperties.escrowBuild) {
                    projectToConfigure.buildscript.repositories.clear()
                    projectToConfigure.buildscript.repositories {
                        maven {
                            url rootProject.extensions.extraProperties.m2localPath
                        }
                    }
                    projectToConfigure.repositories.clear()
                    projectToConfigure.repositories {
                        maven {
                            url rootProject.extensions.extraProperties.m2localPath
                        }
                    }
                }
            }

            def exportDependenciesToTeamcitySpecified = project.gradle.startParameter.taskNames.any { it.endsWith("exportDependenciesToTeamcity") }

            if (!exportDependenciesToTeamcitySpecified && !rootProject.gradle.startParameter.offline && !project.rootProject.extensions.extraProperties.escrowBuild) {
                def releaseManagementDependenciesExtension = project.extensions.getByType(ReleaseManagementDependenciesExtension.class)
                if (releaseManagementDependenciesExtension.releaseDependenciesConfiguration.isTouched() || rootProject.findProperty("includeAllDependencies")?.toString()?.equalsIgnoreCase("true")) {
                    if (releaseManagementDependenciesExtension.releaseDependenciesConfiguration.autoRegistration || rootProject.hasProperty("buildVersion")) {
                        def exportDependenciesToTeamcityTask = project.getTasksByName("exportDependenciesToTeamcity", false)[0] as ExportDependenciesToTeamcityTask
                        rootProject.gradle.buildFinished { BuildResult buildResult ->
                            if (buildResult.failure == null) {
                                exportDependenciesToTeamcityTask.exportDependencies()
                            } else {
                                LOGGER.debug("Skip executing the exportDependenciesToTeamcity task because of build failure")
                            }
                        }
                    } else {
                        LOGGER.debug("The exportDependenciesToTeamcity task autorun is not enabled")
                    }
                } else {
                    LOGGER.debug("The release management extension is not configured, skip exporting dependencies to TeamCity")
                }
            }
        }

        def baseUrl = System.getenv('ARTIFACTORY_URL') ?: project.properties.get('artifactoryUrl')
        if (baseUrl == null) {
            LOGGER.info("Artifactory URL is not provided, configuration com.jfrog.artifactory is skipped")
        }

        if (!project.rootProject.extensions.extraProperties.escrowBuild && baseUrl != null) {
            project.rootProject.pluginManager.apply('com.jfrog.artifactory')
            def repositoryKey = ("true" == project.rootProject.findProperty("publishToReleaseRepository") ?: System.getProperty("publishToReleaseRepository", System.getenv("publishToReleaseRepository"))) ? 'rnd-maven-release-local' : 'rnd-maven-dev-local'
            LOGGER.debug("Deploy to {} repository", repositoryKey)
            final String jfrogPublishConfigs = project.rootProject.findProperty(ARTIFACTORY_PUBLISH_CONFIGS_PROPERTY) as String
            //TODO Get artifactory credentials via method
            project.rootProject.artifactory {
                publish {
                    contextUrl = "${baseUrl}/artifactory" as String
                    repository {
                        repoKey = repositoryKey
                        username = project.rootProject.findProperty(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY) ?: System.getProperty(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY, System.getenv(ARTIFACTORY_DEPLOYER_USERNAME_PROPERTY))
                        password = project.rootProject.findProperty(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY) ?: System.getProperty(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY, System.getenv(ARTIFACTORY_DEPLOYER_PASSWORD_PROPERTY))
                        maven = true
                    }
                    defaults {
                        if (jfrogPublishConfigs == null) {
                            publications('ALL_PUBLICATIONS')
                            publishPom = true
                        } else {
                            publishConfigs(jfrogPublishConfigs)
                            publishPom = false
                        }
                        publishArtifacts = true
                        publishBuildInfo = true
                    }
                }
            }
            project.rootProject.afterEvaluate { configureProjectPublish(project.rootProject) }

            project.rootProject.subprojects { Project subProject ->
                subProject.pluginManager.apply('com.jfrog.artifactory')
                subProject.afterEvaluate { configureProjectPublish(subProject) }
            }
        }

        if (project.getRootProject().getGradle().getStartParameter().isDryRun() && "ASSEMBLE" == project.getRootProject().findProperty(ESCROW_PULL_IMAGES_PARAMETER_NAME)) {
            LOGGER.debug("Configure to pull image for docker tasks")
            project.getRootProject().getGradle().buildFinished { buildResult ->
                def pulledImages = new HashSet<>()
                project.getAllTasks(true).values().flatten().findAll {task -> task instanceof DockerTask}.forEach { task ->
                    final DockerTask dockerTask = (DockerTask) task
                    if (pulledImages.add(dockerTask.getImage())) {
                        dockerTask.pullImage()
                    }
                }
            }
        }

        if (project.rootProject.hasProperty(CYCLONE_DX_SKIP_PROPERTY) && !Boolean.parseBoolean(project.rootProject.property(CYCLONE_DX_SKIP_PROPERTY).toString())) {
            project.allprojects { Project subProject ->
                subProject.afterEvaluate {
                    subProject.tasks.findByPath("assemble")?.dependsOn(":cyclonedxBom")
                }
            }

            project.rootProject.pluginManager.apply("org.cyclonedx.bom")
            project.rootProject.cyclonedxBom {
                schemaVersion = "1.4"
                destination = new File("build/generated-resources/sbom")
                outputFormat = "json"
                includeConfigs = ["runtimeClasspath"]
                projectType = "application"
            }
        }

        project.pluginManager.apply("com.platformlib.gradle-wrapper")
        project.rootProject.extensions[PLUGIN_STATE_PROPERTY] = "applied"
    }

    private void configureProjectPublish(final Project project) {
        if (project.rootProject.extensions.extraProperties.escrowBuild) {
            if (project.pluginManager.findPlugin('maven-publish')) {
                project.plugins.withType(MavenPublishPlugin.class) {
                    if (project.publishing.repositories.empty) {
                        project.publishing.repositories {
                            maven {
                            }
                        }
                    }
                }
                LOGGER.debug("Configure ESCROW repository for {} publish tasks", project)
                project.tasks.withType(PublishToMavenRepository.class).forEach {
                    it.repository.url = project.rootProject.extensions.extraProperties.m2localPath
                }
            }
        } else if (!project.rootProject.gradle.startParameter.offline && project.pluginManager.findPlugin('com.jfrog.artifactory')) {
            if (project == project.rootProject && !project.rootProject.pluginManager.findPlugin('maven-publish')) {
                project.rootProject.pluginManager.apply('maven-publish')
            }
            if (project.pluginManager.findPlugin('maven-publish')) {
                project.tasks.findByPath("publish")?.dependsOn(project.tasks.findByPath("artifactoryPublish"))
                project.tasks.withType(PublishToMavenRepository.class)?.forEach {
                    it.enabled = false
                }
            } else {
                project.tasks.findByPath("artifactoryPublish").skip = true
            }
        }
    }

    private static void setBuildVersion(final Project project) {
        if (project.rootProject.hasProperty("buildVersion")) {
            final String buildVersion = project.rootProject.properties['buildVersion']
            if (project.version != buildVersion) {
                project.version = buildVersion
                LOGGER.debug("Set project version to {}", buildVersion)
                return
            }
        }
        if ('unspecified' == project.version) {
            project.version = '1.0-SNAPSHOT'
            LOGGER.debug("The project version is not specified, use {}", project.version)
        }
    }
}
