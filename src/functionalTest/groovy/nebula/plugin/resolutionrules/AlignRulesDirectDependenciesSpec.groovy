package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.internal.impldep.com.amazonaws.util.Throwables
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import spock.lang.Unroll

class AlignRulesDirectDependenciesSpec extends AbstractAlignRulesSpec {
    def 'can align direct dependencies if necessary'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
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
                compile 'test.nebula:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    def 'can align direct dependencies from ivy repositories'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .build()
        GradleDependencyGenerator ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        ivyrepo.generateTestIvyRepo()

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
                ${ivyrepo.ivyRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    def 'can align dynamic dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
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
                compile 'test.nebula:a:1.+'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.nebula:a:1.+ -> 1.0.1\n'
    }

    def 'can align dynamic range dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
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
                compile 'test.nebula:a:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.nebula:a:[1.0.0, 2.0.0) -> 1.0.1\n'
    }

    def 'unresolvable dependencies cause assemble to fail'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "com.google.guava",
                        "reason": "Align guava",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories { jcenter() }
            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                compile 'com.google.guava:guava:oops'
            }
        """

        writeHelloWorld('com.netflix.nebula')

        when:
        def result = runTasks('assemble')

        then:
        def cause = Throwables.getRootCause(result.failure)
        cause.message.startsWith("Could not find com.google.guava:guava:oops")
    }

    @Unroll('unresolvable dependencies do not cause #tasks to fail')
    def 'unresolvable dependencies do not cause dependencies tasks to fail'() {
        buildFile.delete()
        buildFile << """\
            apply plugin: 'java'

            repositories { jcenter() }

            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                compile 'com.google.guava:guava:oops'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully(*tasks)

        then:
        noExceptionThrown()

        where:
        tasks | _
        ['dependencies', '--configuration', 'compile'] | _
        ['dependencyInsight', '--dependency', 'guava'] | _
    }

    @Unroll('unresolvable dependencies do not cause #tasks to fail when align rules present')
    def 'unresolvable dependencies do not cause tasks to fail when align rules present'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "com.google.guava",
                        "reason": "Align guava",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories { jcenter() }
            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                compile 'com.google.guava:guava:oops'
            }
        """

        when:
        def result = runTasksSuccessfully(*tasks)

        then:
        noExceptionThrown()
        result.standardOutput.contains("Resolution rules could not resolve all dependencies to align in configuration 'compile' should also fail to resolve")

        where:
        tasks | _
        ['dependencies', '--configuration', 'compile'] | _
        ['dependencyInsight', '--dependency', 'guava'] | _
    }
}
