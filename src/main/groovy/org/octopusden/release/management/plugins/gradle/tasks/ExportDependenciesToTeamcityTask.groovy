package org.octopusden.release.management.plugins.gradle.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.release.management.plugins.gradle.dto.ComponentArtifact
import org.octopusden.release.management.plugins.gradle.ReleaseManagementDependenciesExtension
import org.octopusden.release.management.plugins.gradle.dto.VersionedComponent
import org.apache.http.HttpStatus
import org.apache.http.client.HttpRequestRetryHandler
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.HTTP
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContextBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration

class ExportDependenciesToTeamcityTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportDependenciesToTeamcityTask.class)

    @Input @Optional
    def excludedConfigurations = ["sourceArtifacts", "-runtime", "runtimeElements", "runtimeOnly", "testRuntimeOnly", "testRuntime"]

    @TaskAction
    def exportDependencies() {
        def findComponentsRestUrl = System.getenv('COMPONENT_REGISTRY_SERVICE_URL') + '/rest/api/2/components/findByArtifacts'
        ReleaseManagementDependenciesExtension releaseManagementDependenciesExtension = project.rootProject.extensions.findByName("releaseManagement")
        def releaseDependenciesConfiguration = releaseManagementDependenciesExtension.getReleaseDependenciesConfiguration()
        if (releaseDependenciesConfiguration.getSupportedGroupIds() == null) {
            releaseDependenciesConfiguration.supportedGroupIds(getConfProperty("supportedGroupIds"))
            if (releaseDependenciesConfiguration.getSupportedGroupIds() == null || releaseDependenciesConfiguration.getSupportedGroupIds().size() == 0) {
                throw new GradleException("Property 'supportedGroupIds' must be set")
            }
        }
        logger.debug("FILTER = " + releaseDependenciesConfiguration.getSupportedGroupIds())

        final String dependenciesString
        if (releaseDependenciesConfiguration.isFromDependencies()) {
            Collection<ComponentArtifact> componentArtifacts = new HashSet<>()
            project.rootProject.allprojects { subProject ->
                subProject.configurations
                        .findAll {configuration -> !excludedConfigurations.contains(configuration.getName()) }
                        .forEach { Configuration configuration -> componentArtifacts.addAll(extractConfigurationDependencies(configuration, releaseDependenciesConfiguration.getSupportedGroupIds())) }
                logger.info("STEP1: {}", componentArtifacts)
                subProject.getBuildscript().configurations
                        .findAll {configuration -> !excludedConfigurations.contains(configuration.getName()) }
                        .forEach { Configuration configuration -> componentArtifacts.addAll(extractConfigurationDependencies(configuration, releaseDependenciesConfiguration.getSupportedGroupIds())) }
            }
            logger.info("STEP2: {}", componentArtifacts)
            def excludeModules = releaseDependenciesConfiguration.getExtractingConfiguration().getExcludeModules()
            if (!excludeModules.empty) {
                componentArtifacts.removeIf {componentArtifact -> excludeModules.any {excludeModule ->
                            excludeModule.group != null && excludeModule.group == componentArtifact.group && excludeModule.module != null && excludeModule.module == componentArtifact.name ||
                                    excludeModule.group == null && excludeModule.module != null && excludeModule.module == componentArtifact.name ||
                                    excludeModule.group != null && excludeModule.group == componentArtifact.group && excludeModule.module == null
                } }
            }
            logger.info("componentArtifacts 1: {}", componentArtifacts)
            def includeModules = releaseDependenciesConfiguration.getExtractingConfiguration().getIncludeModules()
            if (!includeModules.empty) {
                logger.info("includeModules: {}", includeModules)
                componentArtifacts.removeIf { componentArtifact -> !includeModules.any { includeModule ->
                    (includeModule.group == null && includeModule.module == componentArtifact.name) ||
                            (includeModule.module == null && includeModule.group == componentArtifact.group) ||
                            (includeModule.group != null && includeModule.module != null && includeModule.group == componentArtifact.group && includeModule.module == componentArtifact.name)
                } }
            }
            logger.info("componentArtifacts 2: {}", componentArtifacts)
            if (componentArtifacts.empty) {
                dependenciesString = ""
            } else {
                dependenciesString = getRestClient()
                        .withCloseable { httpClient ->
                            final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            HttpPost httpPost = new HttpPost(findComponentsRestUrl)
                            httpPost.addHeader(HTTP.CONTENT_TYPE, "application/json")
                            def entity = new StringEntity(objectMapper.writeValueAsString(componentArtifacts))
                            httpPost.setEntity(entity)
                            LOGGER.debug("Send POST request to $findComponentsRestUrl, payload $entity")
                            httpClient.execute(httpPost).withCloseable { response ->
                                if (response.statusLine.statusCode != HttpStatus.SC_OK) {
                                    LOGGER.error("Fail to call to " + findComponentsRestUrl +", response is" + response)
                                    throw new GradleException("unable to get components by artifacts because of response status code ${response.statusLine.statusCode}");
                                }
                                def responseContent = response.entity.content.text
                                LOGGER.debug("Response content is $responseContent")
                                objectMapper.readValue(responseContent, new TypeReference<List<VersionedComponent>>() { })
                            }
                        }
                        .findAll {versionedComponent -> releaseDependenciesConfiguration.getExtractingConfiguration().getExcludeComponents().empty || releaseDependenciesConfiguration.getExtractingConfiguration().excludeComponents.any {excludeComponent -> versionedComponent.id == excludeComponent.id || versionedComponent.id == excludeComponent.name } }
                        .collect {versionedComponent -> versionedComponent.id + ":" + versionedComponent.version }.join(",")
            }
       } else {
            dependenciesString = releaseDependenciesConfiguration.getComponents().collect { it.name + ":" + it.version }.join(",")
        }
        LOGGER.info("Found dependencies: {}", dependenciesString)
        println "##teamcity[setParameter name='DEPENDENCIES' value='$dependenciesString']"
    }

    private String[] getConfProperty(String name) {
        def confProperties = new Properties()
        confProperties.load(getClass().classLoader.getResourceAsStream("configuration.properties"))
        confProperties.getProperty(name).split(',')
    }

    public Collection<ComponentArtifact> extractConfigurationDependencies(Configuration configuration, String[] filters) {
        final Configuration copiedConfiguration = configuration.copyRecursive()
        copiedConfiguration.setCanBeConsumed(true)
        copiedConfiguration.setCanBeResolved(true)
        def list = copiedConfiguration
                .getIncoming()
                .getResolutionResult()
                .getAllDependencies()
                .findAll { dependencyResult -> dependencyResult instanceof ResolvedDependencyResult && ((ResolvedDependencyResult) dependencyResult).getSelected().getId() instanceof ModuleComponentIdentifier }
                .collect { moduleComponentIdentifier -> moduleComponentIdentifier.selected.id as ModuleComponentIdentifier }
        LOGGER.info("Dependensies: {}", list)
        LOGGER.info("Filter: {}", filters)
                list.findAll { componentIdentifier -> Arrays.stream(filters).anyMatch(filter -> {
                    def result = componentIdentifier.group.startsWith(filter)
                    LOGGER.info("MATCH {} = {}", componentIdentifier.group, result)
                    result
                }) && !componentIdentifier.group.endsWith('release-management')}
                .collect { componentIdentifier -> new ComponentArtifact(componentIdentifier.group, componentIdentifier.module, componentIdentifier.version) }
    }

    private CloseableHttpClient getRestClient() {
        final SSLContextBuilder sslContextBuilder = new SSLContextBuilder()
        sslContextBuilder.loadTrustMaterial(null, new TrustStrategy() {
            @Override
            boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true
            }
        })
        HttpClients.custom()
                .setRetryHandler(new HttpRequestRetryHandler() {
                    @Override
                    boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                        LOGGER.warn("An http call error", exception)
                        if (executionCount < 60) {
                            Thread.sleep(Duration.ofMinutes(1).toMillis())
                            return true
                        }
                        return false
                    }
                })
                .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContextBuilder.build(), new NoopHostnameVerifier()))
                .build()
    }
}
