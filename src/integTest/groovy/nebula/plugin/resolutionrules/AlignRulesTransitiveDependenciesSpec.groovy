package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Issue

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
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains '\\--- test.other:c:1.0.0\n'
        result.output.contains '     \\--- test.nebula:b:1.1.0\n'
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
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains '\\--- test.other:d:1.0.0\n'
        result.output.contains '     \\--- test.other:c:1.0.0\n'
        result.output.contains '          \\--- test.nebula:b:1.1.0\n'
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
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula:a:1.1.0\n'
        result.output.contains '|    \\--- test.other:b:1.0.0\n'
        result.output.contains '|         \\--- test.nebula:b:1.0.0 -> 1.1.0\n'
        result.output.contains '|              \\--- test.other:b:1.0.0 (*)\n'
        result.output.contains '\\--- test.nebula:b:1.0.0 -> 1.1.0 (*)\n'
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
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '|    \\--- test.nebula:b:1.0.0\n'
        result.output.contains '\\--- test.nebula:b -> 1.0.0\n'
    }

    @Issue('#48')
    def 'transitive dependencies with alignment are aligned, when parent dependency is also aligned'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula.a:a1:1.0.0').addDependency('test.nebula.b:b1:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula.a:a1:2.0.0').addDependency('test.nebula.b:b1:2.0.0').build())
                .addModule('test.nebula.a:a2:1.0.0')
                .addModule('test.nebula.a:a2:2.0.0')
                .addModule('test.nebula.a:a3:1.0.0')
                .addModule('test.nebula.a:a3:2.0.0')
                .addModule('test.nebula.b:b1:1.0.0')
                .addModule('test.nebula.b:b1:2.0.0')
                .addModule(new ModuleBuilder('test.nebula.b:b2:1.0.0').addDependency('test.nebula.a:a3:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula.b:b2:2.0.0').addDependency('test.nebula.a:a3:1.0.0').build())
                .addModule('test.nebula.c:c1:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebulaA",
                        "group": "test.nebula.a",
                        "reason": "Align test.nebula.a dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "name": "testNebulaB",
                        "group": "test.nebula.b",
                        "reason": "Align test.nebula.b dependencies",
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

            // Make at least one of the dependencies a non-expected dependency
            configurations.compile {
                resolutionStrategy {
                    force 'test.nebula.c:c1:1.0.0'
                }
            }

            dependencies {
                compile 'test.nebula.a:a1:1.+'
                compile 'test.nebula.a:a2:latest.release'
                compile 'test.nebula.b:b2:1.0.0'
                compile 'test.nebula.c:c1:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula.a:a1:1.+ -> 2.0.0\n'
        result.output.contains '|    \\--- test.nebula.b:b1:2.0.0\n'
        result.output.contains '+--- test.nebula.a:a2:latest.release -> 2.0.0\n'
        result.output.contains '+--- test.nebula.b:b2:1.0.0 -> 2.0.0\n'
        result.output.contains '\\--- test.nebula.a:a3:1.0.0 -> 2.0.0\n'
        result.output.contains '\\--- test.nebula.c:c1:1.0.0'
    }

    def 'can align a transitive dependency with multiple versions contributed transitively'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.nebula:d:2.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('test.nebula:d:3.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0').addDependency('test.nebula:d:1.0.0').build())
                .addModule('test.nebula:d:1.0.0')
                .addModule('test.nebula:d:2.0.0')
                .addModule('test.nebula:d:3.0.0')
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
                compile 'test.nebula:c:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '\\--- test.nebula:d:3.0.0\n'
    }

    def 'can align a transitive dependency with direct and use substitution to downgrade'() {
        given:
        debug = true
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.1.0').addDependency('test.nebula:b:1.1.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.2.0').addDependency('test.nebula:b:1.2.0').build())
                .build()

        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [],
                "substitute": [
                    {
                        "module": "test.nebula:a:[1.2.0,)",
                        "with": "test.nebula:a:1.1.0",
                        "reason": "Downgrade",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:b:[1.2.0,)",
                        "with": "test.nebula:b:1.1.0",
                        "reason": "Downgrade",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
             
                ], "replace": [],
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
                compile 'test.nebula:b:1.2.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '\\--- test.nebula:b:1.2.0 -> 1.1.0'
    }
}
