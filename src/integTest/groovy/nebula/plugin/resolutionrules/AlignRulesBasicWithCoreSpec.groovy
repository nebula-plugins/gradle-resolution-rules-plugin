package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class AlignRulesBasicWithCoreSpec extends AbstractAlignRulesSpec {

    def setup() {
        debug = true
        new File("${projectDir}/gradle.properties").text = "systemProp.nebula.features.coreAlignmentSupport=true"
        settingsFile << """
        enableFeaturePreview("GRADLE_METADATA")
        """
    }

    def 'align rules and force to latest.release'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.1.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

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
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
            }
            configurations.all {
                resolutionStrategy { 
                    force 'test.nebula:a:latest.release' 
                }
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        result.output.contains 'coreAlignmentSupport feature enabled'
        result.output.contains 'belongs to platform aligned-platform:align-rules-and-force-to-latest-release-0-for-test.nebula:1.1.0'
    }

    def 'align rules and force to latest.release when brought in transitively'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.1.0').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

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
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.other:brings-a:latest.release'
                compile 'test.other:also-brings-a:latest.release'
                compile 'test.other:brings-b:latest.release'
            }
            configurations.all {
                resolutionStrategy { 
                    force 'test.nebula:a:latest.release' 
                }
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        result.output.contains 'coreAlignmentSupport feature enabled'
    }

    def 'multiple align rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.other:c:0.12.2')
                .addModule('test.other:c:1.0.0')
                .addModule('test.other:d:0.12.2')
                .addModule('test.other:d:1.0.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

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
                    },
                    {
                        "name": "testOther",
                        "group": "test.other",
                        "reason": "Aligning test",
                        "author": "Example Tester <test@example.org>",
                        "date": "2016-04-05T19:19:49.495Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
                compile 'test.other:c:1.0.0'
                compile 'test.other:d:0.12.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains 'test.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains 'test.nebula:b:1.1.0\n'
        result.output.contains 'test.other:c:1.0.0\n'
        result.output.contains 'test.other:d:0.12.+ -> 1.0.0\n'

        when:
        result = runTasks('dependencyInsight', '--dependency', 'test.nebula:a')

        then:
        result.output.contains 'coreAlignmentSupport feature enabled'
    }

    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }
}
