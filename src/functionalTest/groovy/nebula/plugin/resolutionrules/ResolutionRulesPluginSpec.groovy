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
                         jcenter()
                     }

                     dependencies {
                         resolutionRules files("$rulesJsonFile", "$optionalRulesJsonFile")
                     }
                     """.stripIndent()

        rulesJsonFile << """
                        {
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

    def 'warning output when no rules source'() {
        given:
        buildFile.delete()
        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'nebula.resolution-rules'
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies')

        then:
        result.standardOutput.contains("No resolution rules have been added to the 'resolutionRules' configuration")
    }

    def 'empty configuration'() {
        expect:
        runTasksSuccessfully()
    }

    def 'warning logged when configuration has been resolved'() {
        given:
        buildFile.text = """\
             apply plugin: 'java'
             configurations.compile.resolve()
             apply plugin: 'nebula.resolution-rules'

             repositories {
                 jcenter()
             }

             dependencies {
                 resolutionRules files("$rulesJsonFile", "$optionalRulesJsonFile")
             }
             """.stripIndent()

        when:
        def result = runTasksSuccessfully()

        then:
        result.standardOutput.contains("Configuration 'compile' has been resolved. Dependency resolution rules will not be applied")
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
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies')

        then:
        def output = result.standardOutput
        output.contains 'Using duplicate-rules-sources (duplicate-rules-sources.json) a dependency rules source'
        output.contains 'Found rules with the same name. Overriding existing ruleset duplicate-rules-sources'
        output.contains "Using duplicate-rules-sources ($projectDir/rules.jar) a dependency rules source"
        output.contains "Using duplicate-rules-sources ($projectDir/rules.zip) a dependency rules source"
    }

    def 'replace module'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'asm:asm:3.3.1'
                        compile 'org.ow2.asm:asm:5.0.4'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }

    def "module is not replaced if the replacement isn't in the configuration"() {
        given:
        buildFile << """
                     dependencies {
                        compile 'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('asm:asm:3.3.1')
        !result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }


    def 'exclude dependency'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'io.netty:netty-all:5.0.0.Alpha2'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

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
                         compile 'log4j:log4j:1.2.17'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

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

                         compile 'log4j:log4j:1.2.17'
                         compile 'org.slf4j:jcl-over-slf4j:1.7.0'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains("""\
+--- log4j:log4j:1.2.17 -> org.slf4j:log4j-over-slf4j:1.7.21
|    \\--- org.slf4j:slf4j-api:1.7.21
\\--- org.slf4j:jcl-over-slf4j:1.7.0 -> 1.7.21
     \\--- org.slf4j:slf4j-api:1.7.21
""")
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

                         compile 'log4j:log4j:1.2.17'
                         compile 'asm:asm:3.3.1'
                         compile 'org.ow2.asm:asm:5.0.4'
                     }
                     """.stripIndent()


        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('log4j:log4j:1.2.17 -> org.slf4j:log4j-over-slf4j:1.7.21')
        !result.standardOutput.contains('asm:asm:3.3.1 -> org.ow2.asm:asm:5.0.4')
    }

    @Unroll
    def "deny dependency (from super: #inherited)"() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.google.guava:guava:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', configuration)

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message == "Dependency com.google.guava:guava:19.0-rc2 denied by dependency rule: Guava 19.0-rc2 is not permitted"

        where:
        configuration      | inherited
        'compile'          | false
        'compileClasspath' | true
    }

    @Unroll
    def "deny dependency without version (from super: #inherited)"() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.sun.jersey:jersey-bundle:1.19'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', configuration)

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message == "Dependency com.sun.jersey:jersey-bundle denied by dependency rule: jersey-bundle is a fat jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly"

        where:
        configuration      | inherited
        'compile'          | false
        'compileClasspath' | true
    }

    def 'reject dependency'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.google.guava:guava:12.0'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains("Selection of com.google.guava:guava:12.0 rejected by component selection rule: Rejected by resolution rule reject-dependency - Guava 12.0 significantly regressed LocalCache performance")
    }
}
