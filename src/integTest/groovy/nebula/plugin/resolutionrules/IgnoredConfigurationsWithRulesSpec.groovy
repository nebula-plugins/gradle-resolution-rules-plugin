package nebula.plugin.resolutionrules


class IgnoredConfigurationsWithRulesSpec extends AbstractIntegrationTestKitSpec {
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
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', '-PresolutionRulesIgnoredConfigurations=myIgnoredConfiguration,myExtraIgnoredConfiguration')

        then:
        !result.output.contains('com.google.guava:guava:19.0-rc2 -> 19.0-rc1')
        !result.output.contains('bouncycastle:bcmail-jdk16:1.40 -> org.bouncycastle:bcmail-jdk16:')
    }


    def 'does not apply for configurations housing only built artifacts'() {
        given:
        System.setProperty('ignoreDeprecations', 'true')

        forwardOutput = true
        keepFiles = true
        def intermediateBuildFileText = buildFile.text
        buildFile.delete()
        buildFile.createNewFile()
        buildFile << """
            buildscript {
              repositories {
                maven {
                  url = uri("https://plugins.gradle.org/m2/")
                }
              }
              dependencies {
                classpath("org.springframework.boot:spring-boot-gradle-plugin:2.+")
              }
            }""".stripIndent()
        buildFile << intermediateBuildFileText
        buildFile << """
            apply plugin: 'org.springframework.boot'
            dependencies {
                implementation 'com.google.guava:guava:19.0-rc2'
                implementation 'bouncycastle:bcmail-jdk16:1.40'
            }
            tasks.named("bootJar") {
                mainClass = 'com.test.HelloWorldApp'
            }
            project.tasks.register("viewSpecificConfigurations").configure {
                it.dependsOn project.tasks.named('bootJar')
                it.dependsOn project.tasks.named('assemble')
                doLast {
                    project.configurations.matching { it.name == 'bootArchives' || it.name == 'archives' }.each {
                        println "Dependencies for \${it}: " + it.allDependencies 
                        println "Artifacts for \${it}: " + it.allArtifacts
                    }
                }
            }
            """.stripIndent()
        writeJavaSourceFile("""
            package com.test;
            
            class HelloWorldApp {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }""".stripIndent())
        new File(projectDir, 'gradle.properties').text = '''org.gradle.configuration-cache=false'''.stripIndent()

        when:
        def result = runTasks( 'bootJar', 'assemble')
        def resolutionResult = runTasks( 'viewSpecificConfigurations')

        then:
        !result.output.contains('FAIL')
        !resolutionResult.output.contains('FAIL')
        resolutionResult.output.contains(':jar')
    }

}
