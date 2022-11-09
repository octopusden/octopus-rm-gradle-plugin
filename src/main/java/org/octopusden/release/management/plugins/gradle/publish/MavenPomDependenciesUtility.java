package org.octopusden.release.management.plugins.gradle.publish;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MavenPomDependenciesUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenPomDependenciesUtility.class);

    public static Action<XmlProvider> fromConfiguration(final Configuration configuration) {
        return fromConfigurations(Collections.singleton(configuration));
    }

    public static Action<XmlProvider> fromConfigurations(final Collection<Configuration> configurations) {
        return xmlProvider -> {
            final Element root = xmlProvider.asElement();
            final Document document = root.getOwnerDocument();
            final Node dependencies = root.appendChild(document.createElement("dependencies"));
            configurations.forEach(configuration -> {
                configuration.getDependencies().forEach(dependency -> {
                    final Node dependencyNode = dependencies.appendChild(document.createElement("dependency"));
                    final Node groupId = dependencyNode.appendChild(document.createElement("groupId"));
                    groupId.setTextContent(dependency.getGroup());
                    final Node artifactId = dependencyNode.appendChild(document.createElement("artifactId"));
                    artifactId.setTextContent(dependency.getName());
                    final Node version = dependencyNode.appendChild(document.createElement("version"));
                    version.setTextContent(dependency.getVersion());
                    if (dependency instanceof ModuleDependency) {
                        final ModuleDependency moduleDependency = (ModuleDependency) dependency;
                        final Set<DependencyArtifact> artifacts = moduleDependency.getArtifacts();
                        if (artifacts.size() != 1) {
                            LOGGER.debug("  Configuration {} has no or more than one dependency artifacts {}", configuration.getName(), artifacts);
                        } else {
                            final DependencyArtifact artifact = artifacts.iterator().next();
                            if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
                                final Node classifier = dependencyNode.appendChild(document.createElement("classifier"));
                                classifier.setTextContent(artifact.getClassifier());
                            }
                            if (artifact.getType() != null && !artifact.getType().isEmpty() && !"jar".equals(artifact.getType())) {
                                final Node type = dependencyNode.appendChild(document.createElement("type"));
                                type.setTextContent(artifact.getType());
                            }
                        }
                    }
                });
            });
        };
    }
}
