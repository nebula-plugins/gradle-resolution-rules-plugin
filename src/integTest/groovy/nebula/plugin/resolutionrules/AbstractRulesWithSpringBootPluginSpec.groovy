package nebula.plugin.resolutionrules


class AbstractRulesWithSpringBootPluginSpec extends AbstractIntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")
        keepFiles = true
    }

    static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }

    File addSpringDependenciesWhenUsingManagedDependencies(String requestedVersion) {
        buildFile << """
            dependencies {
                implementation "org.springframework:spring-core$requestedVersion"
                implementation "org.springframework.boot:spring-boot-starter"
                implementation "org.springframework.boot:spring-boot-starter-web"
            }
            """.stripIndent()
    }

    static def tasks(String groupForInsight = 'org.springframework:') {
        return [
                'dependencyInsight',
                '--dependency',
                groupForInsight, '-s'
        ]
    }

    void setupForDirectDependencyScenario(String extSpringBootVersion, String forcedVersion, String additionalPlugin = '', String additionalExtProperty = '') {
        setupBaseSpringBootBasedBuildFileWith(extSpringBootVersion, additionalPlugin, additionalExtProperty)

        if (forcedVersion != '' && forcedVersion != null) {
            buildFile << """
                configurations.all {
                    resolutionStrategy {
                        force "org.springframework:spring-aop:$forcedVersion"
                        force "org.springframework:spring-beans:$forcedVersion"
                        force "org.springframework:spring-context:$forcedVersion"
                        force "org.springframework:spring-core:$forcedVersion"
                        force "org.springframework:spring-expression:$forcedVersion"
                        force "org.springframework:spring-web:$forcedVersion"
                        force "org.springframework:spring-webmvc:$forcedVersion"
                    }
                }
                """.stripIndent()
        }

        rulesJsonFile << alignSpringRule()
    }

    void setupForTransitiveDependencyScenario(String extSpringBootVersion, String forcedVersion, String additionalPlugin = '', String additionalExtProperty = '') {
        setupBaseSpringBootBasedBuildFileWith(extSpringBootVersion, additionalPlugin, additionalExtProperty)

        if (forcedVersion != '' && forcedVersion != null) {
            buildFile << """
                configurations.all {
                    resolutionStrategy {
                        force "org.slf4j:slf4j-simple:$forcedVersion"
                        force "org.slf4j:slf4j-api:$forcedVersion"
                    }
                }
                """.stripIndent()
        }

        rulesJsonFile << alignSlf4jRule()
    }

    private File setupBaseSpringBootBasedBuildFileWith(String extSpringBootVersion, String additionalPlugin = '', String additionalExtProperty = '') {
        buildFile << """
buildscript {
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:$extSpringBootVersion")
        classpath "io.spring.gradle:dependency-management-plugin:1.1.0"
    }
    repositories {
        maven {
            url "https://plugins.gradle.org/m2/"
        }
    }
}
plugins {
    id 'java'
    id 'com.netflix.nebula.resolution-rules'
}
apply plugin: 'org.springframework.boot'$additionalPlugin
repositories {
    mavenCentral()
}
dependencies {
    resolutionRules files('$rulesJsonFile')
}
ext {$additionalExtProperty
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

    void writeOutputToProjectDir(String output) {
        def file = new File(projectDir, "result.txt")
        file.createNewFile()
        file << output
    }
}
