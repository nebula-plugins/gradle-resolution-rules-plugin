package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import org.codehaus.groovy.runtime.StackTraceUtils
import spock.lang.PendingFeature

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

    def 'substitute details are shown by dependencyInsight'() {
        given:
        buildFile << """\
             dependencies {
                compile 'bouncycastle:bcprov-jdk15:140'
             }
             """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencyInsight', '--configuration', 'compile', '--dependency', 'bcprov-jdk15')

        then:
        result.standardOutput.contains('org.bouncycastle:bcprov-jdk15:latest.release (substitution because The latest version of BC is required, using the new coordinate)')
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

        buildFile << """
                     dependencies {
                        compile 'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleMissingVersionException'
        rootCause.message == "The dependency to be substituted (org.ow2.asm:asm) must have a version. Invalid rule: SubstituteRule(module=asm:asm, with=org.ow2.asm:asm, ruleSet=missing-version-in-substitution-rule, reason=The asm group id changed for 4.0 and later, author=Danny Thomas <dmthomas@gmail.com>, date=2015-10-07T20:21:20.368Z)"
    }

    def 'fail on forced version that conflicts with substitution version'() {
        given:
        def reason = UUID.randomUUID()
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "substitute": [
                                 {
                                     "module" : "commons-logging:commons-logging:1.1.2",
                                     "with" : "commons-logging:commons-logging:1.1.3",
                                     "reason" : "$reason",
                                     "author" : "Example User <user@example.com>",
                                     "date" : "2017-11-07T20:21:20.368Z"
                                 }
                             ]
                         }
                         """.stripIndent()

        buildFile << """
                     configurations.compile {
                        resolutionStrategy {
                            force 'commons-logging:commons-logging:1.1.2'
                        }
                     }
                     dependencies {
                        compile 'commons-logging:commons-logging:latest.release'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        println result.standardError
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleConflictsWithForceException'
        rootCause.message == String.format("CONFLICT: commons-logging:commons-logging:1.1.2 forced on configuration ':compile'%n\tsubstitution rule: fail-on-forced-version-that-conflicts-with-substitution-version (reason: $reason)%n\tRemove or update force to fix")
    }

    def 'fail on forced version that conflicts with substitution range'() {
        given:
        def reason = UUID.randomUUID()
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "substitute": [
                                 {
                                     "module" : "commons-logging:commons-logging:[1.0,1.0.2]",
                                     "with" : "commons-logging:commons-logging:1.0.4",
                                     "reason" : "$reason",
                                     "author" : "Example User <user@example.com>",
                                     "date" : "2017-10-07T20:21:20.368Z"
                                 }
                             ]
                         }
                         """.stripIndent()
        buildFile << """
                     configurations.compile {
                        resolutionStrategy {
                            force 'commons-logging:commons-logging:1.0.1'
                        }
                     }
                     dependencies {
                        compile 'commons-logging:commons-logging:latest.release'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        println result.standardError
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleConflictsWithForceException'
        rootCause.message == String.format("CONFLICT: commons-logging:commons-logging:1.0.1 forced on configuration ':compile'%n\tsubstitution rule: fail-on-forced-version-that-conflicts-with-substitution-range (reason: $reason)%n\tRemove or update force to fix")
    }

    def 'fail on inline forced version that conflicts with substitution version'() {
        given:
        def reason = UUID.randomUUID()
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "substitute": [
                                 {
                                     "module" : "commons-logging:commons-logging:1.1.2",
                                     "with" : "commons-logging:commons-logging:1.1.3",
                                     "reason" : "$reason",
                                     "author" : "Example User <user@example.com>",
                                     "date" : "2017-11-07T20:21:20.368Z"
                                 }
                             ]
                         }
                         """.stripIndent()

        buildFile << """
                     dependencies {
                        compile('commons-logging:commons-logging:1.1.2') { force = true }
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        println result.standardError
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleConflictsWithForceException'
        rootCause.message == String.format("CONFLICT: commons-logging:commons-logging:1.1.2 forced on configuration ':compile'%n\tsubstitution rule: fail-on-inline-forced-version-that-conflicts-with-substitution-version (reason: $reason)%n\tRemove or update force to fix")
    }

    def 'fail on inline forced version that conflicts with substitution range'() {
        given:
        def reason = UUID.randomUUID()
        rulesJsonFile.delete()
        rulesJsonFile << """
                         {
                             "substitute": [
                                 {
                                     "module" : "commons-logging:commons-logging:[1.0,1.0.2]",
                                     "with" : "commons-logging:commons-logging:1.0.4",
                                     "reason" : "$reason",
                                     "author" : "Example User <user@example.com>",
                                     "date" : "2017-10-07T20:21:20.368Z"
                                 }
                             ]
                         }
                         """.stripIndent()
        buildFile << """
                     dependencies {
                        compile('commons-logging:commons-logging:1.0.1') { force = true }
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        println result.standardError
        def rootCause = StackTraceUtils.extractRootCause(result.failure)
        rootCause.class.simpleName == 'SubstituteRuleConflictsWithForceException'
        rootCause.message == String.format("CONFLICT: commons-logging:commons-logging:1.0.1 forced on configuration ':compile'%n\tsubstitution rule: fail-on-inline-forced-version-that-conflicts-with-substitution-range (reason: $reason)%n\tRemove or update force to fix")
    }
}
