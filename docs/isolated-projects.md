# Project-owned resolution rules for Isolated Projects (NEBULA-4645)

## Existing behavior remains the default for ordinary builds

Without Gradle Isolated Projects, programmatic adoption or an enabled opt-in property, the plugin
retains the existing build-wide `nebulaResolutionRules` service. The first project to register it supplies rule
sources for all participating projects. Parsed rule instances remain shared, and child-only
plugin application retains its implicit creation of the root extension. Direct lookups of the
legacy service name and the public `registerService(Project)` method continue to work as before.

With Gradle Isolated Projects active, the existing plugin automatically uses project-owned rule
state. Nebula can keep its existing `apply(ResolutionRulesPlugin::class.java)` call after updating
the plugin dependency; consumers need no second flag or new API call for this mode.
Convention plugins can also explicitly adopt the project-owned default through the API below
before enabling Gradle Isolated Projects.
Exact first-registration behavior can depend on project configuration order; it cannot also
guarantee deterministic independent child inputs under concurrent configuration. This preserves
that policy for ordinary builds and changes source ownership when adopting isolated state.

Existing public types and signatures, synchronous `ruleSet()` behavior, append setters, optional opt-in,
include-over-exclude precedence, rule ordering, duplicate ruleset-name precedence, alignment,
replacement history and project-local callback timing are unchanged.

## Adoption

### Automatic adoption with the existing plugin

Enable Gradle Isolated Projects using the mechanism supported by your Gradle version, and apply
the existing plugin locally in every participating project. The plugin reads the effective
feature state through Gradle's public `BuildFeatures` service, covering both the older system
property and the newer `--isolated-projects` option.

An explicit `nebulaResolutionRules.isolatedState` property takes precedence over its earlier
`projectLocal` alias, then over the programmatic default or automatic selection. Setting it to
`false` restores legacy behavior; that behavior remains incompatible with strict multi-project
isolation. The programmatic API enables project-owned state in ordinary builds too.

Automatic detection uses a public API available since Gradle 8.5. Older versions keep the legacy
default unless explicitly opted in. Verification remains limited to the Gradle baselines below;
this guard does not establish support for additional older versions.

### From Nebula or another convention plugin

To adopt project-owned state before enabling Gradle Isolated Projects, replace the convention
plugin's direct application of `ResolutionRulesPlugin` with:

```kotlin
import nebula.plugin.resolutionrules.ResolutionRulesPlugin

ResolutionRulesPlugin.applyWithIsolatedState(project)
```

The same static method is available to Java and Groovy callers. It selects project-owned state
and applies the existing plugin through Gradle's plugin manager. No project property, additional
plugin ID or settings plugin is required. Call it locally in every participating project,
including the root; root-only adoption does not change the mode of children.

An explicit `nebulaResolutionRules.isolatedState` Gradle property overrides the programmatic
default, so `-PnebulaResolutionRules.isolatedState=false` remains a rollback option. The earlier
`nebulaResolutionRules.projectLocal` name remains a compatibility alias; if both properties are
set, `isolatedState` takes precedence. Without an explicit adoption mechanism or Gradle Isolated
Projects, the legacy default remains unchanged. The selected mode is fixed
when this project's plugin is applied or its public service registration is first requested.
Repeated compatible calls are safe; adoption after legacy application or service registration
fails with guidance to move the call earlier, since root-extension access cannot be undone.

In Nebula, this replacement in `DependencyPlugins.apply` is optional when Gradle Isolated
Projects is enabled. Retain the existing root-only `configureResolutionRules()` artifact
declarations. Children continue to receive
those artifacts through their declarative root dependency. SBN's project-local optional rule
selections continue to work; enabling the default in SBN after Nebula has already applied the
legacy plugin is too late. Adoption here addresses resolution rules only, not other plugins'
cross-project access.

### From a build

Set the following in the root `gradle.properties`, or pass the equivalent `-P` option:

```properties
nebulaResolutionRules.isolatedState=true
```

Apply the existing plugin locally in every participating project, directly or through a
convention plugin. Replace cross-project `allprojects`/`subprojects` configuration when adopting
Gradle Isolated Projects. Root-only plugin application still does not opt children into rules.

With project-owned state selected by any of these mechanisms:

- Each project resolves its own `resolutionRules` configuration, including its declared root
  dependency. Additional child sources affect only that child. Move intentionally shared sources
  to the root producer configuration before opting in.
- Each project registers `nebulaResolutionRules.projectLocal` followed by its absolute project
  path, including the root. Parsed mutable rules and preparation state are owned by that service.
  Use `registerService(Project)` instead of looking up a service by a literal name in this mode.
- Children never inspect or create root extensions. Apply the plugin on the root if its extension
  is needed, or declare the root producer configuration locally without applying the plugin:

```groovy
configurations { resolutionRules }
dependencies { resolutionRules files('rules.json') }
```

Multi-project strict isolation selects project-owned state automatically unless explicitly overridden.
The legacy path retains its cross-project access and is not claimed compatible with strict
multi-project isolation. Rules continue to apply in both paths; neither path disables the plugin.
Per-project parsing has additional memory and preparation cost; no shared parsed-rule cache is added.

## Cache inputs and verification

Both modes read JSON contents through `ProviderFactory.fileContents`. Archive contents are also
tracked through that API before the existing `ZipFile` parser runs, since native archive reads
are not tracked as configuration inputs. This fixes rule-file edit invalidation while preserving
the source policy and parsing order. Archive tracking adds a transient full-content read.

Coverage uses Gradle 8.14.3 (a provisional lower test baseline, not a new minimum support
declaration) and wrapper Gradle 9.7.1. Legacy contracts force either child to register first and
verify the resulting shared sources, mutable rule identity, global service name, implicit root
extension, explicit-false setting and cache reuse. They also verify switching the option on
and returning to the default without stale cached results. The ordinary integration suite runs
with the option absent, exercising the existing default path.

The opt-in matrix covers ordinary and strict isolation modes, single/sibling/nested projects,
root artifact transport, explicit root producers, nonparticipating children, early public API
calls, selections, within-project identity, ordering, alignment and replacement history across
late configurations. Independent child inputs are exercised with reversed task requests and
repeated concurrent configuration. Cache checks cover storage/reuse and invalidation after JSON,
JAR/ZIP and selection edits, including watcher-enabled input checks. Strict fixtures use
`--configuration-cache-problems=fail`; there are no ignored-problem modes or expected isolation
failures counted as successful migration coverage.

Strict fixtures use the ordinary plugin entry point without an ownership property, exercising
automatic adoption. The explicit API also runs in the independent-child matrix. Override checks
exercise automatic and programmatic defaults, both property names and cache reuse.

The CI JDK 25 failure was reproduced as `Unsupported class file major version 69`: Gradle 8.14.3's
Groovy compiler cannot run on JDK 25. Older Gradle fixtures now select CI's installed JDK 21 through
`org.gradle.java.home`, while wrapper fixtures retain the matrix JDK. No scenarios are skipped.
When running the suite from JDK 25 or newer outside CI, set `JAVA_HOME_21_X64` or
`JAVA_HOME_21_AARCH64` to an installed JDK 21.

```sh
./gradlew test validatePlugins integrationTest --max-workers=2 --console=plain
```

Verification on 2026-11-05 passed `./gradlew build --max-workers=2 --console=plain` with JDK 25
in 3m 14s, using JDK 21 for the Gradle 8.14.3 fixtures. All 73 unit/TestKit tests passed
(56 assessment scenarios and 17 existing tests), along with plugin validation, the integration
suite (with its existing skipped case), and ArchRules checks. The preceding 71-test revision
also passed the unit, validation and integration suites on JDK 21.

Public JVM signature comparisons against the original compiled plugin, extension, service,
companions, parameter interface and rule-data wrapper found no removed or changed signatures.
The only additions on these types are `applyWithIsolatedState(Project)` on the plugin and its
companion, including the static entry point for Java and Groovy callers.

No settings plugin or rule-engine redesign is introduced. Consumer cross-project customizations,
composite builds and versions outside the tested baselines are outside this bounded verification.
No release is published.
