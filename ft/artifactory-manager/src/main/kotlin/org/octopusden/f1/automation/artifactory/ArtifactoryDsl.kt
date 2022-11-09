package org.octopusden.f1.automation.artifactory

import org.octopusden.f1.automation.artifactory.model.BuildInfo
import org.octopusden.f1.automation.artifactory.model.BuildInfoResponse
import org.octopusden.f1.automation.artifactory.model.Promote
import org.apache.http.HttpStatus
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.ArtifactoryRequest
import org.jfrog.artifactory.client.ArtifactoryResponse
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date
import java.util.Properties

private val artifactoryLogger = LoggerFactory.getLogger("org.octopusden.f1.automation.ArtifactoryDsl")

fun artifactory(block: ArtifatoryDsl.() -> Unit) {
    block.invoke(ArtifatoryDsl())
}

class ArtifatoryDsl {
    lateinit var url: String
    private val artifactoryClient: Artifactory by lazy {
        val authConn = ArtifactoryConnection.defaultArtifactoryConnection(url)
        ArtifactoryClientBuilder.create()
            .setUrl(authConn.url)
            .setUsername(authConn.username)
            .setPassword(authConn.password)
            .build()
    }

    fun getBuildInfo(buildName: String, buildNumber: String): BuildInfo? {
        for (postfix in arrayOf("", "_ii", "_ee", "_ie", "_ei")) {
            val repositoryRequest: ArtifactoryRequest = ArtifactoryRequestImpl()
                .apiUrl("api/build/$buildName$postfix/$buildNumber")
                .method(ArtifactoryRequest.Method.GET)
                .responseType(ArtifactoryRequest.ContentType.JSON)
            val response = artifactoryClient.restCall(repositoryRequest)
            if (response.statusLine.statusCode == HttpStatus.SC_OK) {
                return response.parseBody(BuildInfoResponse::class.java).buildInfo
            }
            if (response.statusLine.statusCode != HttpStatus.SC_NOT_FOUND) {
                throw logError("Unexpected response", response)
            }
        }
        return null
    }

    fun promote(buildInfo: BuildInfo) {
        val buildName = buildInfo.name
        val buildNumber = buildInfo.number
        if (buildInfo.statuses?.find { it.status == "release" } == null) {
            val promotePayload = Promote("release",
                artifactoryClient.username,
                Date(),
                false,
                "rnd-maven-release-local",
                false,
                true,
                false,
                true
            )
            val repositoryRequest: ArtifactoryRequest = ArtifactoryRequestImpl()
                .apiUrl("api/build/promote/$buildName/$buildNumber")
                .method(ArtifactoryRequest.Method.POST)
                .requestType(ArtifactoryRequest.ContentType.JSON)
                .requestBody(promotePayload)
            val response = artifactoryClient.restCall(repositoryRequest)
            if (response.statusLine.statusCode != HttpStatus.SC_OK) {
                throw logError("Fail to promote build", response)
            }
        } else {
            artifactoryLogger.info("Build $buildInfo already promoted to release")
        }

    }
}

private fun logError(errorMessage: String, response: ArtifactoryResponse): Exception {
    artifactoryLogger.error("Status code: {}, body: {}", response.statusLine.statusCode, response.rawBody)
    return RuntimeException(errorMessage)
}

class ArtifactoryConnection private constructor(url: String, val username: String, val password: String) {
    val url: String

    init {
        this.url = "$url/artifactory"
    }

    companion object {
        @JvmStatic
        fun defaultArtifactoryConnection(url: String): ArtifactoryConnection {
            Files.newInputStream(Paths.get(System.getProperty("user.home")).resolve(".gradle/gradle.properties")).use {
                val gradleProperties = Properties()
                gradleProperties.load(it)
                return ArtifactoryConnection(
                    url,
                    gradleProperties.getProperty("NEXUS_USER"),
                    gradleProperties.getProperty("NEXUS_PASSWORD")
                )
            }
        }
    }
}

