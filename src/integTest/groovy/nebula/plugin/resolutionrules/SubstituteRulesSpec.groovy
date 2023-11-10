package nebula.plugin.resolutionrules


class SubstituteRulesSpec  extends AbstractIntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        definePluginOutsideOfPluginBlock = true

        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'com.netflix.nebula.resolution-rules'

                     repositories {
                         mavenCentral()
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
                        implementation'bouncycastle:bcmail-jdk16:1.40'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('bouncycastle:bcmail-jdk16:1.40 -> org.bouncycastle:bcmail-jdk16:')
    }

    def 'substitute details are shown by dependencyInsight'() {
        given:
        buildFile << """\
             dependencies {
                implementation'bouncycastle:bcmail-jdk16:1.40'
             }
             """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'bcmail-jdk16')

        then:
        !result.output.contains('org.bouncycastle:bcmail-jdk16:1.40')
        result.output.contains('org.bouncycastle:bcmail-jdk16:')
        result.output.contains('The latest version of BC is required, using the new coordinate')
    }

    def 'substitute dependency with version'() {
        given:
        buildFile << """
                     dependencies {
                        implementation'com.google.guava:guava:19.0-rc2'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('com.google.guava:guava:19.0-rc2 -> 19.0-rc1')
    }

    def 'substitute dependency outside allowed range'() {
        given:
        buildFile << """
                     dependencies {
                        implementation'com.sun.jersey:jersey-bundle:1.17'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('om.sun.jersey:jersey-bundle:1.17 -> 1.18')
    }

    def 'do not substitute dependency above allowed range'() {
        given:
        buildFile << """
                     dependencies {
                        implementation'com.sun.jersey:jersey-bundle:1.18'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('om.sun.jersey:jersey-bundle:1.18\n')
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
                        implementation'asm:asm:3.3.1'
                     }
                     """.stripIndent()

        when:
        def result = runTasksAndFail('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains("The dependency to be substituted (org.ow2.asm:asm) must have a version. Rule missing-version-in-substitution-rule is invalid")
    }
}
