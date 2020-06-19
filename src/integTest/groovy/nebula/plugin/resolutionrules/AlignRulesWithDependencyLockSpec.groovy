package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class AlignRulesWithDependencyLockSpec extends IntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")
        debug = true
    }

    @Unroll
    def 'dependency locks are honored over alignment rules'() {
        def lock = new File(projectDir, 'dependencies.lock')
        lock << """\
                {
                    "compileClasspath": {
                        "test.nebula:a": {
                            "locked": "1.41.5"
                        },
                        "test.nebula:b": {
                            "locked": "1.42.2"
                        }
                    },
                    "runtimeClasspath": {
                        "test.nebula:a": {
                            "locked": "1.41.5"
                        },
                        "test.nebula:b": {
                            "locked": "1.42.2"
                        }
                    }
                }
        """
        rulesJsonFile << alignTestNebulaRule()
        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:9.0.0'
                }
            }

            plugins {
                id 'nebula.resolution-rules'
                id 'java'
            }
            apply plugin: 'nebula.dependency-lock'

            repositories {
                maven { url '${getMavenRepo().toURI().toURL()}' }              
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def compileClasspathResult = runTasks('dependencyInsight', '--dependency', 'test.nebula:a', '--configuration', 'compileClasspath', '--refresh-dependencies')

        then:
        // final results where locks win over new alignment rules
        compileClasspathResult.output.contains 'Selected by rule : aligned to 1.42.2 by rule rules aligning group \'test.nebula\''
        compileClasspathResult.output.contains 'Selected by rule : test.nebula:a locked to 1.41.5'
        compileClasspathResult.output.contains 'test.nebula:a:1.41.5\n'
    }

    private static String alignTestNebulaRule() {
        """
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
        """.stripIndent()
    }

    private File getMavenRepo() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.41.5')
                .addModule('test.nebula:a:1.42.2')
                .addModule('test.nebula:b:1.41.5')
                .addModule('test.nebula:b:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "$projectDir/testrepogen")
        mavenrepo.generateTestMavenRepo()
    }
}
