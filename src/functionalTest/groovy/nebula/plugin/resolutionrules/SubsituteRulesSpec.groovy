package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import org.codehaus.groovy.runtime.StackTraceUtils

class SubsituteRulesSpec extends IntegrationSpec {
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
                                    "module" : "bouncycastle:bcprov-jdk15",
                                    "with" : "org.bouncycastle:bcprov-jdk15:latest.release",
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
                                },
                                {
                                    "module": "com.sun.jersey:jersey-bundle:(,1.18)",
                                    "with": "com.sun.jersey:jersey-bundle:1.18",
                                    "reason" : "Use a minimum version of 1.18",
                                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                                    "date" : "2015-10-07T20:21:20.368Z"
                                }
                            ]
                        }
                        """.stripIndent()
    }

    def 'substitute dependency without version'() {
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

    def 'substitute dependency with version'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.google.guava:guava:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('com.google.guava:guava:19.0-rc2 -> 19.0-rc1')
    }

    def 'substitute dependency outside allowed range'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.sun.jersey:jersey-bundle:1.17'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('om.sun.jersey:jersey-bundle:1.17 -> 1.18')
    }

    def 'do not substitute dependency above allowed range'() {
        given:
        buildFile << """
                     dependencies {
                        compile 'com.sun.jersey:jersey-bundle:1.18'
                     }
                     """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('om.sun.jersey:jersey-bundle:1.18\n')
    }

    def 'missing version in substitution rule'() {
        given:
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "substitute": [
                                 {
                                     "module" : "asm:asm",
                                     "with" : "org.ow2.asm:asm",
                                     "reason" : "The asm group id changed for 4.0 and later",
                                     "author" : "Danny Thomas <dmthomas@gmail.com>",
                                     "date" : "2015-10-07T20:21:20.368Z"
                                 }
                             ]
                         }
                         """.stripIndent()
        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleMissingVersionException'
        rootCause.message == "The dependency to be substituted (org.ow2.asm:asm) must have a version. Invalid rule: SubstituteRule(module=asm:asm, with=org.ow2.asm:asm, ruleSet=missing-version-in-substitution-rule, reason=The asm group id changed for 4.0 and later, author=Danny Thomas <dmthomas@gmail.com>, date=2015-10-07T20:21:20.368Z)"
    }
}
