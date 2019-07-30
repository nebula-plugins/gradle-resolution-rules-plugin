package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import spock.lang.Unroll

class AlignAndSubstituteRulesWithSpringSpec extends IntegrationTestKitSpec {
    File rulesJsonFile
    String reason = "★ custom reason"

    private static String alignRuleForSpring = """\
        {
            "group": "org\\\\.springframework",
            "includes": ["spring-(tx|aop|instrument|context-support|beans|jms|test|core|oxm|web|context|expression|aspects|websocket|framework-bom|webmvc|webmvc-portlet|jdbc|orm|instrument-tomcat|messaging)"],
            "excludes": [],
            "match": "[2-9]\\\\.[0-9]+\\\\.[0-9]+.RELEASE",
            "reason": "Align Spring",
            "author": "User <user@example.com>",
            "date": "2016-05-16"
        }""".stripIndent()

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        debug = true
        keepFiles = true
    }

    @Unroll
    def 'spring boot 1.x dependencies interaction | with #versionType | core alignment #coreAlignment'() {
        given:
        def extSpringVersion = '4.2.4.RELEASE'
        def extSpringBootVersion = '1.5.6.RELEASE'

        // in Spring Boot 1.x plugin, dependency management is added automatically
        springBootBasedBuildFileWith(extSpringBootVersion, extSpringVersion, usesForce, requestedVersion, '')
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter"
                compile "org.springframework.boot:spring-boot-starter-web"
            }
            """.stripIndent()

        new File("${projectDir}/gradle.properties").delete()

        rulesJsonFile << alignSpringRule()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'org.springframework', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def output = result.output

        then:
        dependencyInsightContains(output, 'org.springframework:spring-aop', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-beans', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-expression', resultingVersionForMost)

        if (!resultsAreAligned) {
            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-core', resultingVersionForMost)
        }

        where:
        versionType                   | coreAlignment | requestedVersion     | resultingVersionForMost | usesForce | resultsAreAligned
        'provided version'            | false         | ''                   | '4.3.10.RELEASE'        | false     | true
        'provided version'            | true          | ''                   | '4.3.10.RELEASE'        | false     | true

        'declared version'            | false         | ':\${springVersion}' | '4.3.10.RELEASE'        | false     | true
        'declared version'            | true          | ':\${springVersion}' | '4.3.10.RELEASE'        | false     | false

        'declared version and forced' | false         | ':\${springVersion}' | '4.2.4.RELEASE'         | true      | true
        'declared version and forced' | true          | ':\${springVersion}' | '4.3.10.RELEASE'        | true      | false
    }

    @Unroll
    def 'spring boot 2.x dependencies interaction | with #versionType | core alignment #coreAlignment'() {
        given:
        def extSpringVersion = '4.2.4.RELEASE'
        def extSpringBootVersion = '2.1.4.RELEASE'

        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        springBootBasedBuildFileWith(extSpringBootVersion, extSpringVersion, usesForce, requestedVersion, "\napply plugin: \"io.spring.dependency-management\"")
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter"
                compile "org.springframework.boot:spring-boot-starter-web"
            }
            """.stripIndent()

        new File("${projectDir}/gradle.properties").delete()

        rulesJsonFile << alignSpringRule()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'org.springframework', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def output = result.output

        then:
        dependencyInsightContains(output, 'org.springframework:spring-aop', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-beans', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-expression', resultingVersionForMost)

        if (!resultsAreAligned) {
            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-core', resultingVersionForMost)
        }

        where:
        versionType                   | coreAlignment | requestedVersion     | resultingVersionForMost | usesForce | resultsAreAligned
        'provided version'            | false         | ''                   | '5.1.6.RELEASE'         | false     | true
        'provided version'            | true          | ''                   | '5.1.6.RELEASE'         | false     | true

        'declared version'            | false         | ':\${springVersion}' | '5.1.6.RELEASE'         | false     | true
        'declared version'            | true          | ':\${springVersion}' | '5.1.6.RELEASE'         | false     | false

        'declared version and forced' | false         | ':\${springVersion}' | '4.2.4.RELEASE'         | true      | true
        'declared version and forced' | true          | ':\${springVersion}' | '5.1.6.RELEASE'         | true      | false
    }

    @Unroll
    def 'spring boot 2.x dependencies interaction without Spring dependency management | with #versionType | core alignment #coreAlignment'() {
        given:
        def extSpringVersion = '4.2.4.RELEASE'
        def extSpringBootVersion = '2.1.4.RELEASE'

        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        springBootBasedBuildFileWith(extSpringBootVersion, extSpringVersion, usesForce, requestedVersion, '')
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                compile "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

        new File("${projectDir}/gradle.properties").delete()

        rulesJsonFile << alignSpringRule()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'org.springframework', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def output = result.output

        then:
        dependencyInsightContains(output, 'org.springframework:spring-aop', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-beans', resultingVersionForMost)
        dependencyInsightContains(output, 'org.springframework:spring-expression', resultingVersionForMost)

        // and results are aligned!
        dependencyInsightContains(output, 'org.springframework:spring-core', resultingVersionForMost)

        where:
        versionType                   | coreAlignment | requestedVersion     | resultingVersionForMost | usesForce
        'declared version'            | false         | ':\${springVersion}' | '5.1.6.RELEASE'         | false
        'declared version'            | true          | ':\${springVersion}' | '5.1.6.RELEASE'         | false

        'declared version and forced' | false         | ':\${springVersion}' | '4.2.4.RELEASE'         | true
        'declared version and forced' | true          | ':\${springVersion}' | '4.2.4.RELEASE'         | true
    }


    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }

    private static String alignSpringRule() {
        """
        {
            "align": [
                $alignRuleForSpring
            ]
        }
        """.stripIndent()
    }

    private void springBootBasedBuildFileWith(String extSpringBootVersion, String extSpringVersion, boolean usesForce, String requestedVersion, String additionalPlugin) {
        buildFile << """
        buildscript {
            dependencies {
                classpath("org.springframework.boot:spring-boot-gradle-plugin:$extSpringBootVersion")
                classpath "io.spring.gradle:dependency-management-plugin:1.0.7.RELEASE"
            }
            repositories {
                maven {
                    url "https://plugins.gradle.org/m2/"
                }
            }
        }
        plugins {
            id 'java'
            id 'nebula.resolution-rules'
        }
        apply plugin: 'org.springframework.boot'$additionalPlugin
        repositories {
            mavenCentral()
        }
        dependencies {
            resolutionRules files('$rulesJsonFile')
        }
        ext {
            springVersion = "$extSpringVersion"
            springBootVersion = "$extSpringBootVersion"
        }
        """.stripIndent()

        if (usesForce) {
            buildFile << """
                configurations.all {
                    resolutionStrategy {
                        force "org.springframework:spring-core$requestedVersion"
                    }
                }
                """.stripIndent()
        }
    }
}