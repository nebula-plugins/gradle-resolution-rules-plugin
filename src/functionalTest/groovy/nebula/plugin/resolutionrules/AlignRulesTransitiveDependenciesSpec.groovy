package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class AlignRulesTransitiveDependenciesSpec extends AbstractAlignRulesSpec {
    def 'can align transitive dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:b:1.1.0').build())
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
                compile 'test.other:c:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '\\--- test.other:c:1.0.0\n'
        result.standardOutput.contains '     \\--- test.nebula:b:1.1.0\n'
    }

    def 'can align deeper transitive dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .addModule(new ModuleBuilder('test.other:d:1.0.0').addDependency('test.other:c:1.0.0').build())
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
                compile 'test.other:d:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '\\--- test.other:d:1.0.0\n'
        result.standardOutput.contains '     \\--- test.other:c:1.0.0\n'
        result.standardOutput.contains '          \\--- test.nebula:b:1.1.0\n'
    }

    def 'dependencies with cycles do not lead to infinite loops'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.other:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.other:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.1.0').addDependency('test.other:b:1.0.0').build())
                .addModule('test.nebula:b:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.1.0').addDependency('test.other:b:1.0.0').build())
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
                compile 'test.nebula:a:1.1.0'
                compile 'test.nebula:b:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.1.0\n'
        result.standardOutput.contains '|    \\--- test.other:b:1.0.0\n'
        result.standardOutput.contains '|         \\--- test.nebula:b:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '|              \\--- test.other:b:1.0.0 (*)\n'
        result.standardOutput.contains '\\--- test.nebula:b:1.0.0 -> 1.1.0 (*)\n'
    }

    def 'able to omit dependency versions to take what is given transitively'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule('test.nebula:b:1.0.0')
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
                compile 'test.nebula:b'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '|    \\--- test.nebula:b:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:b: -> 1.0.0\n'
    }
}
