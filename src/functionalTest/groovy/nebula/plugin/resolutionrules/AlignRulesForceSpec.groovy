package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class AlignRulesForceSpec extends AbstractAlignRulesSpec {
    def 'alignment uses forced version, rather than highest version, when a force is present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .addModule('test.nebula.other:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.0.0'
                compile 'test.nebula:c:0.15.0'
                compile 'test.nebula.other:a:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains 'Found force(s) [test.nebula:a:0.15.0] that supersede resolution ruleset alignment-uses-forced-version-rather-than-highest-version-when-a-force-is-present align rule [group: test.nebula]. Will use 0.15.0 instead of 1.0.0'
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:b:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:c:0.15.0\n'
        result.standardOutput.contains '\\--- test.nebula.other:a:1.0.0\n'
    }

    def 'alignment uses lowest forced version, when multiple forces are present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.00.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:2.0.0'
                compile 'test.nebula:c:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:2.0.0'
                force 'test.nebula:b:1.0.0'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:2.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:b:2.0.0 -> 0.15.0\n'
        result.standardOutput.contains '\\--- test.nebula:c:1.0.0 -> 0.15.0\n'
    }

    def 'alignment outputs warnings and honors static force when dynamic forces are present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.00.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:2.0.0'
                compile 'test.nebula:c:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains('Resolution rules ruleset alignment-outputs-warnings-and-honors-static-force-when-dynamic-forces-are-present align rule [group: test.nebula] is unable to honor forced versions [latest.release, 1.+]. For a force to take precedence on an align rule, it must use a static version')
        standardOutput.contains '+--- test.nebula:a:2.0.0 -> 0.15.0\n'
        standardOutput.contains '+--- test.nebula:b:2.0.0 -> 0.15.0\n'
        standardOutput.contains '\\--- test.nebula:c:1.0.0 -> 0.15.0\n'
    }

    def 'alignment outputs warnings and falls back to default logic, when only dynamic forces are present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:1.0.0'
                compile 'test.nebula:c:0.15.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:2.+'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains('Resolution rules ruleset alignment-outputs-warnings-and-falls-back-to-default-logic-when-only-dynamic-forces-are-present align rule [group: test.nebula] is unable to honor forced versions [latest.release, 1.+, 2.+]. For a force to take precedence on an align rule, it must use a static version')
        standardOutput.contains('No static forces found for ruleset alignment-outputs-warnings-and-falls-back-to-default-logic-when-only-dynamic-forces-are-present align rule [group: test.nebula]. Falling back to default alignment logic')
        standardOutput.contains '+--- test.nebula:a:2.0.0\n'
        standardOutput.contains '+--- test.nebula:b:1.0.0 -> 2.0.0\n'
        standardOutput.contains '\\--- test.nebula:c:0.15.0 -> 2.0.0\n'
    }
}
