# Opt-in project-owned resolution rules (NEBULA-4645)

## Existing behavior remains the default

Without programmatic adoption or an enabled opt-in property, the plugin retains the existing
build-wide `nebulaResolutionRules` service. The first project to register it supplies rule
sources for all participating projects. Parsed rule instances remain shared, and child-only
plugin application retains its implicit creation of the root extension. Direct lookups of the
legacy service name and the public `registerService(Project)` method continue to work as before.

No automatic switch is tied to Gradle's isolation flag. Convention plugins can explicitly adopt
the project-owned default through the API below without setting any project property.
Exact first-registration behavior can depend on project configuration order; it cannot also
guarantee deterministic independent child
inputs under concurrent configuration. This candidate preserves that policy for existing builds
and makes the source-ownership change an explicit adoption step.

Existing public types and signatures, synchronous `ruleSet()` behavior, append setters, optional opt-in,
include-over-exclude precedence, rule ordering, duplicate ruleset-name precedence, alignment,
replacement history and project-local callback timing are unchanged.

## Explicit adoption for Isolated Projects

### From Nebula or another convention plugin

Replace the convention plugin's direct application of `ResolutionRulesPlugin` with:

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
set, `isolatedState` takes precedence. Without
either adoption mechanism, the legacy default remains unchanged. The selected mode is fixed
when this project's plugin is applied or its public service registration is first requested.
Repeated compatible calls are safe; adoption after legacy application or service registration
fails with guidance to move the call earlier, since root-extension access cannot be undone.

In Nebula, make this replacement in `DependencyPlugins.apply` and retain the existing
root-only `configureResolutionRules()` artifact declarations. Children continue to receive
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

With this option enabled:

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

Multi-project strict isolation requires adoption through the API or property as well as Gradle's
isolation setting.
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

```sh
./gradlew test validatePlugins integrationTest --max-workers=2 --console=plain
```

Verification on 2026-11-05 completed successfully with JDK 21 in 2m 52s: all 57 unit/TestKit tests
passed (40 assessment/compatibility scenarios and 17 existing tests), plugin validation passed,
and the existing integration suite passed with its existing skipped case. Public JVM signature
comparisons against the original compiled plugin, extension, service, companion, parameter
interface and rule-data wrapper found no changes.

The subsequent programmatic API is an additive public method on the plugin and its companion.
Its assessment coverage exercises property-free adoption on both Gradle baselines, explicit
property overrides, repeated application and service identity, rejection of late mode changes,
independent child sources under strict isolation and concurrent configuration, configuration
cache reuse, and rule-file invalidation.

Programmatic adoption and naming verification on 2026-11-05 passed the same full command above with JDK 21
in 3m 57s: all 71 unit/TestKit tests passed (54 assessment scenarios and 17 existing tests),
plugin validation passed, and the integration suite passed with its existing skipped case.
Coverage includes both property names and the precedence of `isolatedState` over `projectLocal`.
JVM signature inspection confirmed the static `applyWithIsolatedState` entry point for Java and Groovy callers.

No settings plugin or rule-engine redesign is introduced. Consumer cross-project customizations,
composite builds and alternate JVMs are outside this bounded verification. No release is published.
