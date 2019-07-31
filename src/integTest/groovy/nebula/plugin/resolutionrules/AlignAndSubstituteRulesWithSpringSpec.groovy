package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import spock.lang.Unroll

class AlignAndSubstituteRulesWithSpringSpec extends IntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        debug = true
        keepFiles = true
    }

    @Unroll
    def 'spring boot 1.x plugin: direct dependencies | with provided version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ''
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 1.x plugin: direct dependencies | with requested version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)

        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 1.x plugin: direct dependencies | without requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-aop', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ''
        forcedVersion = '4.2.9.RELEASE'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 1.x plugin: direct dependencies | with requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)

            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)

        } else {
            dependencyInsightContains(output, 'org.springframework:spring-aop', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = '4.2.9.RELEASE'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with provided version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, "\napply plugin: \"io.spring.dependency-management\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ''
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with requested version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, "\napply plugin: \"io.spring.dependency-management\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)

        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | without requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, "\napply plugin: \"io.spring.dependency-management\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-aop', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ''
        forcedVersion = '4.2.9.RELEASE'
        coreAlignment << [false, true]
    }


    @Unroll
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, "\napply plugin: \"io.spring.dependency-management\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)

            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-aop', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', forcedVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = '4.2.9.RELEASE'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 2.x plugin without Spring dependency management: direct dependencies | with requested version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                compile "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring boot 2.x plugin without Spring dependency management: direct dependencies | with requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, '')
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                compile "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', forcedVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', forcedVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', forcedVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', forcedVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = '4.2.4.RELEASE'
        coreAlignment << [false, true]
    }

//    @Unroll
//    def 'spring boot 1.x plugin: transitive dependencies | with #versionType | core alignment #coreAlignment'() {
//        given:
//        def extSpringVersion = '4.2.4.RELEASE'
//        def extSpringBootVersion = '1.5.6.RELEASE'
//        def extSlf4jVersion = '1.6.0'
//
//        // in Spring Boot 1.x plugin, dependency management is added automatically
//        setupForTransitiveDependencyScenario(extSpringBootVersion, extSpringVersion, forcedVersion, requestedVersion,
//                "\napply plugin: \"io.spring.dependency-management\"",
//                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
//        buildFile << """
//            dependencies {
//                compile "org.slf4j:slf4j-simple$requestedVersion"
//            }
//            """.stripIndent()
//
//        new File("${projectDir}/gradle.properties").delete()
//
//        when:
//        def result = runTasks('dependencyInsight', '--dependency', 'org.slf4j', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
//        def output = result.output
//
//        then:
//        writeOutputToProjectDir(output)
//        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', resultingVersionForMost)
//        dependencyInsightContains(output, 'org.slf4j:slf4j-api', resultingVersionForMost)
//
//        where:
//        versionType                   | coreAlignment | requestedVersion  | resultingVersionForMost | forcedVersion | resultsAreAligned
//        'provided version'            | false         | ''                | '1.7.25'                | false     | true
//        'provided version'            | true          | ''                | '1.7.25'                | false     | true
//
//        'requested version'            | false         | ':\$slf4jVersion' | '1.7.25'                | false     | true
//        'requested version'            | true          | ':\$slf4jVersion' | '1.7.25'                | false     | false
//
//        'requested version and forced' | false         | ':\$slf4jVersion' | '1.6.0'                 | true      | true
//        'requested version and forced' | true          | ':\$slf4jVersion' | '1.6.0'                 | true      | false
//    }

    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }

    private File addSpringDependenciesWhenUsingManagedDependencies(String requestedVersion) {
        buildFile << """
            dependencies {
                compile "org.springframework:spring-core$requestedVersion"
                compile "org.springframework.boot:spring-boot-starter"
                compile "org.springframework.boot:spring-boot-starter-web"
            }
            """.stripIndent()
    }

    private static def tasks(Boolean usingCoreAlignment, String groupForInsight = 'org.springframework') {
        return [
                'dependencyInsight',
                '--dependency',
                groupForInsight,
                "-Dnebula.features.coreAlignmentSupport=$usingCoreAlignment"
        ]
    }

    private void setupForDirectDependencyScenario(String extSpringBootVersion, String extSpringVersion, String forcedVersion, String additionalPlugin = '', String additionalExtProperty = '') {
        setupBaseSpringBootBasedBuildFileWith(extSpringBootVersion, extSpringVersion, additionalPlugin, additionalExtProperty)

        if (forcedVersion != '' && forcedVersion != null) {
            buildFile << """
                configurations.all {
                    resolutionStrategy {
                        force "org.springframework:spring-core:$forcedVersion"
                    }
                }
                """.stripIndent()
        }

        rulesJsonFile << alignSpringRule()
    }

    private void setupForTransitiveDependencyScenario(String extSpringBootVersion, String extSpringVersion, String forcedVersion, String additionalPlugin = '', String additionalExtProperty = '') {
        setupBaseSpringBootBasedBuildFileWith(extSpringBootVersion, extSpringVersion, additionalPlugin, additionalExtProperty)

        if (forcedVersion != '' && forcedVersion != null) {
            buildFile << """
                configurations.all {
                    resolutionStrategy {
                        force "org.slf4j:slf4j-simple:$forcedVersion"
                    }
                }
                """.stripIndent()
        }

        rulesJsonFile << alignSlf4jRule()
    }

    private File setupBaseSpringBootBasedBuildFileWith(String extSpringBootVersion, String extSpringVersion, String additionalPlugin = '', String additionalExtProperty = '') {
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
    springBootVersion = "$extSpringBootVersion"$additionalExtProperty
}
""".stripIndent()

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

    private static String alignSlf4jRule() {
        """
        {
            "align": [
                {
                    "name": "align slf4j",
                    "group": "org.slf4j",
                    "reason": "Align slf4j",
                    "author": "User <user@example.com>",
                    "date": "2016-05-16"
                }
           ]
        }
        """.stripIndent()
    }

    private void writeOutputToProjectDir(String output) {
        def file = new File(projectDir, "result.txt")
        file.createNewFile()
        file << output
    }
}