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
        val projectPath = Paths.get(ReleaseManagementPluginDockerTest::class.java.getResource("/test-agent")!!.toURI())
        logger.debug("Project directory {}", projectPath)
        val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
        var packageName: String = System.getProperty("packageName").toString()
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

}
