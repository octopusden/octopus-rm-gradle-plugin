package org.octopusden.release.management.plugins.gradle

import org.octopusden.f1.automation.artifactory.artifactory
import com.platformlib.process.factory.ProcessBuilders
import com.platformlib.process.local.builder.LocalProcessBuilder
import com.platformlib.process.local.specification.LocalProcessSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import kotlin.IllegalStateException

class ReleaseManagementPluginTest {
    companion object {
        private val logger = LoggerFactory.getLogger(ReleaseManagementPluginTest::class.java)

        @JvmStatic
        fun projectAndArtifactsTestData(): Stream<Arguments> =  Stream.of(
                Arguments.of("single-module-gradle-4.10.3", listOf("single-module-gradle-4103")),
                Arguments.of("single-module", listOf("single-module")),
                Arguments.of("multi-module", listOf("module-1", "module-2")),
                Arguments.of("single-module-gradle-6.8.3", listOf("single-module-gradle-6.8.3")),
                Arguments.of("multi-module-4.10.3", listOf("module-3", "module-4")),
                Arguments.of("multi-module-with-root-publish-4.10.3", listOf("multi-module-with-root-publish-4.10.3", "module-5")),
                Arguments.of("legacy-staging-plugin", listOf("solution-mounter-dsl-core", "solution-mounter-dsl-file"))
        )

        @JvmStatic
        fun dependedComponentsRegistrationData(): Stream<Arguments> =  Stream.of(
                Arguments.of("multi-module", listOf("DBSM-Cloud-Common:0.1.67", "DBSM-Cloud-API:0.1.71")),
                Arguments.of("auto-registration", listOf("w4w3_doc:3.53.3-137"))
        )

        @JvmStatic
        fun versionSpecificationData(): Stream<Arguments> =  Stream.of(
                Arguments.of("1.0-SNAPSHOT", listOf("--no-daemon", "assemble")),
                Arguments.of("0.1", listOf("--no-daemon", "assemble", "-PbuildVersion=0.1"))
        )
    }

    @ParameterizedTest
    @MethodSource("projectAndArtifactsTestData")
    fun testPublishArtifacts(project: String, artifacts: Collection<String>) {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val buildVersion: String = System.getenv()["__BUILD_VERSION__"] ?: throw IllegalStateException("The __BUILD_VERSION__ environment variable is not set")
        val componentName = "org.octopusden.octopus-release-management.ft.$project"

        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/publish/$project")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val gradleCommandAndLineProperties = Properties()
        ReleaseManagementPluginTest::class.java.getResourceAsStream("/publish/teamcity-gradle-template-command.properties").use {
            gradleCommandAndLineProperties.load(it)
        }
        val gradleCommandAdnArguments = gradleCommandAndLineProperties.getProperty("command-and-arguments")
                .replace("__RELEASE_MANAGEMENT_VERSION__", releaseManagementVersion)
                .replace("__BUILD_VERSION__", buildVersion)
                .replace("__COMPONENT_NAME__", componentName)
                .replace("__PACKAGE_NAME__", System.getProperty("packageName"))
                .split(Regex("\\s+"))
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val processInstance = processBuilder
                .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
                .logger { it.logger(logger) }
                .mapBatExtension()
                .mapCmdExtension()
                .workDirectory(projectPath)
                .commandAndArguments("$projectPath/gradlew")
                .build()
                .execute(*gradleCommandAdnArguments.toTypedArray())
                .toCompletableFuture()
                .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")

        artifactory {
            url = System.getenv("ARTIFACTORY_URL")
                ?: throw throw IllegalStateException("The ARTIFACTORY_URL environment variable is not set")
            val buildInfo = getBuildInfo(componentName, buildVersion)
            assertNotNull(buildInfo, "Build $componentName:$buildVersion is not registered in artifactory")
            artifacts.forEach { artifact ->
                val module = buildInfo!!.modules.find { it.id == "org.octopusden.octopus-release-management.ft:$artifact:$buildVersion" }
                assertNotNull(module, "Module $artifact:$buildVersion wasn't found in build")
                val moduleArtifact = module!!.artifacts.find { it.name == "$artifact-${buildVersion}.jar" }
                assertNotNull(moduleArtifact, "Module artifact $artifact-${buildVersion}.jar hasn't been found in $buildInfo")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("dependedComponentsRegistrationData")
    fun testDependedComponentsRegistration(project: String, expectedComponents: Collection<String>) {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"]
                ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")

        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/teamcity-dependencies-registration/$project")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val gradleCommandAndLineProperties = Properties()
        ReleaseManagementPluginTest::class.java.getResourceAsStream("/teamcity-dependencies-registration/teamcity-gradle-template-command.properties").use {
            gradleCommandAndLineProperties.load(it)
        }
        val gradleCommandAdnArguments = gradleCommandAndLineProperties.getProperty("command-and-arguments")
                .replace("__RELEASE_MANAGEMENT_VERSION__", releaseManagementVersion)
                .replace("__PACKAGE_NAME__", System.getProperty("packageName"))
                .split(Regex("\\s+"))
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val stdout = ArrayList<String>()
        val processInstance = processBuilder
                .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
                .logger { it.logger(logger) }
                .mapBatExtension()
                .mapCmdExtension()
                .workDirectory(projectPath)
                .commandAndArguments("$projectPath/gradlew")
                .stdOutConsumer(stdout::add)
                .build()
                .execute(*gradleCommandAdnArguments.toTypedArray())
                .toCompletableFuture()
                .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        val teamcitySetParameterOutputLine = stdout.find { line -> line.startsWith("##teamcity[setParameter name='DEPENDENCIES' value='") }
        assertNotNull(teamcitySetParameterOutputLine)
        val dependencies = teamcitySetParameterOutputLine!!.split(Regex("value='"))[1].replace("']", "").split(Regex("\\s*,\\s*"))
        assertThat(dependencies).containsExactlyInAnyOrderElementsOf(expectedComponents)
    }

    @Test
    fun testDeclareDependencies() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/declare-pom-dependencies/single-module")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val packageName: String = System.getProperty("packageName")
        val processInstance = processBuilder
            .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
            .logger { it.logger(logger) }
            .mapBatExtension()
            .mapCmdExtension()
            .workDirectory(projectPath)
            .commandAndArguments("$projectPath/gradlew")
            .build()
            .execute("clean", "assemble", "generatePomFileForMavenPublication", "-Prelease-management.version=$releaseManagementVersion", "-PpackageName=$packageName")
            .toCompletableFuture()
            .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        val pomPath = projectPath.resolve("build/publications/maven/pom-default.xml")
        assertThat(pomPath).exists()
        val pomContext = String(Files.readAllBytes(pomPath))
        val prefix2Uri = mapOf("pom" to "http://maven.apache.org/POM/4.0.0")
        org.xmlunit.assertj.XmlAssert.assertThat(pomContext).withNamespaceContext(prefix2Uri).hasXPath("//pom:project/pom:dependencies/pom:dependency")
    }

    @ParameterizedTest
    @MethodSource("versionSpecificationData")
    fun versionSpecificationData(expectedVersion: String, commandLineArguments: Collection<String>) {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: "1.0-SNAPSHOT"
        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/version-specification")!!.toURI())
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val processInstance = processBuilder
                .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
                .logger { it.logger(logger) }
                .mapBatExtension()
                .mapCmdExtension()
                .workDirectory(projectPath)
                .commandAndArguments("$projectPath/gradlew", "-Prelease-management.version=$releaseManagementVersion")
                .processInstance { it.unlimited() }
                .build()
                .execute(*commandLineArguments.toTypedArray())
                .toCompletableFuture()
                .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        assertThat(processInstance.stdOut).contains("Project version is $expectedVersion", "Assemble version: $expectedVersion")
    }

    @Test
    @DisplayName("Test publish artifacts configured via publishConfigs")
    fun testPublishConfigs() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val buildVersion: String = System.getenv()["__BUILD_VERSION__"] ?: throw IllegalStateException("The __BUILD_VERSION__ environment variable is not set")
        val classifiers = arrayOf("lib", "bin")
        classifiers.forEach { classifier ->
            val componentName = "org.octopusden.octopus-release-management.ft.publishConfigs_$classifier"
            val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/publish/sqlprs")!!.toURI())
            logger.debug("Project directory {}", projectPath)
            val gradleCommandAndLineProperties = Properties()
            ReleaseManagementPluginTest::class.java.getResourceAsStream("/publish/teamcity-gradle-template-command.properties").use {
                gradleCommandAndLineProperties.load(it)
            }
            val gradleCommandAdnArguments = gradleCommandAndLineProperties.getProperty("command-and-arguments")
                .replace("__RELEASE_MANAGEMENT_VERSION__", releaseManagementVersion)
                .replace("__BUILD_VERSION__", buildVersion)
                .replace("__COMPONENT_NAME__", componentName)
                .replace("__PACKAGE_NAME__", System.getProperty("packageName"))
                .split(Regex("\\s+")) + arrayOf("-PCLASSIFIER=$classifier")
            val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
            val processInstance = processBuilder
                .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
                .logger { it.logger(logger) }
                .mapBatExtension()
                .mapCmdExtension()
                .workDirectory(projectPath)
                .commandAndArguments("$projectPath/gradlew")
                .build()
                .execute(*gradleCommandAdnArguments.toTypedArray())
                .toCompletableFuture()
                .get()
            assertEquals(0, processInstance.exitCode, "Gradle execution failure")
            artifactory {
                url = System.getenv("ARTIFACTORY_URL")
                    ?: throw throw IllegalStateException("The ARTIFACTORY_URL environment variable is not set")
                val buildInfo = getBuildInfo(componentName, buildVersion)
                assertNotNull(buildInfo, "Build $componentName:$buildVersion is not registered in artifactory")
                assertThat(buildInfo!!.modules).size().isEqualTo(1)
                assertThat(buildInfo.modules.first().artifacts.map { it.name }).containsExactly("sqlprs-${buildVersion}-${classifier}.txt")
            }
        }
    }
}
