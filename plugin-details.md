# ReleaseManagementGradlePlugin — Detailed Breakdown

## Overview
This is an **organizational release management plugin** for Gradle projects. It standardizes versioning, artifact publishing (via JFrog Artifactory), dependency tracking (for TeamCity CI), dependency auto-update configuration, and SBOM generation across all projects that apply it.

---

## 1. Idempotency / One-Time Guard
- Uses a root project extra property `releaseManagementConfigurationState` to ensure the core configuration logic runs **only once**, even if the plugin is applied to multiple subprojects.
- The `apply()` method returns early if this property is already set.

---

## 2. `exportDependenciesToTeamcity` Task
- **Registered on every project** that applies the plugin (if not already present).
- **Purpose**: Resolves the project's runtime dependencies, queries a **Components Registry Service** (via `COMPONENT_REGISTRY_SERVICE_URL` env var) to map Maven artifacts → internal component names, and writes the result as a JSON file (`components-dependencies.json` by default).
- **Inputs**:
    - `includedConfigurations` (default: `runtimeElements`, `runtimeClasspath`)
    - `excludedConfigurations`
    - `outputFile` property
    - `includeAllDependencies` project property
- **Auto-run behavior**: After evaluation, if `releaseManagement.releaseDependencies` has been configured (or `includeAllDependencies=true`) **and** `autoRegistration` is enabled (or `buildVersion` is set), the task is **automatically finalized by every other task** in all subprojects — meaning it runs at the end of any build.

---

## 3. `releaseManagement` Extension (`ReleaseManagementDependenciesExtension`)
- DSL block for declaring which components are release dependencies:
  ```groovy
  releaseManagement {
      releaseDependencies {
          component name: "foo", version: "1.0"
          fromDependencies()           // auto-extract from resolved deps
          autoRegistration = true      // enable auto-run of export task
      }
  }
  ```
- Supports explicit `component(Map)`, `component(String "name:version")`, and `fromDependencies()` with include/exclude filters.

---

## 4. `autoUpdateDependencies` Extension & `dumpAutoUpdateDependencies` Task
- **Extension** (`AutoUpdateDependenciesExtension`): lets you declare which component dependencies should be **automatically updated** by an external "Update Dependencies Service" (UDS).
  ```groovy
  autoUpdateDependencies {
      autoMapping = true  // scan gradle.properties for *.version keys
      component {
          name "my-component"
          projectProperty "myComponent.version"
          versionRange "[1.0,2.0)"
          pullRequest true
          createJiraIssue true
      }
  }
  ```
- `autoMapping = true` reads `gradle.properties` and auto-discovers all `*.version` properties.
- **Task** `dumpAutoUpdateDependencies` serializes this config to JSON for the UDS to consume.
- Validated during `afterEvaluate` to catch misconfigurations early.

---

## 5. `MavenPom.declareDependencies` (Metaclass Extension)
- Dynamically adds a `declareDependencies(configurations)` method to Gradle's `MavenPom` class.
- Allows POM generation to include dependencies from specific Gradle configurations:
  ```groovy
  publishing {
      publications {
          maven(MavenPublication) {
              pom.declareDependencies(configurations.runtimeClasspath)
          }
      }
  }
  ```
- Delegates to `MavenPomDependenciesUtility` which manipulates the POM XML.

---

## 6. Version Management (`setBuildVersion`)
- If the root project has a `buildVersion` property (typically set by CI), **all projects** get that version.
- If no version is specified at all (`unspecified`), defaults to `1.0-SNAPSHOT`.
- Applied eagerly (before `afterEvaluate`) and again inside `afterEvaluate` to ensure it sticks.
- Inside `afterEvaluate`, all subproject versions are forcibly set to match the root.

---

## 7. Escrow Build Support
- If `m2_local` property is set, the plugin enters **escrow mode**:
    - `escrowBuild = true`
    - All `buildscript` and project repositories are **cleared** and replaced with the local Maven repo path.
    - Publishing tasks target the local repo instead of Artifactory.
- This supports offline/air-gapped builds from a pre-populated local Maven cache.

---

## 8. JFrog Artifactory Publishing (`com.jfrog.artifactory`)
- The plugin **applies `com.jfrog.artifactory`** to the root and all subprojects.
- Configures the Artifactory plugin with:
    - **Context URL**: `${ARTIFACTORY_URL}/artifactory`
    - **Repository**: `rnd-maven-release-local` (if `publishToReleaseRepository=true`) or `rnd-maven-dev-local` (default).
    - **Credentials**: from project properties, system properties, or environment variables (`ARTIFACTORY_DEPLOYER_USERNAME` / `ARTIFACTORY_DEPLOYER_PASSWORD`).
    - **Publications**: `ALL_PUBLICATIONS`
    - **Build info**: published.
- Skipped entirely if `ARTIFACTORY_URL` is not set or if in escrow mode.

---

## 9. Publishing Wiring (`setupRootPublishing` / `setupProjectPublishing`)
- For **escrow builds**: configures `maven-publish` repositories to point to the local M2 path.
- For **normal builds**:
    - If `com.jfrog.artifactory` is present, the `publish` task is wired to depend on `artifactoryPublish`.
    - Standard `PublishToMavenRepository` tasks are **disabled** (Artifactory plugin handles publishing instead).
    - If `maven-publish` is not applied to a subproject, `artifactoryPublish` is disabled for that subproject.
    - If the root project doesn't have `maven-publish`, it's auto-applied.

---

## 10. Backward Compatibility (Nexus Staging)
- Creates a `nexusStaging` extension (`GradleStagingPluginExtension`) — a **deprecated stub** for backward compatibility with old Nexus-based publishing.
- Registers four **no-op deprecated tasks**: `openStagingRepository`, `useStagingRepository`, `closeStagingRepository`, `releaseStagingRepository`.
- These exist solely so old TeamCity build configs don't fail.

---

## 11. CycloneDX SBOM Generation
- If the project property `cyclonedx.skip` is **explicitly set to `false`**:
    - Applies the `org.cyclonedx.bom` plugin to the root project.
    - Configures it: schema 1.4, JSON format, `runtimeClasspath` only, output to `build/generated-resources/sbom`.
    - Wires all subprojects' `assemble` tasks to depend on `:cyclonedxBom`.
- By default (property not set, or set to `true`), SBOM generation is **skipped**.

---

## Summary of Registered Tasks

| Task | Scope | Purpose |
|------|-------|---------|
| `exportDependenciesToTeamcity` | Per-project | Export resolved dependencies as JSON for TeamCity/CR |
| `dumpAutoUpdateDependencies` | Root only | Dump auto-update config as JSON for UDS |
| `openStagingRepository` | Root only | No-op (deprecated) |
| `useStagingRepository` | Root only | No-op (deprecated) |
| `closeStagingRepository` | Root only | No-op (deprecated) |
| `releaseStagingRepository` | Root only | No-op (deprecated) |

## Summary of Extensions

| Extension | Type | Scope |
|-----------|------|-------|
| `releaseManagement` | `ReleaseManagementDependenciesExtension` | Per-project |
| `autoUpdateDependencies` | `AutoUpdateDependenciesExtension` | Root only |
| `nexusStaging` | `GradleStagingPluginExtension` (deprecated) | Root only |

## Key Properties / Environment Variables

| Name | Source | Purpose |
|------|--------|---------|
| `buildVersion` | Project property | Override version for all projects |
| `m2_local` | Project property | Path to local Maven repo (escrow mode) |
| `ARTIFACTORY_URL` / `artifactoryUrl` | Env / property | Artifactory server base URL |
| `ARTIFACTORY_DEPLOYER_USERNAME` | Env / sys prop / project prop | Artifactory credentials |
| `ARTIFACTORY_DEPLOYER_PASSWORD` | Env / sys prop / project prop | Artifactory credentials |
| `publishToReleaseRepository` | Property / sys prop / env | Target release vs dev repo |
| `includeAllDependencies` | Project property | Force export of all dependencies |
| `COMPONENT_REGISTRY_SERVICE_URL` | Env variable | Components Registry API URL |
| `cyclonedx.skip` | Project property | Enable/disable SBOM generation |
| `outputFile` | Project property | Custom output path for dependency JSON |
