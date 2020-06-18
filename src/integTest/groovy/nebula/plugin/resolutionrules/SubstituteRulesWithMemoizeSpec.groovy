package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import org.codehaus.groovy.runtime.StackTraceUtils

class SubstituteRulesWithMemoizeSpec extends IntegrationSpec {
    File rulesJsonFile
    File rulesJsonFileSecondary

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        rulesJsonFileSecondary = new File(projectDir, "${moduleName}-secondary.json")

        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'nebula.resolution-rules'

                     repositories {
                         jcenter()
                     }

                     dependencies {
                         resolutionRules files("$rulesJsonFile")
                         resolutionRules files("$rulesJsonFileSecondary")
                     }
                     """.stripIndent()

        rulesJsonFile << """
                        {
                            "substitute": [
                                {
                                    "module": "com.google.guava:guava:19.0-rc2",
                                    "with": "com.google.guava:guava:19.0-rc1",
                                    "reason" : "Guava 19.0-rc2 is not permitted, use previous release",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ]
                        }
                        """.stripIndent()

        rulesJsonFileSecondary << """
                        {
                            "substitute": [
                                {
                                    "module": "com.google.guava:guava-testlib:19.0-rc2",
                                    "with": "com.google.guava:guava-testlib:19.0-rc1",
                                    "reason" : "guava-testlib 19.0-rc2 is not permitted, use previous release",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ]
                        }
                        """.stripIndent()
    }

    def 'substitute dependency with version'() {
        given:
        buildFile << """
                     dependencies {
                        implementation'com.google.guava:guava:19.0-rc2'
                        implementation'com.google.guava:guava-testlib:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath')

        then:
        result.standardOutput.contains('com.google.guava:guava:19.0-rc2 -> 19.0-rc1')
        result.standardOutput.contains('com.google.guava:guava-testlib:19.0-rc2 -> 19.0-rc1')
    }
}
