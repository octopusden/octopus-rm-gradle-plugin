package org.octopusden.release.management.plugins.gradle

import com.platformlib.process.api.ProcessInstance
import com.platformlib.process.builder.ProcessBuilder
import com.platformlib.process.factory.ProcessBuilders
import com.platformlib.process.local.specification.LocalProcessSpec
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val LOGGER = LoggerFactory.getLogger("org.octopusden.release.management.plugins.gradle")!!

open class TestGradleDSL {
    lateinit var testProjectName: String
    var additionalArguments: Array<String> = arrayOf()
    var tasks: Array<String> = arrayOf()
}

fun gradle(init: TestGradleDSL.() -> Unit): Path {
    val (processInstance, projectPath) = gradleProcessInstance(init)
    if (processInstance.exitCode != 0) {
        throw IllegalStateException("An error while gradle exec, exit code is ${processInstance.exitCode}")
    }
    return projectPath
}

fun gradleProcessInstance(init: TestGradleDSL.() -> Unit): Pair<ProcessInstance, Path> {
    val testGradleDSL = TestGradleDSL()
    init.invoke(testGradleDSL)
    val resource = TestGradleDSL::class.java.getResource("/${testGradleDSL.testProjectName}") ?: throw IllegalArgumentException("The specified project ${testGradleDSL.testProjectName} hasn't been found in resources")
    val projectPath = Paths.get(resource.toURI())
    if (!Files.isDirectory(projectPath)) {
        throw IllegalArgumentException("The specified project '${testGradleDSL.testProjectName}' hasn't been found at $projectPath")
    }
    val releaseManagementVersion: String = System.getenv().getOrDefault("__RELEASE_MANAGEMENT_VERSION__", "1.0-SNAPSHOT")
    return Pair(ProcessBuilders
        .newProcessBuilder<ProcessBuilder>(LocalProcessSpec.LOCAL_COMMAND)
        .envVariables(mapOf("JAVA_HOME" to System.getProperties().getProperty("java.home")))
        .logger { it.logger(LOGGER) }
        .defaultExtensionMapping()
        .workDirectory(projectPath)
        .processInstance { processInstanceConfiguration -> processInstanceConfiguration.unlimited() }
        .commandAndArguments("$projectPath/gradlew", "--no-daemon")
        .build()
        .execute(*(listOf("-Prelease-management.version=$releaseManagementVersion") + testGradleDSL.tasks + testGradleDSL.additionalArguments).toTypedArray())
        .toCompletableFuture()
        .join(), projectPath)
}