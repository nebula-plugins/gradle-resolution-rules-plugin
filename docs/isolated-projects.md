# Project-owned resolution rules (NEBULA-4645)

## Ownership and compatibility

The existing project plugin remains the entry point. Each participating project creates only
its own extension. A child still declares a dependency on the root `resolutionRules`
configuration; it never reads or creates the root extension. A root that does not apply the
plugin must declare its producer configuration and dependencies itself:

```groovy
configurations { resolutionRules }
dependencies { resolutionRules files('rules.json') }
```

Apply `com.netflix.nebula.resolution-rules` in each child that needs rules. Keep repositories,
selection configuration and tasks local, or configure them through a locally applied convention
plugin. Root-only application continues to affect only the root. Builds that relied on a child
implicitly creating a root extension must now explicitly configure the root.

Each project's `registerService(Project)` resolves that project's own `resolutionRules`
configuration, including the existing root dependency and any additional local sources. The
root service retains the name `nebulaResolutionRules`; child services use
`nebulaResolutionRules` followed by the absolute project path. Each registration parses separate
rule objects, so mutable substitution selectors, rejection selectors and alignment matchers do
not cross project boundaries. The cost is parsing shared sources once per participating project;
no cross-project parsed-rule cache is introduced.

JSON contents are read through `ProviderFactory.fileContents`. Archive contents are also
registered through that API before the existing `ZipFile` parser runs, since native archive
reads are not tracked as configuration inputs. Both formats now invalidate cached preparation
when their contents change, including the tested watcher-enabled cases. Archive tracking adds
a transient full-content read; the existing archive parser and entry order are retained.

Public types and signatures, `ruleSet()`'s synchronous behavior and within-project rule identity,
the extension's mutable ArrayList APIs and append setters remain unchanged. Selection still
requires explicit optional opt-in; nonempty include takes precedence over exclude. Rule-file
encounter order, last-source-wins handling of duplicate ruleset names, first-pass registration
order and the alignment second pass remain unchanged. Replacement history remains once per
project and declaration identity, including across selection changes. Configuration filtering,
`configureEach`/`onExecute` timing and the already-resolved-configuration warning remain unchanged.

## Intentional behavior decision for owners

Before implementation, a temporary TestKit characterization forced either child A or child B
to register first, with different local substitution files. On both Gradle 8.14.3 and 9.7.1,
both children then used the first registering child's sources. Reversing the first registration
reversed both children's results. That behavior followed the single build-wide registration,
rather than each project's declared inputs.

This candidate adopts project-owned sources in both ordinary and isolated modes. A child's
additional rules no longer become global, nor disappear because another project registered
first. Builds intentionally relying on that behavior must move shared sources to the root
producer configuration. Owner confirmation is requested for this behavior correction and the
assumption that literal child build-service registration names are internal. The public
`registerService(Project)` API is preserved; callers should use it to obtain the owning service.
The legacy root registration name is retained, but a global child registration alias would
reintroduce ambiguous ownership and is not provided.

## Verification

The converted assessment coverage uses Gradle 8.14.3 (a provisional lower baseline, not a new
minimum support declaration) and wrapper Gradle 9.7.1, in ordinary and strict isolation modes.
Isolation fixtures use `--configuration-cache-problems=fail`, `--parallel` and two workers;
there is no ignored-problem mode or conditional disabling of rules.

Coverage includes single projects, siblings, nested children, explicit root producers without
root plugin application, nonparticipating children, multiple configurations and late-created
configurations. It checks resolved artifacts, selections, alignment, early public `ruleSet()`
calls, retained within-project identities, project-local mutable rules, replacement history,
plugin order, reversed task requests and swapped child inputs. Repeated fresh configurations
exercise concurrent configuration with independent child inputs.

Cache checks assert initial storage, unchanged reuse, changed outputs after root JSON and
selection property edits, and subsequent reuse. Additional tests use filesystem watching for
child JSON and JAR/ZIP content invalidation, archive entry ordering, duplicate ruleset names,
include-over-exclude precedence and optional opt-in. The original assessment matrix keeps
filesystem watching disabled for deterministic external fixture edits.

```sh
./gradlew test validatePlugins integrationTest --max-workers=2 --console=plain
```

Final verification on 2026-11-05 completed successfully with JDK 21: all 53 unit/TestKit tests
passed (36 migrated assessment scenarios and 17 existing tests), `validatePlugins` passed, and
the existing integration suite passed with its existing skipped case. The final combined build
took 3m 59s. `javap -public` comparisons against the original compiled plugin, extension,
service, service companion, parameter interface and rule-data wrapper found no signature changes.

The migration does not add a settings plugin, change the rule engine or publish a release.
Custom rules that cross project boundaries, consumer cross-project configuration, composite
builds and alternate JVMs are outside this bounded verification.
