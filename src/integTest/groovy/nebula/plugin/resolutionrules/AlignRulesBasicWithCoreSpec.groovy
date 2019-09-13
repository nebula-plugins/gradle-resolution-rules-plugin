package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.util.GradleVersion
import spock.lang.Unroll

class AlignRulesBasicWithCoreSpec extends IntegrationTestKitSpec {
    private def rulesJsonFile

    def setup() {
        debug = true
        keepFiles = true
        new File("${projectDir}/gradle.properties").text = "systemProp.nebula.features.coreAlignmentSupport=true"
        if (GradleVersion.current().baseVersion < GradleVersion.version("6.0")) {
            settingsFile << '''\
                enableFeaturePreview("GRADLE_METADATA")
            '''.stripIndent()
        }

        rulesJsonFile = new File(projectDir, "rules.json")
        rulesJsonFile.createNewFile()

        buildFile << """\
            plugins {
                id 'nebula.resolution-rules'
                id 'java'
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        settingsFile << """\
            rootProject.name = '${moduleName}'
        """.stripIndent()
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

        rulesJsonFile << alignTestNebulaRule()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
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
        result.output.contains 'belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:1.1.0'
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

        rulesJsonFile << alignTestNebulaRule()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                implementation 'test.other:brings-a:latest.release'
                implementation 'test.other:also-brings-a:latest.release'
                implementation 'test.other:brings-b:latest.release'
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
                implementation 'test.other:c:1.0.0'
                implementation 'test.other:d:0.12.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

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

    @Unroll
    def 'core alignment uses versions observed during resolution'() {
        // test case from https://github.com/nebula-plugins/gradle-nebula-integration/issues/52
        // higher version transitive aligning parent dependency
        given:
        rulesJsonFile << """
            {
                "align": [
                    {
                        "name": "exampleapp-client-align",
                        "group": "test.nebula",
                        "includes": [ "exampleapp-.*" ],
                        "excludes": [],
                        "reason": "Library all together",
                        "author": "example@example.com",
                        "date": "2018-03-01"
                    }
                ],
                "deny": [],
                "exclude": [],
                "reject": [],
                "replace": [],
                "substitute": []
            }
            """.stripIndent()

        def mavenrepo = createDependenciesForExampleAppDependencies()

        buildFile << """
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                implementation 'test.nebula:exampleapp-client:80.0.139'
            }
            """.stripIndent()
        when:
        def dependenciesResult = runTasks('dependencies', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:exampleapp-client", resultingVersion)

        if (coreAlignment) {
            // Nebula implementation attempts to resolve multiple times which introduces new versions
            assert dependenciesResult.output.contains("""
            \\--- test.nebula:exampleapp-client:80.0.139 -> 80.0.225
                 +--- test.nebula:exampleapp-common:80.0.249
                 \\--- test.nebula:exampleapp-smart-client:80.0.10
            """.stripIndent())
        } else {
            // Gradle implementation only considers versions currently observed during resolution
            assert dependenciesResult.output.contains("""
            \\--- test.nebula:exampleapp-client:80.0.139 -> 80.0.236
                 +--- test.nebula:exampleapp-common:80.0.260
                 \\--- test.nebula:exampleapp-smart-client:80.0.21
                      \\--- test.nebula:exampleapp-model:80.0.15
            """.stripIndent())
        }

        where:
        coreAlignment | resultingVersion
        false         | "80.0.236"
        true          | "80.0.225"
    }

    private static def tasks(Boolean usingCoreAlignment, Boolean usingCoreBomSupport = false, String groupForInsight = 'test.nebula') {
        return [
                'dependencyInsight',
                '--dependency',
                groupForInsight,
                "-Dnebula.features.coreAlignmentSupport=$usingCoreAlignment",
                "-Dnebula.features.coreBomSupport=$usingCoreBomSupport"
        ]
    }

    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }

    private static String alignTestNebulaRule() {
        return '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "(test.nebula|test.nebula.ext)",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()
    }

    private GradleDependencyGenerator createDependenciesForExampleAppDependencies() {
        def client = 'test.nebula:exampleapp-client'
        def common = 'test.nebula:exampleapp-common'
        def model = 'test.nebula:exampleapp-model'
        def smartClient = 'test.nebula:exampleapp-smart-client'
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder("$client:80.0.139")
                        .addDependency("$common:80.0.154")
                        .build())
                .addModule(new ModuleBuilder("$client:80.0.154")
                        .addDependency("$common:80.0.177")
                        .build())
                .addModule(new ModuleBuilder("$client:80.0.177")
                        .addDependency("$common:80.0.201")
                        .build())
                .addModule(new ModuleBuilder("$client:80.0.201")
                        .addDependency("$common:80.0.225")
                        .build())
                .addModule(new ModuleBuilder("$client:80.0.225")
                        .addDependency("$common:80.0.249")
                        .addDependency("$smartClient:80.0.10")
                        .build())
                .addModule(new ModuleBuilder("$client:80.0.236")
                        .addDependency("$common:80.0.260")
                        .addDependency("$smartClient:80.0.21")
                        .build())

                .addModule("$common:80.0.154")
                .addModule("$common:80.0.177")
                .addModule("$common:80.0.201")
                .addModule("$common:80.0.225")
                .addModule("$common:80.0.249")
                .addModule("$common:80.0.260")

                .addModule("$model:80.0.15")

                .addModule("$smartClient:80.0.10")
                .addModule(new ModuleBuilder("$smartClient:80.0.21")
                        .addDependency("$model:80.0.15")
                        .build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()
        return mavenrepo
    }
}
