package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Unroll

class AlignRulesBasicWithCoreSpec extends AbstractAlignRulesSpec {

    def setup() {
        debug = true
        keepFiles = true
        new File("${projectDir}/gradle.properties").text = "systemProp.nebula.features.coreAlignmentSupport=true"
        settingsFile << """
        enableFeaturePreview("GRADLE_METADATA")
        """
    }

    def 'align rules and force to latest.release'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.1.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
            }
            configurations.all {
                resolutionStrategy { 
                    force 'test.nebula:a:latest.release' 
                }
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        result.output.contains 'coreAlignmentSupport feature enabled'
        result.output.contains 'belongs to platform aligned-platform:align-rules-and-force-to-latest-release-0-for-test.nebula:1.1.0'
    }

    def 'align rules and force to latest.release when brought in transitively'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.1.0').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.other:brings-a:latest.release'
                compile 'test.other:also-brings-a:latest.release'
                compile 'test.other:brings-b:latest.release'
            }
            configurations.all {
                resolutionStrategy { 
                    force 'test.nebula:a:latest.release' 
                }
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        result.output.contains 'coreAlignmentSupport feature enabled'
    }

    def 'multiple align rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.other:c:0.12.2')
                .addModule('test.other:c:1.0.0')
                .addModule('test.other:d:0.12.2')
                .addModule('test.other:d:1.0.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "name": "testOther",
                        "group": "test.other",
                        "reason": "Aligning test",
                        "author": "Example Tester <test@example.org>",
                        "date": "2016-04-05T19:19:49.495Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
                compile 'test.other:c:1.0.0'
                compile 'test.other:d:0.12.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compile')

        then:
        result.output.contains 'test.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains 'test.nebula:b:1.1.0\n'
        result.output.contains 'test.other:c:1.0.0\n'
        result.output.contains 'test.other:d:0.12.+ -> 1.0.0\n'

        when:
        result = runTasks('dependencyInsight', '--dependency', 'test.nebula:a')

        then:
        result.output.contains 'coreAlignmentSupport feature enabled'
    }

    @Unroll
    def 'spring boot 1.x dependencies interaction | with #versionType | core alignment #coreAlignment'() {
        given:
        buildFile.delete()
        buildFile.createNewFile()
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
        buildFile.delete()
        buildFile.createNewFile()
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
        buildFile.delete()
        buildFile.createNewFile()
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

    private static String alignSpringRule() {
        """
        {
            "align": [
                {
                    "group": "org\\\\.springframework",
                    "includes": ["spring-(tx|aop|instrument|context-support|beans|jms|test|core|oxm|web|context|expression|aspects|websocket|framework-bom|webmvc|webmvc-portlet|jdbc|orm|instrument-tomcat|messaging)"],
                    "excludes": [],
                    "match": "[2-9]\\\\.[0-9]+\\\\.[0-9]+.RELEASE",
                    "reason": "Align Spring",
                    "author": "User <user@example.com>",
                    "date": "2016-05-16"
                }
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

    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }
}
