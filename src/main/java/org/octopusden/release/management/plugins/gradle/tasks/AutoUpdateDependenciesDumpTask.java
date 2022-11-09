package org.octopusden.release.management.plugins.gradle.tasks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.octopusden.release.management.plugins.gradle.AutoUpdateDependenciesExtension;
import org.octopusden.release.management.plugins.gradle.dto.ComponentDependency;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Do not change signature or behaviour, just add new one and mark old one as deprecated.
 * Delete deprecated only after supporting new signatures in Update Dependencies Service.
 */
public class AutoUpdateDependenciesDumpTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoUpdateDependenciesDumpTask.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private File outputFile;

    /**
     * Empty constructor for Gradle.
     */
    public AutoUpdateDependenciesDumpTask() {
    }

    @OutputFile
    @org.gradle.api.tasks.Optional
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void dumpAutoUpdateDependenciesConfiguration() {
        getAutoUpdateDependenciesConfiguration().ifPresent(autoUpdateDependenciesConfiguration -> {
            try {
                if (getOutputFile() == null) {
                    System.out.println(
                        "-----BEGIN AUTO UPDATE DEPENDENCIES CONFIGURATION-----\n" +
                                deserializeToJson(autoUpdateDependenciesConfiguration) +
                        "\n-----END AUTO UPDATE DEPENDENCIES CONFIGURATION-----"
                    );
                } else {
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(getOutputFile(), autoUpdateDependenciesConfiguration);
                }
            } catch (final IOException ioException) {
                throw new GradleException("An error while dumping auto dependencies configuration", ioException);
            }
        });
    }

    @Internal
    public Optional<AutoUpdateDependenciesConfiguration> getAutoUpdateDependenciesConfiguration() {
        final AutoUpdateDependenciesConfiguration autoUpdateDependenciesConfiguration = new AutoUpdateDependenciesConfiguration();
        final AutoUpdateDependenciesExtension autoUpdateDependenciesExtension = getProject().getRootProject().getExtensions().getByType(AutoUpdateDependenciesExtension.class);
        if (autoUpdateDependenciesExtension.getAutoMapping()) {
            LOGGER.debug("Auto mapping dependencies is specified");
            final Path gradlePropertiesPath = getProject().getRootProject().file("gradle.properties").toPath();
            if (Files.isRegularFile(gradlePropertiesPath)) {
                try (InputStream is = new BufferedInputStream(Files.newInputStream(gradlePropertiesPath))) {
                    final Properties properties = new Properties();
                    properties.load(is);
                    properties.forEach((k, v) -> {
                        if (k.toString().endsWith(".version")) {
                            autoUpdateDependenciesConfiguration.declared.put((String) k, (String) v);
                        }
                    });
                } catch (final IOException ioException) {
                    throw new GradleException("Unable to read " + gradlePropertiesPath, ioException);
                }
            }
        }
        autoUpdateDependenciesExtension.getComponents().forEach(componentDependency -> {
            final String projectProperty = Objects.requireNonNull(componentDependency.getProjectProperty(), "Project property must be set for " + componentDependency.getComponentKey());
            if (!getProject().getRootProject().hasProperty(projectProperty)) {
                throw new GradleException("The specified project property '" + projectProperty + "' is unknown");
            }
            autoUpdateDependenciesConfiguration.configured.add(componentDependency);
            autoUpdateDependenciesConfiguration.declared.put(projectProperty, Objects.requireNonNull(getProject().getRootProject().findProperty(projectProperty)).toString());
        });

        if (autoUpdateDependenciesConfiguration.configured.isEmpty() && autoUpdateDependenciesConfiguration.declared.isEmpty()) {
            LOGGER.debug("Dependency auto updating is not configured");
            return java.util.Optional.empty();
        }
        return Optional.of(autoUpdateDependenciesConfiguration);
    }

    /**
     * Get JSON transformed auto update dependencies configuration.
     * Any changes in this method has to be synchronized with Update Dependencies Service (UDS).
     * UDS uses this method directly.
     * @return Returns dependencies auto update configuration in JSON format
     */
    public Optional<String> toPrettyJson() {
        return getAutoUpdateDependenciesConfiguration().map(AutoUpdateDependenciesDumpTask::deserializeToJson);
    }

    private static String deserializeToJson(final AutoUpdateDependenciesConfiguration autoUpdateDependenciesConfiguration) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(autoUpdateDependenciesConfiguration);
        } catch (IOException ioException) {
            throw new IllegalStateException(ioException);
        }
    }

    /**
     * Auto update dependencies configuration.
     * Any changes in this class has to be synchronized with Update Dependencies Service.
     */
    private static final class AutoUpdateDependenciesConfiguration {
        @JsonProperty("declared")
        private final Map<String, String> declared = new HashMap<>();

        @JsonProperty("configured")
        private final Collection<ComponentDependency> configured = new ArrayList<>();
    }
}
