package org.octopusden.release.management.plugins.gradle

import org.octopusden.f1.automation.artifactory.artifactory
import com.platformlib.process.factory.ProcessBuilders
import com.platformlib.process.local.builder.LocalProcessBuilder
import com.platformlib.process.local.specification.LocalProcessSpec
import org.apache.http.entity.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import kotlin.IllegalStateException
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import kotlin.collections.ArrayList
import org.mockserver.junit.jupiter.MockServerExtension
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.integration.ClientAndServer;


@ExtendWith(MockServerExtension::class)
@MockServerSettings(ports = intArrayOf(1080))
class ReleaseManagementPluginTest(val mockRmServer: ClientAndServer) {
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
                Arguments.of("legacy-staging-plugin", listOf("deployer-dsl-core", "deployer-dsl-file")),
                Arguments.of("multi-module-regression", listOf("rmtest-log", "rmtest-core"))
        )

        @JvmStatic
        fun dependedComponentsRegistrationData(): Stream<Arguments> =  Stream.of(
            Arguments.of("multi-module", "teamcity-gradle-template-command.properties", listOf("DBSM-Cloud-Common:0.1.67", "DBSM-Cloud-API:0.1.71")),
            Arguments.of("multi-module", "teamcity-gradle-template-command-include-all-deps.properties", listOf("DBSM-Cloud-Common:0.1.67", "DBSM-Cloud-API:0.1.71", "components-registry-service:0.0.645")),
            Arguments.of("auto-registration", "teamcity-gradle-template-command.properties", listOf("web_portal3_doc:3.53.3-137","web_portal3_doc_ch:3.53.3-137")),
            Arguments.of("auto-registration", "teamcity-gradle-template-command-include-all-deps.properties", listOf("web_portal3_doc:3.53.3-137", "web_portal3_doc_ch:3.53.3-137", "DBSM-Cloud-API:0.1.71")),
            Arguments.of("without-configuration", "teamcity-gradle-template-command.properties", emptyList<String>()),
            Arguments.of("without-configuration", "teamcity-gradle-template-command-include-all-deps.properties", listOf("DBSM-Cloud-API:0.1.71","DBSM-Cloud-Common:0.1.67")),

            Arguments.of("transitive-dependencies", "teamcity-gradle-template-command.properties", emptyList<String>()),

            // indirect "DBSM-Cloud-API:0.1.55" must not be included!
            Arguments.of("transitive-dependencies", "teamcity-gradle-template-command-include-all-deps.properties", listOf("DBSM-Cloud-Common:0.1.54")),

            Arguments.of("multi-module", "template-all-deps-subproj_api.properties", listOf("DBSM-Cloud-API:0.1.71")),
            Arguments.of(
                "multi-module",
                "template-all-deps-subproj_core.properties",
                listOf("DBSM-Cloud-Common:0.1.67", "components-registry-service:0.0.645")
            ),
            Arguments.of(
                "multi-module",
                "template-all-deps-root_prj.properties",
                listOf("DBSM-Cloud-API:0.1.71", "DBSM-Cloud-Common:0.1.67", "components-registry-service:0.0.645")
            )
        )

        @JvmStatic
        fun subprojectDeclaredData(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "sub-projects",
                "template-deps-subproj2.properties",
                listOf("ComponentOne:3.2.1", "ComponentThree:7.8.9")
            )
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
    fun testDependedComponentsRegistration(project: String, commandPropFile: String, expected: Collection<String>) {
        teamcityDependenciesRegistrationTest(project, commandPropFile, expected)
    }

    @ParameterizedTest
    @MethodSource("subprojectDeclaredData")
    fun testSubprojectDeclared(project: String, commandPropFile: String, expected: Collection<String>) {
        teamcityDependenciesRegistrationTest(project, commandPropFile, expected)
    }

    fun teamcityDependenciesRegistrationTest(
        project: String,
        gradleCommandPropFile: String,
        expectedComponents: Collection<String>
    ) {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"]
            ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")

        val projectPath = Paths.get(
            ReleaseManagementPluginTest::class.java.getResource("/teamcity-dependencies-registration/$project")!!
                .toURI()
        )
        logger.debug("Project directory {}", projectPath)
        val gradleCommandAndLineProperties = Properties()
        ReleaseManagementPluginTest::class.java.getResourceAsStream("/teamcity-dependencies-registration/$gradleCommandPropFile")
            .use {
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
        val dependencies =
            stdout.find { line -> line.startsWith("##teamcity[setParameter name='DEPENDENCIES' value='") }
                ?.split(Regex("value='"))
                ?.get(1)
                ?.replace("']", "")
                ?.split(Regex("\\s*,\\s*"))
                ?: emptyList()
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
            .execute("clean", "assemble", "generatePomFileForMavenPublication", "-Poctopus-release-management.version=$releaseManagementVersion", "-PpackageName=$packageName")
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
                .commandAndArguments("$projectPath/gradlew", "-Poctopus-release-management.version=$releaseManagementVersion")
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

    @Test
    @DisplayName("RM config test in Kotlin style")
    fun testRmKotlinConfig() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"]
            ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val buildVersion: String = System.getenv()["__BUILD_VERSION__"]
            ?: throw IllegalStateException("The __BUILD_VERSION__ environment variable is not set")
        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/rm-kotlin-config")!!.toURI())
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val stdout = ArrayList<String>()
        val processInstance = processBuilder
            .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
            .logger { it.logger(logger) }
            .mapBatExtension()
            .mapCmdExtension()
            .workDirectory(projectPath)
            .stdOutConsumer(stdout::add)
            .commandAndArguments("$projectPath/gradlew")
            .build()
            .execute(
                "-Poctopus-release-management.version=$releaseManagementVersion",
                "-PbuildVersion=$buildVersion",
                "-PskipCheckDependencies=true",
            )
            .toCompletableFuture()
            .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        assertThat(stdout).contains(
            "##teamcity[setParameter name='DEPENDENCIES' value='deployer:1.1,deployerDSL:1.2']"
        )
    }


    @Test
    @DisplayName("Apply to subprojects test")
    fun testConfigureMoreThanOnce() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"]
            ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val buildVersion: String = System.getenv()["__BUILD_VERSION__"]
            ?: throw IllegalStateException("The __BUILD_VERSION__ environment variable is not set")
        val projectPath = Paths.get(ReleaseManagementPluginTest::class.java.getResource("/configure-more-than-once")!!.toURI())
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val stdout = ArrayList<String>()
        val processInstance = processBuilder
            .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
            .logger { it.logger(logger) }
            .mapBatExtension()
            .mapCmdExtension()
            .workDirectory(projectPath)
            .stdOutConsumer(stdout::add)
            .commandAndArguments("$projectPath/gradlew")
            .build()
            .execute(
                "-Poctopus-release-management.version=$releaseManagementVersion",
                "-PbuildVersion=$buildVersion",
                "--stacktrace",
                "--info",
                "tasks"
            )
            .toCompletableFuture()
            .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
    }

    private fun startRmMockServer() {
        data class Endpoint(val path: String, val body: String, val status: Int)

        val endpoints = listOf<Endpoint>(
            Endpoint(
                path = "/rest/api/1/builds/component/ReleaseManagementService/version/1.0.1",
                body = """{
                          "component": "ReleaseManagementService",
                          "version": "1.0.1",
                          "status": "RELEASE",
                          "parents": [
                            {
                              "component": "parent",
                              "version": "1.1",
                              "status": "BUILD"
                            }
                          ],
                          "dependencies": [],
                          "commits": [
                            {
                              "repository": "ssh://git@github.com:octopusden/octopus-release-management-service.git",
                              "sha": "0da242ad739a01e7c50aeb522fd17e1eece77bb3",
                              "branch": "refs/heads/master",
                              "inReleaseBranch": true
                            }
                          ],
                          "statusHistory": {
                            "BUILD": "2024-07-04T13:12:17.000+00:00",
                            "RC": "2024-07-04T19:56:43.000+00:00",
                            "RELEASE": "2024-07-04T20:03:30.000+00:00"
                          }
                    }""".trimIndent().replace("\n",""),
                status = 200,
            ),
            Endpoint(
                path = "/rest/api/1/builds/component/ReleaseManagementService/version/1.0.3",
                body = """
                {
                    "errorCode": "NOT_FOUND",
                    "errorMessage": "Build for ReleaseManagementService:1.0.3 not found"
                }""".trimIndent().replace("\n",""),
                status = 404,
            ),
        )
        mockRmServer.reset()
        endpoints.forEach { e ->
            mockRmServer.`when`(
                HttpRequest.request().withMethod("GET").withPath(e.path)
            ).respond {
                logger.debug(
                    "MockServer request: {} {} {} {}",
                    it.method,
                    it.path,
                    it.queryStringParameterList.joinToString(","),
                    it.pathParameterList.joinToString(",")
                )
                HttpResponse.response()
                    .withHeader("Content-Type", ContentType.APPLICATION_JSON.mimeType)
                    .withBody(e.body)
                    .withStatusCode(e.status)
            }
        }
    }

    @Test
    @DisplayName("Check dependencies test")
    fun testCheckDependencies() {
        startRmMockServer()
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"]
            ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val buildVersion: String = System.getenv()["__BUILD_VERSION__"]
            ?: throw IllegalStateException("The __BUILD_VERSION__ environment variable is not set")
        val projectPath =
            Paths.get(ReleaseManagementPluginTest::class.java.getResource("/check-dependencies")!!.toURI())
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val stdout = ArrayList<String>()
        val processInstance = processBuilder
            .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
            .logger { it.logger(logger) }
            .mapBatExtension()
            .mapCmdExtension()
            .workDirectory(projectPath)
            .stdOutConsumer(stdout::add)
            .stdErrConsumer(stdout::add)
            .commandAndArguments("$projectPath/gradlew")
            .build()
            .execute(
                "-Poctopus-release-management.version=$releaseManagementVersion",
                "-PbuildVersion=$buildVersion",
                "-Prelease-management-service.url=http://${mockRmServer.remoteAddress().hostString}:${mockRmServer.remoteAddress().port}",
            )
            .toCompletableFuture()
            .get()
        assertEquals(1, processInstance.exitCode, "Gradle execution failure")
        assertThat(stdout).contains("[ERROR] Build for ReleaseManagementService:1.0.3 not found")
    }
}
