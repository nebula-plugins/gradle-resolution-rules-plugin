package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndSubstituteRulesWithSpringBoot2xPluginWithoutManagedDepsSpec extends AbstractRulesWithSpringBootPluginSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        debug = true
        keepFiles = true
    }

    @Unroll
    def 'spring boot 2.x plugin without Spring dependency management: direct dependencies | with requested version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tspringVersion = \"$extSpringVersion\"")
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
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tspringVersion = \"$extSpringVersion\"")
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

}