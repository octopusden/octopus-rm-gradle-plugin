package org.octopusden.release.management.plugins.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient;
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient;
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider;
import org.octopusden.octopus.components.registry.core.dto.ArtifactDependency;
import org.octopusden.octopus.components.registry.core.dto.VersionedComponent;
import org.octopusden.release.management.plugins.gradle.ReleaseDependenciesConfiguration;
import org.octopusden.release.management.plugins.gradle.ReleaseManagementDependenciesExtension;
import org.octopusden.release.management.plugins.gradle.dto.ComponentArtifact;
import org.octopusden.release.management.plugins.gradle.dto.Module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportDependenciesToTeamcityTask extends DefaultTask {

    private static final String COMPONENT_FORMAT = "%s:%s";
    private static final String COMPONENT_REGISTRY_SERVICE_URL_PROPERTY = "COMPONENT_REGISTRY_SERVICE_URL";

    private final String componentsRegistryServiceUrl = System.getenv(COMPONENT_REGISTRY_SERVICE_URL_PROPERTY);

    private List<String> excludedConfigurations = Arrays.asList("sourceArtifacts", "-runtime", "runtimeElements", "runtimeOnly", "testRuntimeOnly", "testRuntime");

    private final boolean includeAllDependencies = Optional.ofNullable(getProject().findProperty("includeAllDependencies"))
            .map(v -> Boolean.parseBoolean(v.toString()))
            .orElse(false);

    private final boolean includeTransitive = Optional.ofNullable(getProject().findProperty("includeTransitive"))
            .map(v -> Boolean.parseBoolean(v.toString()))
            .orElse(false);

    private ComponentsRegistryServiceClient componentsRegistryServiceClient;

    public ExportDependenciesToTeamcityTask() {
    }

    @Input
    @org.gradle.api.tasks.Optional
    public List<String> getExcludedConfigurations() {
        return excludedConfigurations;
    }

    public void setExcludedConfigurations(List<String> excludedConfigurations) {
        this.excludedConfigurations = excludedConfigurations;
    }

    @TaskAction
    public void exportDependencies() {
        printProperties();

        if (includeTransitive && !includeAllDependencies) {
            throw new IllegalStateException("The option includeTransitive can be used only if the option includeAllDependencies was set to true");
        }

        final ReleaseManagementDependenciesExtension releaseManagementDependenciesExtension = (ReleaseManagementDependenciesExtension) getProject()
                .getRootProject()
                .getExtensions()
                .findByName("releaseManagement");

        final ReleaseDependenciesConfiguration releaseDependenciesConfiguration = (ReleaseDependenciesConfiguration) releaseManagementDependenciesExtension.getReleaseDependenciesConfiguration();

        final String dependenciesString;
        if (releaseDependenciesConfiguration.isFromDependencies() || includeAllDependencies ) {

            final List<String> componentsFromDependencies = getArtifactDependenciesString(releaseDependenciesConfiguration);
            final List<String> componentsFromConfiguration = releaseDependenciesConfiguration.getComponents()
                    .stream()
                    .map(c -> String.format(COMPONENT_FORMAT, c.getName(), c.getVersion())).collect(Collectors.toList());

            dependenciesString = Stream.concat(componentsFromDependencies.stream(), componentsFromConfiguration.stream())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));
        } else {
            dependenciesString = releaseDependenciesConfiguration.getComponents()
                    .stream()
                    .map(c -> String.format(COMPONENT_FORMAT, c.getName(), c.getVersion()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        getLogger().info("ExportDependenciesToTeamcityTask Found dependencies: {}", dependenciesString);
        System.out.printf("##teamcity[setParameter name='DEPENDENCIES' value='%s']%n", dependenciesString);
    }

    private void printProperties() {
        getLogger().info("ExportDependenciesToTeamcityTask Parameters: excludedConfigurations={}, includeAllDependencies={}, includeTransitive={}, componentRegistryServiceUrl={}",
                excludedConfigurations, includeAllDependencies, includeTransitive, componentsRegistryServiceUrl);
    }

    @NotNull
    private List<String> getArtifactDependenciesString(ReleaseDependenciesConfiguration releaseDependenciesConfiguration) {

        final List<ComponentArtifact> dependencies = getConfigurations().stream()
                .flatMap(c -> extractConfigurationDependencies(c, getFilters(releaseDependenciesConfiguration))
                        .stream())
                .collect(Collectors.toList());

        final Set<ArtifactDependency> artifacts = dependencies.stream()
                .map(ca -> new ArtifactDependency(ca.getGroup(), ca.getName(), ca.getVersion()))
                .collect(Collectors.toSet());

        return getComponentsRegistryServiceClient().findArtifactComponentsByArtifacts(artifacts)
                .getArtifactComponents()
                .stream()
                .map(ac -> {
                    final VersionedComponent component = ac.getComponent();
                    final String result;
                    if (component == null) {
                        getLogger().error("ExportDependenciesToTeamcityTask Component not found by {}", ac.getArtifact());
                        result = null;
                    } else {
                        result = String.format(COMPONENT_FORMAT, component.getId(), component.getVersion());
                    }
                    return result;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ComponentsRegistryServiceClient getComponentsRegistryServiceClient() {
        if (componentsRegistryServiceClient == null) {
            if (componentsRegistryServiceUrl == null) {
                throw new IllegalStateException(String.format("System Env variable '%s' must be set", COMPONENT_REGISTRY_SERVICE_URL_PROPERTY));
            }
            componentsRegistryServiceClient = new ClassicComponentsRegistryServiceClient(new ClassicComponentsRegistryServiceClientUrlProvider() {
                @NotNull
                @Override
                public String getApiUrl() {
                    return componentsRegistryServiceUrl;
                }
            });
        }
        return componentsRegistryServiceClient;
    }

    @NotNull
    private List<Predicate<ModuleComponentIdentifier>> getFilters(ReleaseDependenciesConfiguration releaseDependenciesConfiguration) {
        final Predicate<ModuleComponentIdentifier> excludePredicate = getExcludePredicate(releaseDependenciesConfiguration);
        final Predicate<ModuleComponentIdentifier> includePredicate = getIncludePredicate(releaseDependenciesConfiguration);
        final Predicate<ModuleComponentIdentifier> supportedGroupIdsPredicate = getSupportedGroupIdsPredicate();

        final List<Predicate<ModuleComponentIdentifier>> filters = new ArrayList<>();
        filters.add(supportedGroupIdsPredicate);
        filters.add(excludePredicate);
        if (! includeAllDependencies) {
            filters.add(includePredicate);
        }
        return filters;
    }

    private Collection<ComponentArtifact> extractConfigurationDependencies(Configuration configuration, Collection<Predicate<ModuleComponentIdentifier>> filters) {
        getLogger().debug("ExportDependenciesToTeamcityTask Configuration '{}' dependencies", configuration.getName());

        final Configuration copiedConfiguration = configuration.copyRecursive();
        copiedConfiguration.setCanBeConsumed(true);
        copiedConfiguration.setCanBeResolved(true);
        copiedConfiguration.setTransitive(includeTransitive);
        getLogger().info("ExportDependenciesToTeamcityTask Configuration '{}' dependencies, transitive = {}",
                configuration.getName(), includeTransitive);

        final List<ModuleComponentIdentifier> dependencies = copiedConfiguration.getIncoming()
                .getResolutionResult()
                .getAllDependencies()
                .stream()
                .filter(dependencyResult -> dependencyResult instanceof ResolvedDependencyResult && ((ResolvedDependencyResult) dependencyResult).getSelected().getId() instanceof ModuleComponentIdentifier)
                .map(moduleComponentIdentifier -> (ModuleComponentIdentifier) ((ResolvedDependencyResult) moduleComponentIdentifier).getSelected().getId())
                .collect(Collectors.toList());

        getLogger().info("ExportDependenciesToTeamcityTask Configuration '{}', dependencies candidates: {}", configuration.getName(), this.getTaskDependencies());

        return dependencies.stream()
                .filter(componentIdentifier -> {
                    boolean test = true;
                    final Iterator<Predicate<ModuleComponentIdentifier>> iterator = filters.iterator();
                    while (iterator.hasNext() && test) {
                        final Predicate<ModuleComponentIdentifier> filter = iterator.next();
                        test = filter.test(componentIdentifier);
                    }
                    getLogger().info("Filter matches {} {}", componentIdentifier, test);
                    return test;
                }).map(componentIdentifier ->
                        new ComponentArtifact(componentIdentifier.getGroup(), componentIdentifier.getModule(), componentIdentifier.getVersion())
                )
                .collect(Collectors.toList());
    }

    private Collection<Configuration> getConfigurations() {
        return getProject().getRootProject().getAllprojects().stream().flatMap(subProject ->
                subProject.getConfigurations()
                        .stream()
                        .filter(configuration -> !excludedConfigurations.contains(configuration.getName()))
        ).collect(Collectors.toList());
    }

    private Predicate<ModuleComponentIdentifier> getSupportedGroupIdsPredicate() {
        final Set<String> supportedGroupIds = getComponentsRegistryServiceClient().getSupportedGroupIds();
        return componentArtifact -> {
            final boolean passed = supportedGroupIds.stream().anyMatch(g -> componentArtifact.getGroup().startsWith(g));
            getLogger().info("ExportDependenciesToTeamcityTask SupportedGroups dependencies filter: {} passed = {}", componentArtifact, passed);
            return passed;
        };
    }

    @NotNull
    private static final Function<ModuleComponentIdentifier, Predicate<Module>> getModulePredicate =
            componentArtifact -> (Predicate<Module>) module -> {
                final String excludeGroup = module.getGroup();
                final String excludeModule = module.getModule();
                return excludeGroup != null && excludeGroup.equals(componentArtifact.getGroup()) && excludeModule != null && excludeModule.equals(componentArtifact.getGroup()) ||
                        excludeGroup == null && excludeModule != null && excludeModule.equals(componentArtifact.getModule()) ||
                        excludeGroup != null && excludeGroup.equals(componentArtifact.getGroup()) && excludeModule == null;
            };

    private Predicate<ModuleComponentIdentifier> getExcludePredicate(ReleaseDependenciesConfiguration releaseDependenciesConfiguration) {
        return componentArtifact -> {
            final boolean passed = releaseDependenciesConfiguration.getExtractingConfiguration()
                    .getExcludeModules()
                    .stream()
                    .noneMatch(getModulePredicate.apply(componentArtifact));
            getLogger().info("ExportDependenciesToTeamcityTask Exclude dependencies filter: {} passed = {}", componentArtifact, passed);
            return passed;
        };
    }

    private Predicate<ModuleComponentIdentifier> getIncludePredicate(ReleaseDependenciesConfiguration releaseDependenciesConfiguration) {
        return componentArtifact -> {
            final boolean passed = releaseDependenciesConfiguration.getExtractingConfiguration()
                    .getIncludeModules()
                    .stream()
                    .anyMatch(getModulePredicate.apply(componentArtifact));
            getLogger().info("ExportDependenciesToTeamcityTask Include dependencies filter: {} passed = {}", componentArtifact, passed);
            return passed;
        };
    }

}
