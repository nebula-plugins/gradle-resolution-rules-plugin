package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import org.codehaus.groovy.runtime.StackTraceUtils

class IgnoredConfigurationsWithRulesSpec extends IntegrationSpec {
    File rulesJsonFile

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
                            "substitute": [
                                {
                                    "module" : "bouncycastle:bcmail-jdk16",
                                    "with" : "org.bouncycastle:bcmail-jdk16:latest.release",
                                    "reason" : "The latest version of BC is required, using the new coordinate",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                },
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
    }



    def 'does not substitute dependency if the configuration is ignored'() {
        given:
        buildFile << """

                     configurations {
                         myIgnoredConfiguration
                         myExtraIgnoredConfiguration
                     }
                     
                     dependencies {
                        myIgnoredConfiguration 'com.google.guava:guava:19.0-rc2'
                        myExtraIgnoredConfiguration'bouncycastle:bcmail-jdk16:1.40'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compileClasspath', '-PresolutionRulesIgnoredConfigurations=myIgnoredConfiguration,myExtraIgnoredConfiguration')

        then:
        !result.standardOutput.contains('com.google.guava:guava:19.0-rc2 -> 19.0-rc1')
        !result.standardOutput.contains('bouncycastle:bcmail-jdk16:1.40 -> org.bouncycastle:bcmail-jdk16:')
    }

}
