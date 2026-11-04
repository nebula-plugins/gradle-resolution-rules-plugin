package nebula.plugin.resolutionrules

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarOutputStream

internal class ResolutionRulesServiceTest {
    @TempDir lateinit var dir: File

    private fun runner(vararg tasks: String) = GradleRunner.create()
        .withProjectDir(dir).withPluginClasspath()
        .withArguments(*tasks, "--configuration-cache", "--configuration-cache-problems=fail", "--parallel", "--rerun-tasks", "--stacktrace")

    private fun module(group: String, name: String, version: String) {
        val location = File(dir, "repo/${group.replace('.', '/')}/$name/$version").apply { mkdirs() }
        File(location, "$name-$version.pom").writeText("""
            <project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version></project>
        """.trimIndent())
        JarOutputStream(File(location, "$name-$version.jar").outputStream()).close()
    }

    @Test fun `configuration cache reuses correct rules on multiple configurations`() {
        File(dir, "settings.gradle").writeText("rootProject.name = 'cc-probe'")
        listOf("old", "replacement", "sub", "target", "rejected", "excluded").forEach {
            module("probe", it, "1"); module("probe", it, "2")
        }
        listOf("one", "two").forEach {
            module("probe.align", it, "1"); module("probe.align", it, "2")
        }
        val ruleFile = File(dir, "rules.json")
        ruleFile.writeText("""
            {
              "replace": [{"module":"probe:old","with":"probe:replacement","reason":"review","author":"review","date":"2026-11-03"}],
              "substitute": [{"module":"probe:sub","with":"probe:target:1","reason":"review","author":"review","date":"2026-11-03"}],
              "reject": [{"module":"probe:rejected:1","reason":"review","author":"review","date":"2026-11-03"}],
              "exclude": [{"module":"probe:excluded","reason":"review","author":"review","date":"2026-11-03"}],
              "deny": [{"module":"probe:denied","reason":"review","author":"review","date":"2026-11-03"}],
              "align": [{"group":"probe.align","reason":"review","author":"review","date":"2026-11-03"}]
            }
        """.trimIndent())
        File(dir, "build.gradle").writeText("""
            plugins { id 'java'; id 'com.netflix.nebula.resolution-rules' }
            repositories { maven { url = uri('repo') } }
            configurations { alpha; beta }
            dependencies { resolutionRules files('rules.json') }
            [configurations.alpha, configurations.beta].each { config ->
                ['probe:old:1', 'probe:replacement:2', 'probe:sub:1', 'probe:rejected:1', 'probe:rejected:2', 'probe:excluded:1', 'probe.align:one:1', 'probe.align:two:2'].each {
                    dependencies.add(config.name, it)
                }
            }
            tasks.register('resolveAlpha', Sync) { from configurations.alpha; into layout.buildDirectory.dir('alpha') }
            tasks.register('resolveBeta', Sync) { from configurations.beta; into layout.buildDirectory.dir('beta') }
        """.trimIndent())
        val expected = setOf("replacement-2.jar", "target-1.jar", "rejected-2.jar", "one-2.jar", "two-2.jar")
        val first = runner("resolveAlpha", "resolveBeta").build()
        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        listOf("alpha", "beta").forEach { assertEquals(expected, File(dir, "build/$it").list()!!.toSet()) }
        val second = runner("resolveAlpha", "resolveBeta").build()
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
        listOf("alpha", "beta").forEach { assertEquals(expected, File(dir, "build/$it").list()!!.toSet()) }
        ruleFile.writeText(ruleFile.readText().replace("probe:target:1", "probe:target:2"))
        val changed = runner("resolveAlpha", "resolveBeta").build()
        assertFalse(changed.output.contains("Reusing configuration cache"), changed.output)
        listOf("alpha", "beta").forEach { assertEquals(expected - "target-1.jar" + "target-2.jar", File(dir, "build/$it").list()!!.toSet()) }
    }

    @Test fun `rule selections and replacement application are isolated by project`() {
        File(dir, "settings.gradle").writeText("rootProject.name = 'project-scope'; include 'a', 'alsoA', 'b'")
        listOf("a", "alsoA", "b").forEach { File(dir, it).mkdirs() }
        listOf("old", "sub", "a", "b").forEach { module("probe", it, "1") }
        listOf("a", "b").forEach { name ->
            File(dir, "$name.json").writeText("""
                {
                  "replace":[{"module":"probe:old","with":"probe:$name","reason":"review","author":"review","date":"2026-11-03"}],
                  "substitute":[{"module":"probe:sub","with":"probe:$name:1","reason":"review","author":"review","date":"2026-11-03"}]
                }
            """.trimIndent())
        }
        File(dir, "build.gradle").writeText("""
            plugins { id 'com.netflix.nebula.resolution-rules' }
            dependencies { resolutionRules files('a.json', 'b.json') }
            subprojects {
                apply plugin: 'com.netflix.nebula.resolution-rules'
                repositories { maven { url = rootProject.uri('repo') } }
                def selected = name == 'alsoA' ? 'a' : name
                nebulaResolutionRules.include = [selected]
                configurations { first; second }
                [configurations.first, configurations.second].each { config ->
                    dependencies.add(config.name, 'probe:old:1')
                    dependencies.add(config.name, 'probe:sub:1')
                    dependencies.add(config.name, "probe:${'$'}selected:1")
                }
                tasks.register('resolveFirst', Sync) {
                    from configurations.first
                    into layout.buildDirectory.dir('first')
                }
                tasks.register('resolveSecond', Sync) {
                    from configurations.second
                    into layout.buildDirectory.dir('second')
                }
            }
        """.trimIndent())
        val tasks = arrayOf(":a:resolveFirst", ":a:resolveSecond", ":alsoA:resolveFirst", ":alsoA:resolveSecond", ":b:resolveFirst", ":b:resolveSecond")
        repeat(2) { run ->
            val result = runner(*tasks).build()
            if (run == 1) assertTrue(result.output.contains("Reusing configuration cache"), result.output)
            listOf("a", "alsoA", "b").forEach { project ->
                val selected = if (project == "alsoA") "a" else project
                listOf("first", "second").forEach { config ->
                    assertEquals(setOf("$selected-1.jar"), File(dir, "$project/build/$config").list()!!.toSet())
                }
            }
        }
    }
}
