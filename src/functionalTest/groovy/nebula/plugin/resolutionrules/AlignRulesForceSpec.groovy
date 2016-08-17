package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class AlignRulesForceSpec extends AbstractAlignRulesSpec {
    @Unroll("alignment uses #name forced version")
    def 'alignment uses forced version'() {
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
            $force
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains 'Found force(s) [test.nebula:a:0.15.0] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-uses-forced-version, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z). Will use 0.15.0 instead of 1.0.0'
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:b:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:c:0.15.0\n'
        result.standardOutput.contains '--- test.nebula.other:a:1.0.0\n'

        where:
        name << ["all", "configuration", "dependency"]
        force << [
                "configurations.all { resolutionStrategy { force 'test.nebula:a:0.15.0' } }",
                "configurations.compile { resolutionStrategy { force 'test.nebula:a:0.15.0' } }",
                "dependencies { compile ('test.nebula:a:0.15.0') { force = true } }"
        ]
    }

    def 'alignment uses lowest forced version, when multiple forces are present'() {
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
        standardOutput.contains('Found force(s) [test.nebula:a:latest.release, test.nebula:b:1.+, test.nebula:c:0.15.0] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-outputs-warnings-and-honors-static-force-when-dynamic-forces-are-present, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z). Will use 0.15.0 instead of 2.0.0')
        standardOutput.contains '+--- test.nebula:a:2.0.0 -> 0.15.0\n'
        standardOutput.contains '+--- test.nebula:b:2.0.0 -> 0.15.0\n'
        standardOutput.contains '\\--- test.nebula:c:1.0.0 -> 0.15.0\n'
    }

    def 'alignment with latest.release force'() {
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
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains 'Found force(s) [test.nebula:a:latest.release] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-with-latest-release-force, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z). Will use highest dynamic version 2.0.0 that matches most selective selector latest.release'
        standardOutput.contains '+--- test.nebula:a:2.0.0\n'
        standardOutput.contains '+--- test.nebula:b:1.0.0 -> 2.0.0\n'
        standardOutput.contains '\\--- test.nebula:c:0.15.0 -> 2.0.0\n'
    }

    def 'alignment with sub-version force'() {
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
                force 'test.nebula:a:1.+'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains 'Found force(s) [test.nebula:a:1.+] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-with-sub-version-force, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z). Will use highest dynamic version 1.0.0 that matches most selective selector 1.+'
        standardOutput.contains '+--- test.nebula:a:2.0.0 -> 1.0.0\n'
        standardOutput.contains '+--- test.nebula:b:1.0.0\n'
        standardOutput.contains '\\--- test.nebula:c:0.15.0 -> 1.0.0\n'
    }

    def 'alignment uses most selective dynamic version'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:3.0.0')
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
                force 'test.nebula:c:[1.0, 2.0)'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains 'Found force(s) [test.nebula:a:latest.release, test.nebula:b:1.+, test.nebula:c:[1.0, 2.0)] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-uses-most-selective-dynamic-version, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z). Will use highest dynamic version 1.0.0 that matches most selective selector [1.0, 2.0)'
        standardOutput.contains '+--- test.nebula:a:2.0.0 -> 1.0.0\n'
        standardOutput.contains '+--- test.nebula:b:1.0.0\n'
        standardOutput.contains '\\--- test.nebula:c:0.15.0 -> 1.0.0\n'
    }
}
