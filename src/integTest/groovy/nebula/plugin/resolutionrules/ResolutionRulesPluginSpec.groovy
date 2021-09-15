/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.maven.ArtifactType
import nebula.test.dependencies.maven.Pom
import nebula.test.dependencies.repositories.MavenRepo
import org.codehaus.groovy.runtime.StackTraceUtils
import spock.lang.Issue
import spock.lang.Unroll

/**
 * Functional test for {@link ResolutionRulesPlugin}.
 */
class ResolutionRulesPluginSpec extends IntegrationSpec {
    File rulesJsonFile
    File optionalRulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        optionalRulesJsonFile = new File(projectDir, "optional-${moduleName}.json")

        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'nebula.resolution-rules'

                     repositories {
                         mavenCentral()
                     }

                     dependencies {
                         resolutionRules files("$rulesJsonFile", "$optionalRulesJsonFile")
                     }
                     """.stripIndent()

        rulesJsonFile << """
                        {
                            "align": [
                                {
                                    "name": "testNebula",
                                    "group": "com.google.guava",
                                    "reason": "Align guava",
                                    "author": "Example Person <person@example.org>",
                                    "date": "2016-03-17T20:21:20.368Z"
                                }
                            ],
                            "replace" : [
                                {
                                    "module" : "asm:asm",
                                    "with" : "org.ow2.asm:asm",
                                    "reason" : "The asm group id changed for 4.0 and later",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ],
                            "substitute": [
                                {
                                    "module" : "bouncycastle:bcprov-jdk15",
                                    "with" : "org.bouncycastle:bcprov-jdk15:latest.release",
                                    "reason" : "The latest version of BC is required, using the new coordinate",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ],
                            "deny": [
                                {
                                    "module": "com.google.guava:guava:19.0-rc2",
                                    "reason" : "Guava 19.0-rc2 is not permitted",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                },
                                {
                                    "module": "com.sun.jersey:jersey-bundle",
                                    "reason" : "jersey-bundle is a fat jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ],
                            "reject": [
                                {
                                    "module": "com.google.guava:guava:12.0",
                                    "reason" : "Guava 12.0 significantly regressed LocalCache performance",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ],
                            "exclude": [
                                {
                                    "module": "io.netty:netty-all",
                                    "reason": "Bundle dependencies are harmful, they do not conflict resolve",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ]
                        }
                        """.stripIndent()

        optionalRulesJsonFile << """
                                    {
                                        "substitute" : [
                                            {
                                                "module" : "log4j:log4j",
                                                "with" : "org.slf4j:log4j-over-slf4j:1.7.21",
                                                "reason" : "SLF4J bridge replacement",
                                                "author" : "Danny Thomas <dmthomas@gmail.com>",
                                                "date" : "2015-10-07T20:21:20.368Z"
                                            }
                                        ],
                                        "align": [
                                            {
                                                "group": "org.slf4j",
                                                "reason": "Align SLF4J dependencies",
                                                "author" : "Danny Thomas <dmthomas@gmail.com>",
                                                "date" : "2015-10-07T20:21:20.368Z"
                                            }
                                        ]
                                    }
                                 """
    }

    def 'plugin applies'() {
        when:
        def result = runTasksSuccessfully('help')

        then:
        result.standardError.isEmpty()
    }

    def 'empty configuration'() {
        expect:
        runTasksSuccessfully()
    }

    def 'duplicate rules sources'() {
        def ant = new AntBuilder()
        def rulesJarFile = new File(projectDir, 'rules.jar')
        ant.jar(destfile: rulesJarFile, basedir: rulesJsonFile.parentFile)
        def rulesZipFile = new File(projectDir, 'rules.zip')
        ant.zip(destfile: rulesZipFile, basedir: rulesJsonFile.parentFile)
        buildFile << """
                     dependencies {
                         resolutionRules files("$rulesJarFile")
                         resolutionRules files("$rulesZipFile")

                         implementation 'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        logLevel = logLevel.DEBUG
        def result = runTasksSuccessfully('dependencies')

        then:
        def output = result.standardOutput
        output.contains 'Using duplicate-rules-sources (duplicate-rules-sources.json) a dependency rules source'
        output.contains 'Found rules with the same name. Overriding existing ruleset duplicate-rules-sources'
        output.contains "Using duplicate-rules-sources ($projectDir/rules.jar) a dependency rules source"
        output.contains "Using duplicate-rules-sources ($projectDir/rules.zip) a dependency rules source"
    }

     def 'output ruleset that is being used'() {
        def ant = new AntBuilder()
        def rulesJarFile = new File(projectDir, 'rules.jar')
        ant.jar(destfile: rulesJarFile, basedir: rulesJsonFile.parentFile)
        buildFile << """
                     dependencies {
                         resolutionRules files("$rulesJarFile")

                         implementation 'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--debug')

        then:
        def output = result.standardOutput
        output.contains 'nebula.resolution-rules is using ruleset: rules.jar'
    }

    def 'dependencies task with configuration on demand'() {
        def subproject = addSubproject("subprojectA")
        new File(subproject, "build.gradle") << """
            apply plugin: 'java'
            apply plugin: 'nebula.resolution-rules'
        """.stripIndent()

        when:
        def result = runTasksSuccessfully(':subprojectA:dependencies', '--configuration', 'compileClasspath', '-Dorg.gradle.configureondemand=true')

        then:
        result.standardOutput.contains("Configuration on demand is an incubating feature.")
    }

    def 'replace module'() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'asm:asm:3.3.1'
                        implementation 'org.ow2.asm:asm:5.0.4'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }

    def 'replaced module is shown by dependencyInsight'() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'asm:asm:3.3.1'
                        implementation 'org.ow2.asm:asm:5.0.4'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencyInsight', '--dependency', 'asm')

        then:
        // reasons
        result.standardOutput.contains("replaced asm:asm -> org.ow2.asm:asm because 'The asm group id changed for 4.0 and later' by rule replaced-module-is-shown-by-dependencyInsight")
        result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')

        // final result
        result.standardOutput.findAll('Task.*\n.*org.ow2.asm:asm:5.0.4').size() > 0
    }

    def "module is not replaced if the replacement isn't in the configuration"() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('asm:asm:3.3.1')
        !result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }


    def 'exclude dependency'() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'io.netty:netty-all:5.0.0.Alpha2'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('No dependencies')
    }

    @Issue('#33')
    def 'excludes apply without configuration warnings'() {
        when:
        def result = runTasksSuccessfully('dependencies')

        then:
        !result.standardOutput.contains("Changed dependencies of configuration ':compileOnly' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0. Use 'defaultDependencies' instead of 'befo reResolve' to specify default dependencies for a configuration.")
        !result.standardOutput.contains("Changed dependencies of parent of configuration ':compileClasspath' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0')")
    }


    def 'optional rules are not applied by default'() {
        given:
        buildFile << """
                     dependencies {
                         implementation 'log4j:log4j:1.2.17'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('\\--- log4j:log4j:1.2.17\n')
    }

    def 'optional rules are applied when specified'() {
        given:
        buildFile << """
                     nebulaResolutionRules {
                         optional = ["${moduleName}"]
                     }

                     dependencies {
                         resolutionRules files("$optionalRulesJsonFile")

                         implementation 'log4j:log4j:1.2.17'
                         implementation 'org.slf4j:jcl-over-slf4j:1.7.0'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains '+--- log4j:log4j:1.2.17 -> org.slf4j:log4j-over-slf4j:1.7.21\n'
        result.standardOutput.contains '|    \\--- org.slf4j:slf4j-api:1.7.21\n'
        result.standardOutput.contains '\\--- org.slf4j:jcl-over-slf4j:1.7.0 -> 1.7.21\n'
        result.standardOutput.contains '\\--- org.slf4j:slf4j-api:1.7.21\n'
    }

    def 'only included rules are applied'() {
        given:
        def otherRulesFile = new File(projectDir, "other-${moduleName}.json")
        otherRulesFile << """
                                    {
                                        "substitute" : [
                                            {
                                                "module" : "log4j:log4j",
                                                "with" : "org.slf4j:log4j-over-slf4j:1.7.21",
                                                "reason" : "SLF4J bridge replacement",
                                                "author" : "Danny Thomas <dmthomas@gmail.com>",
                                                "date" : "2015-10-07T20:21:20.368Z"
                                            }
                                        ]
                                    }
                                 """
        buildFile << """
                     nebulaResolutionRules {
                         include = ["other-${moduleName}"]
                     }

                     dependencies {
                         resolutionRules files("$optionalRulesJsonFile", "$otherRulesFile")

                         implementation 'log4j:log4j:1.2.17'
                         implementation 'asm:asm:3.3.1'
                         implementation 'org.ow2.asm:asm:5.0.4'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('log4j:log4j:1.2.17 -> org.slf4j:log4j-over-slf4j:1.7.21')
        !result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }

    @Unroll
    def "deny dependency"() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'com.google.guava:guava:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message.contains("Dependency com.google.guava:guava:19.0-rc2 denied by rule deny-dependency")
    }

    @Unroll
    def "deny dependency without version"() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'com.sun.jersey:jersey-bundle:1.19'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message.contains("Dependency com.sun.jersey:jersey-bundle: denied by rule deny-dependency-without-version")
    }

    def 'reject dependency'() {
        given:
        buildFile << """
                     dependencies {
                        implementation 'com.google.guava:guava:12.0'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains("rejected by component selection rule: rejected by rule reject-dependency because 'Guava 12.0 significantly regressed LocalCache performance'")
    }

    def 'reject dependency with selector'() {
        given:
        rulesJsonFile.delete()
        rulesJsonFile << '''\
        {
            "reject": [
                {
                    "module": "com.google.guava:guava:16.+",
                    "reason" : "Just a Guava release that happens to have a patch release",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                }
            ]
        }
        '''

        buildFile << """
                     dependencies {
                        implementation 'com.google.guava:guava:16.0.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains("rejected by rule reject-dependency-with-selector because 'Just a Guava release that happens to have a patch release'")
    }

    def 'rules apply to detached configurations that have been added to the configurations container'() {
        given:
        buildFile << """
                     task testDetached {
                        doFirst {
                            def detachedConfiguration = configurations.detachedConfiguration(dependencies.create('asm:asm:3.3.1'), dependencies.create('org.ow2.asm:asm:5.0.4'))
                            configurations.add(detachedConfiguration)
                            println "FILES: \${detachedConfiguration.files.size()}"
                        }
                     }
                     """

        when:
        def result = runTasksSuccessfully('testDetached')

        then:
        result.standardOutput.contains('FILES: 1')
    }

    def 'warning logged when configuration has been resolved'() {
        given:
        buildFile.text = """\
             apply plugin: 'java'
             apply plugin: 'nebula.resolution-rules'
             repositories {
                 mavenCentral()
             }
             dependencies {
                 resolutionRules files("$rulesJsonFile", "$optionalRulesJsonFile")
                 
                 implementation 'com.google.guava:guava:19.0'
             }
             
             configurations.compileClasspath.resolve()
             """.stripIndent()

        when:
        def result = runTasksSuccessfully()

        then:
        result.standardOutput.contains("Dependency resolution rules will not be applied to configuration ':compileClasspath', it was resolved before the project was executed")
    }

    def 'warning should not be logged when using nebulaRecommenderBom'() {
        setup:
        def nebulaBomResolutionRulesFile =  new File(projectDir, "nebulaRecommenderBom-test-rules.json")
        nebulaBomResolutionRulesFile << """
                        {
                            "align": [
                                {
                                    "name": "foo",
                                    "group": "example",
                                    "reason": "Align foo",
                                    "author": "Example Person <person@example.org>",
                                    "date": "2018-02-17T20:21:20.368Z"
                                },
                                {
                                    "name": "bar",
                                    "group": "example",
                                    "reason": "Align bar",
                                    "author": "Example Person <person@example.org>",
                                    "date": "2018-02-17T20:21:20.368Z"
                                }
                            ]
                        }
                        """.stripIndent()
        MavenRepo repo = new MavenRepo()
        repo.root = new File(projectDir, 'build/bomrepo')
        Pom pom = new Pom('test.nebula.bom', 'testbom', '1.0.0', ArtifactType.POM)
        pom.addManagementDependency('example', 'foo', '1.0.0')
        pom.addManagementDependency('example', 'bar', '1.0.0')
        repo.poms.add(pom)
        repo.generate()
        DependencyGraph depGraph = new DependencyGraphBuilder()
                .addModule('example:foo:1.0.0')
                .addModule('example:bar:1.0.0')
                .build()
        GradleDependencyGenerator generator = new GradleDependencyGenerator(depGraph)
        generator.generateTestMavenRepo()

        buildFile.text = """\
             buildscript {
                repositories { mavenCentral() }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:7.0.1'
                }
             }

             apply plugin: 'java'
             apply plugin: 'nebula.dependency-recommender'
             apply plugin: 'nebula.resolution-rules'
             repositories {
                 mavenCentral()
                 maven { url '${repo.root.absoluteFile.toURI()}' }
                 ${generator.mavenRepositoryBlock}                 
             }
             dependencies {
                 resolutionRules files("$nebulaBomResolutionRulesFile")
                 nebulaRecommenderBom 'test.nebula.bom:testbom:1.0.0@pom'
                 implementation group: 'com.google.guava', name: 'guava', version: '19.0'
             }
             
             configurations.nebulaRecommenderBom.resolve()
             """.stripIndent()

        when:
        def result = runTasksSuccessfully()

        then:
        !result.standardOutput.contains("Dependency resolution rules will not be applied to configuration ':nebulaRecommenderBom', it was resolved before the project was executed")
    }
}
