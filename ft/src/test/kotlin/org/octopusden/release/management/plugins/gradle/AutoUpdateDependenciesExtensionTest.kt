package org.octopusden.release.management.plugins.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.readText

/**
 * Test autoUpdateDependencies extension
 */
class AutoUpdateDependenciesExtensionTest {
    companion object {
        private val objectMapper = ObjectMapper().registerKotlinModule()

        @JvmStatic
        fun autoUpdateDependenciesData(): Stream<Arguments> =  Stream.of(
            Arguments.of("auto-mapping",
                mapOf(
                    "a-doc.version" to "3.53.3-137",
                    "octopus-release-management.version" to "1.0-SNAPSHOT"
                ),
                emptyList<ComponentDependency>()
            ),
            Arguments.of("custom-mapping",
                mapOf("pl-commons-version" to "1.1", "as-server.version" to "1.7.3182"),
                listOf(
                    ComponentDependency( "appserver", "as-server.version", null,
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    ),
                    ComponentDependency( "platform-commons", "pl-commons-version", null,
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    )
                )
            ),
            Arguments.of("kotlin-dsl",
                mapOf("platform-commons.version" to "1.2.687"),
                listOf(
                    ComponentDependency( "platform-commons", "platform-commons.version", null,
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    )
                )
            ),
            Arguments.of("default-values",
                mapOf("pl-commons-version" to "1.1", "as-server.version" to "1.7.3182"),
                listOf(
                    ComponentDependency( "platform-commons", "pl-commons-version", null,
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    ),
                    ComponentDependency( "appserver", "as-server.version", null,
                        pullRequest = true,
                        createJiraIssue = true,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    )
                )
            ),
            Arguments.of("version-range",
                mapOf("pl-commons-version" to "1.1", "as-server.version" to "1.7.3182"),
                listOf(
                    ComponentDependency( "platform-commons", "pl-commons-version", "(1,)",
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    ),
                    ComponentDependency( "appserver", "as-server.version", "[1,)",
                        pullRequest = false,
                        createJiraIssue = false,
                        includeBuild = false,
                        includeRc = false,
                        includeRelease = true
                    )
                )
            )
        )

        @JvmStatic
        fun autoUpdateDependencies(projectPath: Path): AutoDependenciesConfiguration {
            val autoUpdateDependenciesConfigurationPath = projectPath.resolve("build/auto-update-dependencies-configuration.json")
            assertTrue(Files.isRegularFile(autoUpdateDependenciesConfigurationPath))
            return objectMapper.readValue(autoUpdateDependenciesConfigurationPath.readText(), AutoDependenciesConfiguration::class.java)
        }
    }

    @ParameterizedTest
    @MethodSource("autoUpdateDependenciesData")
    fun testDeclared(project: String, declaredAutoUpdateDependencies: Map<String, String>, customAutoUpdateDependencies: Collection<ComponentDependency>) {
        val autoDepConf = autoUpdateDependencies(gradle {
            testProjectName = "auto-update-dependencies/$project"
            tasks = arrayOf("clean", "dumpAutoUpdateDependencies")
        })
        assertThat(autoDepConf.declared).containsExactlyInAnyOrderEntriesOf(declaredAutoUpdateDependencies)
        assertThat(autoDepConf.configured).containsExactlyInAnyOrderElementsOf(customAutoUpdateDependencies)
    }

    /**
     * The same logic are implemented in Update Dependencies Service.
     */
    @Test
    fun testDumpToOutput() {
        val processInstance = gradleProcessInstance {
            testProjectName = "auto-update-dependencies/dump-to-output"
            tasks = arrayOf("clean", "dumpAutoUpdateDependencies")
            additionalArguments = arrayOf("--init-script", Paths.get(AutoUpdateDependenciesExtensionTest::class.java.getResource("/uds-init.gradle")!!.toURI()).toString(), "--dry-run")
        }
        val expectedOutput = """
-----BEGIN AUDC-----
{
   "declared" : {
    "as-server.version" : "1.7.3182"
   },
   "configured" : [ {
     "name" : "appserver",
     "project-property" : "as-server.version",
     "pull-request" : false,
     "create-jira-issue" : false
   } ]
}
-----END AUDC-----
"""
        //TODO Find howto compare with expected output
        assertThat(processInstance.first.stdOut).containsAnyElementsOf(listOf("-----BEGIN AUDC-----", "  \"declared\" : {", "  \"configured\" : [ {", "-----END AUDC-----"))
    }

}
