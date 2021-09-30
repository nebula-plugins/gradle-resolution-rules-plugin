package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Unroll

class AlignRulesVersionSuffixesSpec extends AbstractAlignRulesSpec {
    def setup() {
        debug = true
    }

    @Unroll
    def 'requesting a specific version with no release version available'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0-eap-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0.pr.1').addDependency('test.nebula:a:1.0.0').build())
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
                implementation 'test.nebula:b:1.0.0-1'
                implementation 'test.nebula:c:1.0.0-eap-1'
                implementation 'test.nebula:d:1.0.0.pr.1'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath')
        def insightResults = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        results.output.contains '--- test.nebula:b:1.0.0-1\n'
        results.output.contains '--- test.nebula:c:1.0.0-eap-1\n'
        results.output.contains '--- test.nebula:d:1.0.0.pr.1\n'
        results.output.contains '\\--- test.nebula:a:1.0.0\n'
        assert insightResults.output.contains("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0")
        assert insightResults.output.findAll("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0").size() == 4
    }

    @Unroll
    def 'requesting a specific version with a release version available'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0-eap-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0.pr.1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0').addDependency('test.nebula:a:1.0.0').build())
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
                implementation 'test.nebula:b:1.0.0-1'
                implementation 'test.nebula:c:1.0.0-eap-1'
                implementation 'test.nebula:d:1.0.0.pr.1'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath')
        def insightResults = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        results.output.contains '--- test.nebula:b:1.0.0-1\n'
        results.output.contains '--- test.nebula:c:1.0.0-eap-1 -> 1.0.0\n'
        results.output.contains '--- test.nebula:d:1.0.0.pr.1 -> 1.0.0\n'
        results.output.contains '\\--- test.nebula:a:1.0.0\n'
        assert insightResults.output.contains("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0")
        assert insightResults.output.findAll("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0").size() == 4

    }

    @Unroll
    def 'requesting major.+ with no release version available'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0-eap-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0.pr.1').addDependency('test.nebula:a:1.0.0').build())
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
                implementation 'test.nebula:b:1.+'
                implementation 'test.nebula:c:1.+'
                implementation 'test.nebula:d:1.+'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath')
        def insightResults = runTasks('dependencyInsight', '--dependency', 'test.nebula')
        insightResults.output.contains("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0")
        insightResults.output.findAll("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0").size() == 4

        then:
        results.output.contains '--- test.nebula:b:1.+ -> 1.0.0-1\n'
        results.output.contains '--- test.nebula:c:1.+ -> 1.0.0-eap-1\n'
        results.output.contains '--- test.nebula:d:1.+ -> 1.0.0.pr.1\n'
        results.output.contains '\\--- test.nebula:a:1.0.0\n'
    }

    @Unroll
    def 'requesting major.+ with a release version available'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0-eap-1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:c:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0.pr.1').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:d:1.0.0').addDependency('test.nebula:a:1.0.0').build())
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
                implementation 'test.nebula:b:1.+'
                implementation 'test.nebula:c:1.+'
                implementation 'test.nebula:d:1.+'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath')
        def insightResults = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        results.output.contains '--- test.nebula:b:1.+ -> 1.0.0-1\n'
        results.output.contains '--- test.nebula:c:1.+ -> 1.0.0\n'
        results.output.contains '--- test.nebula:d:1.+ -> 1.0.0\n'
        results.output.contains '\\--- test.nebula:a:1.0.0\n'
        assert insightResults.output.contains("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0")
        assert insightResults.output.findAll("belongs to platform aligned-platform:$moduleName-0-for-test.nebula:1.0.0").size() == 4
    }
}
