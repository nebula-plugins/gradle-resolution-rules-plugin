package nebula.plugin.resolutionrules

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Strict isolation regression coverage for the opt-in mode, plus legacy default compatibility contracts.
 * All plugins, repositories, tasks and selections are configured in their owning project's script.
 * The repository has no declared Gradle minimum; 8.14.3 is a provisional lower baseline.
 * Filesystem watching is disabled for deterministic edits made outside the fixture daemon.
 * Additional source-ownership coverage below also runs with filesystem watching enabled.
 */
internal class IsolatedProjectsAssessmentTest {
    @TempDir lateinit var dir: File

    private val versions = listOf("8.14.3", GradleVersion.current().version).distinct()
    private val modes = listOf(false, true)

    @TestFactory
    fun `legacy contracts survive explicit opt in and returning to the default`() = versions.flatMap { version ->
        listOf("a", "b").map { first ->
            DynamicTest.dynamicTest("legacy Gradle $version first=$first") {
                val fixture = Fixture(File(dir, "legacy-$version-$first"), version, false, projectLocal = false)
                fixture.settings("a", "b")
                fixture.write("build.gradle", """
                    configurations { resolutionRules }
                    dependencies { resolutionRules files('rules.json') }
                    evaluationDependsOn(':$first')
                    def localRules = providers.gradleProperty('nebulaResolutionRules.projectLocal').orNull == 'true'
                    if (localRules) {
                        assert extensions.findByName('nebulaResolutionRules') == null
                    } else {
                        // Child-only application still creates the root extension in legacy mode.
                        assert extensions.findByName('nebulaResolutionRules') != null
                        assert gradle.sharedServices.registrations.findByName('nebulaResolutionRules') != null
                        assert nebulaResolutionRules.ruleSet().align[0].ruleSet == 'legacy-shared'
                    }
                """.trimIndent())
                listOf("a", "b").forEachIndexed { index, path ->
                    fixture.write("$path/local.json", """
                        {"substitute":[{"module":"probe:local","with":"probe:target:${index + 1}",${Fixture.metadata}}]}
                    """.trimIndent())
                    fixture.write("$path/build.gradle", """
                        ${fixture.consumer("..", false)}
                        dependencies { resolutionRules files('local.json'); implementation 'probe:local:1' }
                        def localRules = providers.gradleProperty('nebulaResolutionRules.projectLocal').orNull == 'true'
                        def rules = nebulaResolutionRules.ruleSet()
                        assert rules.substitute.find { it.module == 'probe:local' }.with ==
                            (localRules ? 'probe:target:${index + 1}' : 'probe:target:${if (first == "a") "1" else "2"}')
                        assert rules.align[0].ruleSet ==
                            (localRules ? 'rules' : '${if (path == first) "rules" else "legacy-shared"}')
                        rules.align[0].ruleSet = 'legacy-shared'
                        def serviceName = localRules ? 'nebulaResolutionRules.projectLocal:$path' : 'nebulaResolutionRules'
                        assert gradle.sharedServices.registrations.findByName(serviceName) != null
                    """.trimIndent())
                }
                val tasks = arrayOf(":b:resolveRuntimeRules", ":a:resolveRules", ":a:resolveRuntimeRules", ":b:resolveRules")
                fun verify(projectLocal: Boolean = false) {
                    listOf("a", "b").forEach { path ->
                        val source = if (projectLocal) path else first
                        fixture.artifacts(path, fixture.expected(if (source == "a") "1" else "2"))
                        fixture.artifacts(path, fixture.expected(if (source == "a") "1" else "2"), "runtimeResolved")
                    }
                }
                fixture.stored(fixture.run(*tasks))
                verify()
                fixture.reused(fixture.run(*tasks))
                verify()
                // An explicit false setting also retains the old source policy and service contract.
                fixture.run(*tasks, "-PnebulaResolutionRules.projectLocal=false")
                verify()
                fixture.reused(fixture.run(*tasks, "-PnebulaResolutionRules.projectLocal=false"))
                verify()
                val optedIn = fixture.run(*tasks, "-PnebulaResolutionRules.projectLocal=true")
                verify(projectLocal = true)
                fixture.stored(optedIn)
                fixture.reused(fixture.run(*tasks, "-PnebulaResolutionRules.projectLocal=true"))
                verify(projectLocal = true)
                fixture.run(*tasks)
                verify()
                fixture.reused(fixture.run(*tasks))
                verify()
            }
        }
    }

    @TestFactory
    fun `replacement history survives a selection refresh and late configurations`() = versions.flatMap { version ->
        modes.map { isolated ->
            DynamicTest.dynamicTest("replacement history Gradle $version isolated=$isolated") {
                val fixture = Fixture(File(dir, "replacement-$version-$isolated"), version, isolated)
                fixture.settings("a", "b")
                fixture.write("build.gradle", """
                    configurations { resolutionRules }
                    dependencies { resolutionRules files('rules.json') }
                """.trimIndent())
                listOf("a", "b").forEach { path ->
                    fixture.write("$path/build.gradle", """
                        plugins { id 'java'; id 'com.netflix.nebula.resolution-rules' }
                        repositories { maven { url = uri('../repo') } }
                        configurations.create('first')
                        dependencies { first 'probe:old:1', 'probe:replacement:2' }
                        tasks.register('resolveFirst', Sync) {
                            from configurations.first
                            into layout.buildDirectory.dir('first')
                        }
                        afterEvaluate {
                            dependencies.modules.module('probe:old') { replacedBy('probe:target', 'intervening declaration') }
                            nebulaResolutionRules.exclude = ['absent']
                            configurations.create('late')
                            dependencies.add('late', 'probe:old:1')
                            dependencies.add('late', 'probe:target:1')
                            tasks.register('resolveLate', Sync) {
                                from configurations.late
                                into layout.buildDirectory.dir('late')
                            }
                        }
                    """.trimIndent())
                }
                val tasks = arrayOf(":b:resolveLate", ":a:resolveFirst", ":a:resolveLate", ":b:resolveFirst")
                repeat(2) { run ->
                    val result = fixture.run(*tasks)
                    if (run == 0) fixture.stored(result) else fixture.reused(result)
                    listOf("a", "b").forEach { path ->
                        // The project-wide intervening declaration wins for both configurations. Replaying the
                        // original rule when preparing 'late' would leave old-1.jar in that configuration.
                        fixture.artifacts(path, setOf("old-1.jar", "replacement-2.jar"), "first")
                        fixture.artifacts(path, setOf("target-1.jar"), "late")
                    }
                }
            }
        }
    }

    @TestFactory
    fun `archive ordering selection precedence and file invalidation`() = versions.flatMap { version ->
        listOf("jar", "zip").map { format ->
            DynamicTest.dynamicTest("$format inputs Gradle $version strict isolation") {
                val fixture = Fixture(File(dir, "archive-$version-$format"), version, true, watchFs = true)
                fixture.settings("child")
                fun archive(target: String) = fixture.archive("rules.$format", linkedMapOf(
                    "nested/first.json" to """{"substitute":[{"module":"probe:sub","with":"probe:replacement:1",${Fixture.metadata}}]}""",
                    "second.json" to """{"substitute":[{"module":"probe:sub","with":"probe:replacement:2",${Fixture.metadata}}]}""",
                    "overrides/first.json" to """{"substitute":[{"module":"probe:sub","with":"probe:target:$target",${Fixture.metadata}}]}""",
                    "optional-extra.json" to """{"exclude":[{"module":"probe:excluded",${Fixture.metadata}}]}"""
                ))
                archive("1")
                fixture.write("build.gradle", """
                    configurations { resolutionRules }
                    dependencies { resolutionRules files('rules.$format') }
                """.trimIndent())
                fixture.write("child/build.gradle", """
                    plugins { id 'java'; id 'com.netflix.nebula.resolution-rules' }
                    repositories { maven { url = uri('../repo') } }
                    dependencies { implementation 'probe:sub:1', 'probe:excluded:1' }
                    nebulaResolutionRules {
                        include = ['first']
                        include = ['second']
                        exclude = ['first', 'second', 'optional-extra']
                        optional = ['unused']
                        optional = ['extra']
                    }
                    // Includes beat excludes, optional files require opt-in, and assignment appends. Both
                    // substitutions survive in archive order; the first matching declaration must win.
                    // The last source named 'first' overrides its contents without moving its position.
                    def rules = nebulaResolutionRules.ruleSet()
                    assert rules.substitute*.ruleSet == ['first', 'second']
                    assert rules.exclude*.ruleSet == ['optional-extra']
                    assert nebulaResolutionRules.include == ['first', 'second']
                    assert nebulaResolutionRules.optional == ['unused', 'extra']
                    tasks.register('resolveRules', Sync) {
                        from configurations.compileClasspath
                        into layout.buildDirectory.dir('resolved')
                    }
                """.trimIndent())
                fixture.stored(fixture.run(":child:resolveRules"))
                fixture.artifacts("child", setOf("target-1.jar"))
                fixture.reused(fixture.run(":child:resolveRules"))
                archive("2")
                val changed = fixture.run(":child:resolveRules")
                fixture.artifacts("child", setOf("target-2.jar"))
                fixture.stored(changed)
                fixture.reused(fixture.run(":child:resolveRules"))
                fixture.artifacts("child", setOf("target-2.jar"))
            }
        }
    }

    @TestFactory
    fun `child sources are independent of project and task order`() = versions.flatMap { version ->
        modes.flatMap { isolated ->
            listOf(false, true).flatMap { programmatic ->
                listOf(false, true).map { reverse ->
                    DynamicTest.dynamicTest("local sources Gradle $version isolated=$isolated reverse=$reverse programmatic=$programmatic") {
                        val fixture = Fixture(File(dir, "sources-$version-$isolated-$reverse-$programmatic"), version, isolated,
                            watchFs = true, programmatic = programmatic)
                        fixture.write("build.gradle", """
                            configurations { resolutionRules }
                            dependencies { resolutionRules files('rules.json') }
                        """.trimIndent())
                        // Swap which project has which inputs, as well as settings/task and plugin application order.
                        val projects = if (reverse) listOf("b", "a") else listOf("a", "b")
                        fixture.settings(*projects.toTypedArray())
                        projects.forEachIndexed { index, path ->
                            fixture.write("$path/local.json", """
                                {"substitute":[{"module":"probe:local","with":"probe:target:${index + 1}",${Fixture.metadata}}]}
                            """.trimIndent())
                            fixture.write("$path/build.gradle", """
                                ${fixture.consumer("..", false, pluginFirst = index == 0)}
                                dependencies { resolutionRules files('local.json'); implementation 'probe:local:1' }
                                providers.gradleProperty('configurationAttempt').orNull
                                // Public synchronous API: registering early must use this project's inputs. Repeated calls
                                // preserve within-project identity, including after refreshing the selection snapshot.
                                def rules = nebulaResolutionRules.ruleSet()
                                assert rules.dependencyRulesPartOne()*.class*.simpleName ==
                                    ['ReplaceRule', 'DenyRules', 'ExcludeRule', 'SubstituteRules', 'RejectRules']
                                def substitution = rules.substitute.find { it.module == 'probe:local' }
                                assert nebulaResolutionRules.ruleSet().substitute.find { it.module == 'probe:local' }.is(substitution)
                                nebulaResolutionRules.exclude = ['absent']
                                assert nebulaResolutionRules.ruleSet().substitute.find { it.module == 'probe:local' }.is(substitution)
                                // All projects read the common root source, but its mutable rules belong only to this project.
                                assert rules.align[0].ruleSet == 'rules'
                                rules.align[0].ruleSet = '$path'
                            """.trimIndent())
                        }
                        val tasks = projects.reversed().flatMap { listOf(":$it:resolveRuntimeRules", ":$it:resolveRules") }.toTypedArray()
                        fun verify(localVersion: String = "1") {
                            projects.forEachIndexed { index, path ->
                                val expected = fixture.expected() - "target-1.jar" + "target-${if (index == 0) localVersion else "2"}.jar"
                                fixture.artifacts(path, expected)
                                fixture.artifacts(path, expected, "runtimeResolved")
                            }
                        }
                        fixture.stored(fixture.run(*tasks))
                        verify()
                        fixture.reused(fixture.run(*tasks))
                        verify()
                        // Several fresh concurrent configurations plus reversed task order must give the same outputs.
                        repeat(3) {
                            fixture.stored(fixture.run(*tasks.reversedArray(), "-PconfigurationAttempt=$it"))
                            verify()
                        }
                        fixture.run(*tasks)
                        fixture.reused(fixture.run(*tasks))
                        verify()
                        fixture.write("${projects.first()}/local.json", """
                            {"substitute":[{"module":"probe:local","with":"probe:target:2",${Fixture.metadata}}]}
                        """.trimIndent())
                        val changed = fixture.run(*tasks)
                        verify("2")
                        fixture.stored(changed)
                        fixture.reused(fixture.run(*tasks))
                        verify("2")
                    }
                }
            }
        }
    }

    @TestFactory
    fun `programmatic adoption supports explicit overrides and cache reuse`() = versions.map { version ->
        DynamicTest.dynamicTest("programmatic overrides Gradle $version") {
            val fixture = Fixture(File(dir, "programmatic-overrides-$version"), version, false, programmatic = true)
            fixture.settings()
            fixture.write("build.gradle", """
                ${fixture.consumer(".", true)}
                def localRules = providers.gradleProperty('nebulaResolutionRules.isolatedState')
                    .orElse(providers.gradleProperty('nebulaResolutionRules.projectLocal')).orNull != 'false'
                def service = nebula.plugin.resolutionrules.NebulaResolutionRulesService.@Companion.registerService(project)
                assert nebulaResolutionRules.ruleSet().substitute[0].with == 'probe:target:1'
                nebula.plugin.resolutionrules.ResolutionRulesPlugin.applyWithIsolatedState(project)
                assert nebula.plugin.resolutionrules.NebulaResolutionRulesService.@Companion.registerService(project).get().is(service.get())
                assert gradle.sharedServices.registrations.findByName(
                    localRules ? 'nebulaResolutionRules.projectLocal:' : 'nebulaResolutionRules') != null
                assert gradle.sharedServices.registrations.findByName(
                    localRules ? 'nebulaResolutionRules' : 'nebulaResolutionRules.projectLocal:') == null
            """.trimIndent())
            listOf(emptyArray<String>(), arrayOf("-PnebulaResolutionRules.projectLocal=false"),
                arrayOf("-PnebulaResolutionRules.projectLocal=true"),
                arrayOf("-PnebulaResolutionRules.isolatedState=false"),
                arrayOf("-PnebulaResolutionRules.isolatedState=true"),
                arrayOf("-PnebulaResolutionRules.isolatedState=false", "-PnebulaResolutionRules.projectLocal=true"),
                arrayOf("-PnebulaResolutionRules.isolatedState=true", "-PnebulaResolutionRules.projectLocal=false"),
                emptyArray()).forEach { flags ->
                fixture.run(":resolveRules", *flags)
                fixture.artifacts("", fixture.expected())
                fixture.reused(fixture.run(":resolveRules", *flags))
                fixture.artifacts("", fixture.expected())
            }
        }
    }

    @TestFactory
    fun `programmatic adoption rejects changing a previously selected legacy mode`() = versions.flatMap { version ->
        listOf(false, true).map { serviceFirst ->
            DynamicTest.dynamicTest("late programmatic adoption Gradle $version serviceFirst=$serviceFirst") {
                val fixture = Fixture(File(dir, "late-adoption-$version-$serviceFirst"), version, false, projectLocal = false)
                fixture.settings()
                fixture.write("build.gradle", """
                    plugins { id 'com.netflix.nebula.resolution-rules' ${if (serviceFirst) "apply false" else ""} }
                    ${if (serviceFirst) """
                        configurations { resolutionRules }
                        nebula.plugin.resolutionrules.NebulaResolutionRulesService.@Companion.registerService(project)
                    """.trimIndent() else ""}
                    nebula.plugin.resolutionrules.ResolutionRulesPlugin.applyWithIsolatedState(project)
                """.trimIndent())
                val result = fixture.fails("help")
                assertTrue(result.output.contains("Resolution rules mode is already selected for :"), result.output)
                assertTrue(result.output.contains("before applying ResolutionRulesPlugin or registering its service"), result.output)
            }
        }
    }

    @TestFactory
    fun `single project behavior cache reuse and input invalidation`() = versions.flatMap { version ->
        modes.map { isolated ->
            DynamicTest.dynamicTest("single project Gradle $version isolated=$isolated") {
                val fixture = Fixture(File(dir, "single-$version-$isolated"), version, isolated)
                fixture.settings()
                fixture.write("build.gradle", fixture.consumer(".", true))
                fixture.verifyCacheAndInvalidation(listOf(""))
            }
        }
    }

    @TestFactory
    fun `siblings and nested projects use root artifacts with independent selections`() = versions.flatMap { version ->
        listOf(false, true).flatMap { nested ->
            modes.map { isolated ->
                DynamicTest.dynamicTest("${if (nested) "nested" else "siblings"} Gradle $version isolated=$isolated") {
                    val fixture = Fixture(File(dir, "multi-$version-$nested-$isolated"), version, isolated)
                    val projects = if (nested) listOf("group", "group/leaf") else listOf("a", "b")
                    fixture.settings(*projects.map { it.replace('/', ':') }.toTypedArray())
                    fixture.write("build.gradle", fixture.producer())
                    projects.forEachIndexed { index, path ->
                        fixture.write("$path/build.gradle", fixture.consumer(
                            List(path.count { it == '/' } + 1) { ".." }.joinToString("/"),
                            false,
                            if (index == 0) "" else "nebulaResolutionRules.exclude = ['rules']"
                        ))
                    }
                    fixture.verifyCacheAndInvalidation(projects, projects.drop(1).toSet())
                }
            }
        }
    }

    @TestFactory
    fun `child only application uses explicitly declared root producer`() = versions.flatMap { version ->
        modes.map { isolated ->
            DynamicTest.dynamicTest("child only Gradle $version isolated=$isolated") {
                val fixture = Fixture(File(dir, "child-only-$version-$isolated"), version, isolated)
                fixture.settings("child")
                // The root exports rules without applying the plugin or acquiring an extension from the child.
                fixture.write("build.gradle", """
                    configurations { resolutionRules }
                    dependencies { resolutionRules files('rules.json') }
                """.trimIndent())
                fixture.write("child/build.gradle", fixture.consumer("..", false))
                fixture.verifyCacheAndInvalidation(listOf("child"))
            }
        }
    }

    @TestFactory
    fun `root only application does not opt children into rules`() = versions.flatMap { version ->
        modes.map { isolated ->
            DynamicTest.dynamicTest("root only Gradle $version isolated=$isolated") {
                val fixture = Fixture(File(dir, "root-only-$version-$isolated"), version, isolated)
                fixture.settings("child")
                fixture.write("build.gradle", fixture.consumer(".", true))
                fixture.write("child/build.gradle", """
                    plugins { id 'java' }
                    repositories { maven { url = uri('../repo') } }
                    dependencies { implementation 'probe:sub:1' }
                    tasks.register('resolveRules', Sync) {
                        from configurations.compileClasspath
                        into layout.buildDirectory.dir('resolved')
                    }
                """.trimIndent())
                val tasks = arrayOf(":resolveRules", ":child:resolveRules")
                val first = fixture.run(*tasks)
                fixture.stored(first)
                fixture.artifacts("", fixture.expected())
                fixture.artifacts("child", setOf("sub-1.jar"))
                val second = fixture.run(*tasks)
                fixture.reused(second)
                fixture.artifacts("", fixture.expected())
                fixture.artifacts("child", setOf("sub-1.jar"))
            }
        }
    }

    private class Fixture(
        val root: File,
        val version: String,
        val isolated: Boolean,
        val watchFs: Boolean = false,
        val projectLocal: Boolean = true,
        val programmatic: Boolean = false
    ) {
        init {
            root.mkdirs()
            listOf("old", "replacement", "sub", "target", "rejected", "excluded").forEach {
                module("probe", it, "1")
                module("probe", it, "2")
            }
            listOf("one", "two").forEach {
                module("probe.align", it, "1")
                module("probe.align", it, "2")
            }
            write("rules.json", """
                {
                  "replace":[{"module":"probe:old","with":"probe:replacement",$metadata}],
                  "substitute":[{"module":"probe:sub","with":"probe:target:1",$metadata}],
                  "reject":[{"module":"probe:rejected:1",$metadata}],
                  "exclude":[{"module":"probe:excluded",$metadata}],
                  "align":[{"group":"probe.align",$metadata}]
                }
            """.trimIndent())
        }

        fun settings(vararg projects: String) = write("settings.gradle", """
            rootProject.name = 'isolation-assessment'
            ${projects.joinToString("\n") { "include '$it'" }}
        """.trimIndent())

        fun producer() = """
            plugins { id 'com.netflix.nebula.resolution-rules' }
            dependencies { resolutionRules files('rules.json') }
        """.trimIndent()

        fun consumer(relativeRoot: String, ownsRules: Boolean, selection: String = "", pluginFirst: Boolean = false) = """
            ${if (programmatic) """
                plugins { id 'com.netflix.nebula.resolution-rules' apply false }
                ${if (!pluginFirst) "apply plugin: 'java'" else ""}
                nebula.plugin.resolutionrules.ResolutionRulesPlugin.applyWithIsolatedState(project)
                nebula.plugin.resolutionrules.ResolutionRulesPlugin.applyWithIsolatedState(project)
                apply plugin: 'com.netflix.nebula.resolution-rules'
                ${if (pluginFirst) "apply plugin: 'java'" else ""}
            """.trimIndent() else "plugins { ${if (pluginFirst) "id 'com.netflix.nebula.resolution-rules'; id 'java'" else "id 'java'; id 'com.netflix.nebula.resolution-rules'"} }"}
            repositories { maven { url = uri('$relativeRoot/repo') } }
            dependencies {
                ${if (ownsRules) "resolutionRules files('rules.json')" else ""}
                implementation 'probe:old:1', 'probe:replacement:2', 'probe:sub:1',
                    'probe:rejected:1', 'probe:rejected:2', 'probe:excluded:1',
                    'probe.align:one:1', 'probe.align:two:2'
            }
            $selection
            tasks.register('resolveRules', Sync) {
                from configurations.compileClasspath
                into layout.buildDirectory.dir('resolved')
            }
            tasks.register('resolveRuntimeRules', Sync) {
                from configurations.runtimeClasspath
                into layout.buildDirectory.dir('runtimeResolved')
            }
        """.trimIndent()

        fun verifyCacheAndInvalidation(projects: List<String>, excluded: Set<String> = emptySet()) {
            val tasks = projects.flatMap { path ->
                listOf("${taskPrefix(path)}:resolveRules", "${taskPrefix(path)}:resolveRuntimeRules")
            }.toTypedArray()
            fun verify(targetVersion: String, excludeAll: Boolean = false) {
                projects.forEach { path ->
                    val expected = if (excludeAll || path in excluded) unmodified else expected(targetVersion)
                    artifacts(path, expected)
                    artifacts(path, expected, "runtimeResolved")
                }
            }
            val first = run(*tasks)
            stored(first)
            tasks.forEach { assertEquals(TaskOutcome.SUCCESS, first.task(it)?.outcome, first.output) }
            verify("1")
            val second = run(*tasks)
            reused(second)
            verify("1")

            val rules = File(root, "rules.json")
            rules.writeText(rules.readText().replace("probe:target:1", "probe:target:2") + "\n")
            val changedFile = run(*tasks)
            verify("2")
            stored(changedFile)
            assertFalse(changedFile.output.contains("Reusing configuration cache"), changedFile.output)
            reused(run(*tasks))
            verify("2")

            val changedProperty = run(*tasks, "-PnebulaResolutionRules.exclude=rules")
            stored(changedProperty)
            assertFalse(changedProperty.output.contains("Reusing configuration cache"), changedProperty.output)
            verify("2", true)
            reused(run(*tasks, "-PnebulaResolutionRules.exclude=rules"))
            verify("2", true)
        }

        fun expected(targetVersion: String = "1") = setOf(
            "replacement-2.jar", "target-$targetVersion.jar", "rejected-2.jar", "one-2.jar", "two-2.jar"
        )

        fun artifacts(path: String, expected: Set<String>, output: String = "resolved") =
            assertEquals(expected, File(root, "${if (path.isEmpty()) "" else "$path/"}build/$output").list()?.toSet(), path)

        fun stored(result: BuildResult) {
            assertTrue(result.output.contains("Configuration cache entry stored"), result.output)
        }

        fun reused(result: BuildResult) {
            assertTrue(result.output.contains("Reusing configuration cache"), result.output)
            assertTrue(result.output.contains("Configuration cache entry reused"), result.output)
        }

        fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

        fun fails(vararg arguments: String): BuildResult = runner(*arguments).buildAndFail()

        private fun runner(vararg arguments: String): GradleRunner {
            val isolationFlags = if (!isolated) emptyList() else if (
                GradleVersion.version(version) >= GradleVersion.version("9.7")
            ) listOf("--isolated-projects") else listOf("-Dorg.gradle.unsafe.isolated-projects=true")
            val localRulesFlags = if (projectLocal && !programmatic) listOf("-PnebulaResolutionRules.isolatedState=true") else emptyList()
            return GradleRunner.create().withProjectDir(root).withPluginClasspath().withGradleVersion(version)
                .withArguments(arguments.toList() + listOf(
                    "--configuration-cache", "--configuration-cache-problems=fail", "--parallel",
                    "--rerun-tasks", "--stacktrace", "--max-workers=2", if (watchFs) "--watch-fs" else "--no-watch-fs"
                ) + isolationFlags + localRulesFlags)
        }

        fun write(path: String, content: String) {
            File(root, path).apply { parentFile.mkdirs(); writeText(content) }
        }

        fun archive(path: String, entries: Map<String, String>) {
            ZipOutputStream(File(root, path).outputStream()).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }

        private fun module(group: String, name: String, version: String) {
            val location = "repo/${group.replace('.', '/')}/$name/$version"
            write("$location/$name-$version.pom", """
                <project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version></project>
            """.trimIndent())
            JarOutputStream(File(root, "$location/$name-$version.jar").outputStream()).close()
        }

        private fun taskPrefix(path: String) = if (path.isEmpty()) "" else ":${path.replace('/', ':')}"

        companion object {
            const val metadata = "\"reason\":\"assessment\",\"author\":\"assessment\",\"date\":\"2026-11-04\""
            private val unmodified = setOf(
                "old-1.jar", "replacement-2.jar", "sub-1.jar", "rejected-2.jar", "excluded-1.jar", "one-1.jar", "two-2.jar"
            )
        }
    }
}
