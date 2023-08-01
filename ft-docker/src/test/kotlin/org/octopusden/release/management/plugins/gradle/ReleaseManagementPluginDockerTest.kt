package org.octopusden.release.management.plugins.gradle

import com.platformlib.process.factory.ProcessBuilders
import com.platformlib.process.local.builder.LocalProcessBuilder
import com.platformlib.process.local.specification.LocalProcessSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.slf4j.LoggerFactory
import java.lang.System
import java.nio.file.Paths

class ReleaseManagementPluginDockerTest {
    companion object {
        private val logger = LoggerFactory.getLogger(ReleaseManagementPluginDockerTest::class.java)
        private val dockerRegistry = System.getenv()["DOCKER_REGISTRY"] ?: throw IllegalArgumentException("Environment variable DOCKER_REGISTRY is not set")
    }

    @Test
    fun testPullDockerImage() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val projectPath = Paths.get(ReleaseManagementPluginDockerTest::class.java.getResource("/mesh-agent2")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val packageName: String = System.getProperty("packageName").toString()
        val envVariables = mutableMapOf("JAVA_HOME" to System.getProperty("java.home"))
        System.getenv()["DOCKER_HOST"]?.let { dockerHost ->
            envVariables["DOCKER_HOST"] = dockerHost
        }
        val processInstance = processBuilder
                .envVariables(envVariables)
                .logger { it.logger(logger) }
                .processInstance { it.unlimited() }
                .mapBatExtension()
                .mapCmdExtension()
                .workDirectory(projectPath)
                .commandAndArguments("$projectPath/gradlew")
                .build()
                .execute(
                    "-Poctopus-release-management.version=$releaseManagementVersion",
                    "-Pescrow.build-phase=ASSEMBLE",
                    "-Pdocker.registry=$dockerRegistry",
                    "-PpackageName=$packageName",
                    "clean",
                    "build",
                    "--dry-run"
                )
                .toCompletableFuture()
                .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        assertThat(processInstance.stdOut).contains("Pull docker image $dockerRegistry/platform/go-build:1.1.7")
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "buildpack is not supported on Windows")
    fun testSpringBootBootBuildImage() {
        val releaseManagementVersion: String = System.getenv()["__RELEASE_MANAGEMENT_VERSION__"] ?: throw IllegalStateException("The __RELEASE_MANAGEMENT_VERSION__ environment variable is not set")
        val projectPath = Paths.get(ReleaseManagementPluginDockerTest::class.java.getResource("/uds")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        val processInstance = processBuilder
            .envVariables(mapOf("JAVA_HOME" to System.getProperty("java.home")))
            .logger { it.logger(logger) }
            .processInstance { it.unlimited() }
            .mapBatExtension()
            .mapCmdExtension()
            .workDirectory(projectPath)
            .commandAndArguments("$projectPath/gradlew")
            .build()
            .execute(
                "-Poctopus-release-management.version=$releaseManagementVersion",
                "-Pescrow.build-phase=ASSEMBLE",
                "-Pdocker.registry=$dockerRegistry",
                "bootBuildImage"
            )
            .toCompletableFuture()
            .get()
        assertEquals(0, processInstance.exitCode, "Gradle execution failure")
        assertThat(processInstance.stdOut).anyMatch{logEntry -> logEntry.contains("Pulled builder image '$dockerRegistry/cnbs/uds-stack-builder")}
        assertThat(processInstance.stdOut).anyMatch{logEntry -> logEntry.contains("Pulled run image '$dockerRegistry/cnbs/uds-stack-run")}
    }
}
