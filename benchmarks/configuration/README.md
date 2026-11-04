# Resolution-rule configuration benchmark

Compare the configuration cost of preparing and registering resolution rules across source checkouts. The harness
uses each checkout's compiled plugin classes; plugin compilation happens before measurement.

## Run

Requires Ruby 3 with its standard library, the repository's Gradle wrapper, and a locally available JDK 21. Worktrees
must contain matching dependency locks. The harness checks that their resolved dependency JAR paths match.

Create checkouts of the main baseline and original PR:

```sh
git fetch origin pull/178/head
git worktree add --detach /tmp/resolution-rules-main 6f2b88aa5e797f13bf92ea5b47be81f263e2575b
git worktree add --detach /tmp/resolution-rules-pr178 7a71a36d71c8fe7fca28bf02b556d1f96da7a158
```

From each checkout's root, including this service version, build its plugin classes and TestKit classpath metadata:

```sh
./gradlew classes pluginUnderTestMetadata --max-workers=2
```

From the service version's root, run:

```sh
ruby benchmarks/configuration/benchmark.rb \
  --scenario main=/tmp/resolution-rules-main \
  --scenario original-pr=/tmp/resolution-rules-pr178 \
  --scenario build-service="$PWD" \
  --work-dir /tmp/resolution-rules-configuration-benchmark
```

Choose a new work directory for each comparison. Generated projects stay under `fixture-<label>/`; logs, Gradle HTML
profiles, CSV/JSON samples and summaries go under `results/`. The script refuses to overwrite measured results.
It runs Gradle from each checkout's root with `-p` pointing to the fixture. Use `--prepare-only` to inspect the generated
builds, or `--help` to change project/configuration counts, warmups or measured runs.

For a quick smoke run, append `--projects 2 --warmups 0 --runs 1` and use a fresh work directory. Those settings validate
the harness; they do not reproduce the recorded performance comparison.

## Workload and timing

- One root and **200 Java subprojects**, each with **37 eligible configurations**: 7,400 configurations total.
- Each subproject declares 20 dummy implementation dependencies, inherited by the additional configurations.
- One shared local JSON file has **2,195 rules**: 285 replace, 1,049 substitute, 692 reject, 22 deny, 41 exclude and
  106 align. Counts follow [PR #178](https://github.com/nebula-plugins/gradle-resolution-rules-plugin/pull/178);
  module names are generated.
- The fixture has no application sources. Each build runs `help`, registering rules without resolving application
  dependency graphs or downloading their artifacts.
- Defaults: three warmups and nine measured builds per variant, rotating order each round. With three variants,
  each occupies every position equally often. Finish other builds and tests before measuring.
- All variants use the same dependency JARs and a shared warm daemon with a 3 GiB heap. Builds use two workers,
  offline mode, and disabled configuration cache and parallel project configuration.

The primary timer spans `projectsLoaded` through `projectsEvaluated`, including project `afterEvaluate` callbacks.
It excludes JVM startup, settings evaluation, task execution and plugin compilation. Gradle's `--profile` HTML is
secondary evidence: its project counter ends before some project-local `afterEvaluate` work and undercounts this cost.

## Recorded comparison

Gradle 9.7.1, JDK 21, macOS 15.8/aarch64, the same 21 dependency JARs, and the default workload and sampling above.

| Version | Commit | Median configuration | Mean | Range |
|---|---|---:|---:|---:|
| Main | `6f2b88a` | 1.765 s | 1.785 s | 1.693–1.905 s |
| Original PR #178 | `7a71a36` | 0.737 s | 0.743 s | 0.655–0.859 s |
| Service-owned caches | `e4dfd49` | 0.791 s | 0.775 s | 0.708–0.814 s |

[measurements.json](measurements.json) preserves the original samples, warmups, environment and test totals.
The service median is 55.2% lower than main and 7.3% higher than #178 in this batch. The two cache variants have
overlapping ranges; these samples establish no clear performance winner between them. The motivation for the service
is ownership of runtime state while retaining per-project reuse.

These results apply to a synthetic configuration workload. They do not measure application dependency resolution,
downloads, compilation or end-to-end build performance.

## Full test comparison

Full test builds additionally require a locally discoverable JDK 8. `nebula-test`'s `GradleDependencyGenerator`
requests a Java 8 toolchain when publishing fixture dependencies. Without it, publication fails and the generator
returns without creating the fixture POMs, so subsequent resolution assertions fail. **Java 8 is only used to publish
fixture artifacts.** CI test processes use `JDK_VERSION_FOR_TESTS` from the **17/21/25 matrix**; `setup-java` makes the
last installed version (the matrix JDK) the active `JAVA_HOME`. The configuration benchmark does not publish fixtures.

The full test builds used this command from each source checkout, with the same test-limit init script:

```sh
./gradlew build --continue --rerun-tasks --no-build-cache --max-workers=2 --console=plain --stacktrace \
  -I /path/to/service-checkout/benchmarks/configuration/test-limits.init.gradle
```

Both PR variants passed 247 tests with 15 skipped and zero failures/errors on JDK 21, including the same two added
cache/project-isolation checks. Main was compiled and benchmarked; its full suite was not rerun for this comparison.
