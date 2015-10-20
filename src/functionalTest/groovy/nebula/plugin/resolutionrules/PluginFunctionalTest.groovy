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

/**
 * Functional test for {@link ResolutionRulesPlugin}.
 */
class PluginFunctionalTest extends IntegrationSpec {
    def rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'nebula.resolution-rules'

                     repositories {
                         jcenter()
                     }

                     dependencies {
                         resolutionRules files("$rulesJsonFile")
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
                            ]
                        }
                        """.stripIndent()
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
        def result = runTasksSuccessfully('help')

        then:
        result.standardOutput.contains("No resolution rules have been added to the 'resolutionRules' configuration")
    }

    def 'empty configuration'() {
        expect:
        runTasksSuccessfully()
    }

    def 'warning logged when configuration has been resolved'() {
        given:
        buildFile << """
                     configurations.compile.resolvedConfiguration
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully()

        then:
        result.standardOutput.contains("Configuration 'compile' has been resolved. Dependency resolution rules will not be applied")
    }

    def 'all rules sources'() {
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
        def result = runTasksSuccessfully()

        then:
        [rulesJsonFile, rulesZipFile, rulesJarFile].each {
            assert result.standardOutput.contains("Using ${it.absolutePath} as a dependency rules source")
        }
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

    def 'substitute dependency'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'bouncycastle:bcprov-jdk15:140'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('bouncycastle:bcprov-jdk15:140 -> org.bouncycastle:bcprov-jdk15:latest.release')
    }

    def 'missing version in substitution rule'() {
        given:
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "replace" : [],
                             "substitute": [
                                 {
                                     "module" : "asm:asm",
                                     "with" : "org.ow2.asm:asm",
                                     "reason" : "The asm group id changed for 4.0 and later",
                                     "author" : "Danny Thomas <dmthomas@gmail.com>",
                                     "date" : "2015-10-07T20:21:20.368Z"
                                 }
                             ],
                             "reject": [],
                             "deny": []
                         }
                         """.stripIndent()
        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleMissingVersionException'
        rootCause.message == "The dependency to be substituted (org.ow2.asm:asm) must have a version. Invalid rule: SubstituteRule(module=asm:asm, with=org.ow2.asm:asm, reason=The asm group id changed for 4.0 and later, author=Danny Thomas <dmthomas@gmail.com>, date=2015-10-07T20:21:20.368Z)"
    }

    def 'deny dependency'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.google.guava:guava:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message == "Dependency com.google.guava:guava:19.0-rc2 denied by dependency rule: Guava 19.0-rc2 is not permitted"
    }

    def 'deny dependency without version'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.sun.jersey:jersey-bundle:1.19'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'DependencyDeniedException'
        rootCause.message == "Dependency com.sun.jersey:jersey-bundle denied by dependency rule: jersey-bundle is a far jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly"
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
        result.standardOutput.contains("Selection of com.google.guava:guava:12.0 rejected by component selection rule: Guava 12.0 significantly regressed LocalCache performance")
    }
}
