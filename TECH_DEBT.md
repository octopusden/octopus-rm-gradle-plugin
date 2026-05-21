# Tech Debt — Gradle 9 Migration

This document tracks known technical debt remaining after the migration of
`octopus-rm-gradle-plugin` from Gradle 8 to **Gradle 9**.

The plugin currently builds and the functional tests pass, so none of the
items below are blocking. They are listed in priority order so they can be
picked up incrementally.

Target runtime assumptions:
- Plugin is built with **JDK 21**.
- Plugin is consumed by Gradle projects running on **JDK 17+** (Gradle 9
  requires JDK 17 as a minimum).

---

## 1. Bump `cyclonedx-gradle-plugin` (1.7.4 → 2.x)

**File:** [`gradle.properties`](gradle.properties), [`ReleaseManagementGradlePlugin.groovy`](src/main/groovy/org/octopusden/release/management/plugins/gradle/ReleaseManagementGradlePlugin.groovy)

**Why:** Version `1.7.4` relies on internal Gradle APIs that have been
removed in Gradle 9. It does not break the current build because the SBOM
code path is only activated when a consumer sets the `cyclonedx.skip`
property — none of the existing functional tests do.

**Risk if left:** The first consumer that enables SBOM generation under
Gradle 9 will hit a hard `ClassNotFoundException` / `NoSuchMethodError` at
plugin-apply time.

**What to change:**
- `gradle.properties`: `cyclonedx.version=1.7.4` → `cyclonedx.version=2.3.1`
  (or current stable for Gradle 9).
- In `ReleaseManagementGradlePlugin.groovy`, the `cyclonedxBom { … }`
  configuration block must be updated to the 2.x DSL:
  - `destination = new File("build/generated-resources/sbom")` →
    `destination = layout.buildDirectory.dir("generated-resources/sbom").get().asFile`
  - `schemaVersion = "1.4"` → `schemaVersion = "1.5"` (1.4 is deprecated in 2.x)
  - `outputFormat = "json"` → `outputFormat = "JSON"` (the property became an enum)
- Add an FT (or extend an existing one) that exercises the SBOM path so
  this regression is caught next time.

---

## 2. Replace `${buildDir}` usages

**File:** [`build.gradle`](build.gradle) (line 53)

**Why:** `Project.getBuildDir()` is deprecated in Gradle 9 and scheduled
for removal in Gradle 10. It currently logs a deprecation warning per
build.

**What to change:**

```groovy
// before
def resourceDir = "${buildDir}/generated/resources"

// after
def resourceDir = layout.buildDirectory.dir("generated/resources").get().asFile
```

---

## 3. Simplify `settings.gradle` plugin resolution

**File:** [`settings.gradle`](settings.gradle)

**Why:** Since the JFrog Artifactory plugin is now consumed under its
proper marker (`com.jfrog.artifactory:com.jfrog.artifactory.gradle.plugin`),
the `resolutionStrategy { eachPlugin { … } }` redirect block is dead code.
Removing it eliminates a future foot-gun (it would silently mask a real
resolution failure).

**What to change:**

```groovy
pluginManagement {
    plugins {
        id "com.jfrog.artifactory" version settings['jfrog-artifactory.version']
        id("io.github.gradle-nexus.publish-plugin") version("2.0.0") apply(false)
    }
}
```

(Drop the `resolutionStrategy { eachPlugin { … } }` block entirely.)

---

## 4. Standardise plugin-state flag on `extraProperties`

**File:** [`ReleaseManagementGradlePlugin.groovy`](src/main/groovy/org/octopusden/release/management/plugins/gradle/ReleaseManagementGradlePlugin.groovy) (lines 38, 152)

**Why:** The current write uses Groovy's dynamic dispatch on
`ExtensionContainer`:

```groovy
project.rootProject.extensions[PLUGIN_STATE_PROPERTY] = "applied"
```

while the matching read goes through `hasProperty(...)`, which actually
checks **extra properties**. The two ends are inconsistent and the write
side could be tightened in a future Gradle/Groovy release.

**What to change:**

```groovy
// write
project.rootProject.extensions.extraProperties.set(PLUGIN_STATE_PROPERTY, "applied")

// read (unchanged — already works via extraProperties)
if (project.rootProject.hasProperty(PLUGIN_STATE_PROPERTY)) { ... }
```

---

## 5. Replace eager task APIs with the lazy task API

**Files:** [`ReleaseManagementGradlePlugin.groovy`](src/main/groovy/org/octopusden/release/management/plugins/gradle/ReleaseManagementGradlePlugin.groovy), [`build.gradle`](build.gradle)

**Why:** Every use of `Project.task(...)`, `tasks.getByName/findByPath`,
or `tasks.withType(...).forEach { … }` triggers eager task realisation
and emits Gradle 9 deprecation warnings. These APIs are scheduled for
removal in Gradle 10 and they also prevent adoption of the configuration
cache.

**Where:**

| Location | Current (eager) | Replace with (lazy) |
|---|---|---|
| `ReleaseManagementGradlePlugin.groovy:30` | `project.task("exportDependenciesToTeamcity", type: ExportDependenciesToTeamcityTask)` | `project.tasks.register("exportDependenciesToTeamcity", ExportDependenciesToTeamcityTask)` |
| `ReleaseManagementGradlePlugin.groovy:87` | `project.getTasksByName("exportDependenciesToTeamcity", false)[0] as ExportDependenciesToTeamcityTask` | Capture the `TaskProvider` returned by `register(...)` and reuse it. |
| `ReleaseManagementGradlePlugin.groovy:192` | `project.tasks.findByPath("publish")?.dependsOn(project.tasks.findByPath("artifactoryPublish"))` | `project.tasks.named("publish") { dependsOn(project.tasks.named("artifactoryPublish")) }` |
| `ReleaseManagementGradlePlugin.groovy:197` | `project.tasks.withType(PublishToMavenRepository.class)?.forEach { it.enabled = false }` | `project.tasks.withType(PublishToMavenRepository).configureEach { enabled = false }` |
| `ReleaseManagementGradlePlugin.groovy:200` | `project.tasks.findByPath("artifactoryPublish").enabled = false` | `project.tasks.named("artifactoryPublish") { enabled = false }` |
| `build.gradle` (bottom) | `task openStagingRepository` / `task closeStagingRepository` | `tasks.register("openStagingRepository")` / `tasks.register("closeStagingRepository")` |

---

## 6. Remove `MavenPom.metaClass.declareDependencies` injection

**File:** [`ReleaseManagementGradlePlugin.groovy`](src/main/groovy/org/octopusden/release/management/plugins/gradle/ReleaseManagementGradlePlugin.groovy) (lines 42–48)

**Why:** Injecting a method on Gradle's `MavenPom` interface via Groovy's
meta-class works under Groovy 4 but:
- It is incompatible with the **configuration cache**.
- It can be locked down by a future Gradle/Groovy release without notice.
- It is invisible to static tooling (IDE auto-complete, lint, etc.).

**Suggested replacement:** expose `declareDependencies` as a method on a
proper Gradle extension or as a static helper:

```groovy
// usage at the call site
import static org.octopusden.release.management.plugins.gradle.publish.MavenPomDependenciesUtility.*

mavenPublication {
    pom {
        withXml(fromConfigurations(myConfigurations))
    }
}
```

This is a public API change for plugin consumers, so coordinate the
rollout with the deprecation of the old DSL (keep the meta-class shim
emitting a warning for one release, then remove).

---

## 7. Make `ExportDependenciesToTeamcityTask` configuration-cache-safe

**File:** [`ExportDependenciesToTeamcityTask.java`](src/main/java/org/octopusden/release/management/plugins/gradle/tasks/ExportDependenciesToTeamcityTask.java)

**Why:** The task reads `getProject().findProperty(...)` in field
initialisers and calls `getProject()` from inside `@TaskAction`. Both
patterns are forbidden by the Gradle configuration cache. They are
currently harmless because the configuration cache is not enabled.

**What to change (when adopting the configuration cache):**
- Convert `excludedConfigurations` / `includedConfigurations` /
  `includeAllDependencies` / `outputFile` to `Property<T>` / `ListProperty<T>`
  and wire them in the plugin's `register(...)` block.
- Replace `getProject().getConfigurations().detachedConfiguration(...)`
  inside `extractConfigurationDependencies` with an
  `@Input` / `@Internal`-annotated provider captured at configuration time
  (e.g. via `@ServiceReference` or by pre-resolving and serialising the
  needed metadata in the plugin).
- Replace `getProject().getAllprojects()` traversal with explicit inputs
  computed at configuration time.

---
## Suggested rollout

| PR | Items | Rationale |
|---|---|---|
| A | 1, 2, 3, 4 | Low effort, high value — silences Gradle 9 deprecations and removes the only latent runtime failure (cyclonedx 1.7.4). |
| B | 5 | Mechanical lazy-task migration; required before Gradle 10. |
| C | 6, 7 | Required before enabling the Gradle configuration cache; coordinate with a consumer-visible deprecation of `MavenPom.declareDependencies`. |
