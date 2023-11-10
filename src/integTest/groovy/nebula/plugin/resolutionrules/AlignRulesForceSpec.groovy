package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class AlignRulesForceSpec extends AbstractAlignRulesSpec {
    def setup() {
        keepFiles = true
    }

    @Unroll
    def 'alignment uses #name forced version'() {
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
                implementation 'test.nebula.other:a:1.0.0'
            }
            $force
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', '-s']
        def result = runTasks(*tasks)


        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 0.15.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0 -> 0.15.0\n'
        result.output.contains '+--- test.nebula:c:0.15.0\n'
        result.output.contains '--- test.nebula.other:a:1.0.0\n'

        where:
        name            | force
       "all"           | "configurations.all { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
       "configuration" | "configurations.compileClasspath { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
    }

    @Unroll
    def 'when multiple forces are present then Core alignment fails due to multiple forces'() {
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
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:2.0.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:2.0.0'
                force 'test.nebula:b:1.0.0'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none')
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none')

        then:
        assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:2.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:1.0.0')
    }

    @Unroll
    def 'when dynamic forces are present then Core alignment fails due to multiple forces'() {
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
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:2.0.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none']
        def result = runTasks(*tasks)
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none')

        then:
        assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:2.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:1.0.0')

    }

    @Unroll
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
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none']
        def result = runTasks(*tasks)

        then:
        result.output.contains '+--- test.nebula:a:2.0.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0 -> 2.0.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0 -> 2.0.0\n'

    }

    @Unroll
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
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:1.+'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none']
        def result = runTasks(*tasks)


        then:

        result.output.contains '+--- test.nebula:a:2.0.0 -> 1.0.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0 -> 1.0.0\n'
    }

    @Unroll
    def 'with multiple specific dynamic versions then Core alignment fails due to multiple forces'() {
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
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:[1.0, 2.0)'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none']
        def result = runTasks(*tasks)
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none')


        then:
        assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:1.0.0')
        assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:0.15.0')
    }
}
