# Resolution-rule configuration benchmark

This generated Gradle build measures the cost of preparing and registering rules across many configurations. It has
one root project and 200 Java subprojects, with 37 eligible configurations per subproject (7,400 in total). Each
subproject declares 20 dummy implementation dependencies; additional configurations inherit those dependencies.

The shared local JSON file contains 2,195 rules: 285 replacements, 1,049 substitutions, 692 rejections, 22 denies,
41 excludes and 106 alignment rules. These counts follow the workload described in
[PR #178](https://github.com/nebula-plugins/gradle-resolution-rules-plugin/pull/178). Module names are generated.
The fixture has no application sources. Running `help` configures all projects and registers the rules without resolving
application dependency graphs or downloading their artifacts.

## Recorded comparison

Gradle 9.7.1, JDK 21, macOS 15.8/aarch64; the same 21 dependency JARs and a shared warm daemon with a 3 GiB heap.
Configuration cache and parallel project configuration were disabled, with two workers and offline mode.

| Version | Commit | Median configuration | Mean | Range |
|---|---|---:|---:|---:|
| Main | `6f2b88a` | 1.765 s | 1.785 s | 1.693–1.905 s |
| Original PR #178 | `7a71a36` | 0.737 s | 0.743 s | 0.655–0.859 s |
| Service-owned caches | `e4dfd49` | 0.791 s | 0.775 s | 0.708–0.814 s |

Each variant received three warmup builds followed by nine measured builds. Run order rotated each round, placing each
variant first, second and third equally often. Full test execution finished before these measurements.

The primary timer spans `projectsLoaded` through `projectsEvaluated`, including project `afterEvaluate` callbacks.
It excludes JVM startup, settings evaluation, task execution and plugin compilation. Gradle's `--profile` HTML is also
saved, but its project counter ends before some project-local `afterEvaluate` work and undercounts this plugin's cost.

The service version's median is 55.2% lower than main; the original PR's is 58.3% lower. The service version has a
7.3% higher median than the original PR in this batch, with overlapping ranges. These samples establish no clear
performance winner between the two cache locations. The service approach separates runtime bookkeeping from the DSL
while retaining most of the improvement over main. Results apply to this generated configuration workload.

[measurements.json](measurements.json) contains the individual measured values, warmup values, environment and test
counts. The two PR variants ran the same full build, including the two added cache/project-isolation checks: each had
247 tests pass, 15 skipped and zero failures or errors. Main was compiled and benchmarked; its full suite was not rerun
for this comparison. Later commits add documentation and this portable harness without changing plugin behavior.

## Reproduce

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

Choose a new work directory for each comparison. The script writes generated fixtures, every build log, Gradle HTML
profiles, raw CSV/JSON runs and a summary there. It runs Gradle from each checkout's root with `-p` pointing to the fixture.
Use `--prepare-only` to inspect the fixtures first, or `--help` for workload and sample-count options.

Full test builds additionally require a locally discoverable JDK 8. `nebula-test`'s `GradleDependencyGenerator`
requests a Java 8 toolchain when publishing fixture dependencies. Without it, publication fails and the generator
returns without creating the fixture POMs, so subsequent resolution assertions fail. CI explicitly installs JDK 8
alongside the JDKs used to build and run the tests; the configuration benchmark itself does not publish these fixtures.

The full test builds used this command from each source checkout, with the same test-limit init script:

```sh
./gradlew build --continue --rerun-tasks --no-build-cache --max-workers=2 --console=plain --stacktrace \
  -I /path/to/service-checkout/benchmarks/configuration/test-limits.init.gradle
```
