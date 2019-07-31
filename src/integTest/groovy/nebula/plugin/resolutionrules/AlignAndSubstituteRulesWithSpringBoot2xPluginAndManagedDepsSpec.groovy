package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndSubstituteRulesWithSpringBoot2xPluginAndManagedDepsSpec extends AbstractRulesWithSpringBootPluginSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        debug = true
        keepFiles = true
    }

    @Unroll
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with provided version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
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
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
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
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
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
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with requested version and forced to different versions | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-core', 'FAILED')

            dependencyInsightContains(output, 'org.springframework:spring-context', 'FAILED')
            dependencyInsightContains(output, 'org.springframework:spring-web', 'FAILED')
            dependencyInsightContains(output, 'org.springframework:spring-webmvc', 'FAILED')

            assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

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
    def 'spring boot 2.x plugin with Spring dependency management: direct dependencies | with requested version and forced to same version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.springframework:spring-core', 'FAILED')

            dependencyInsightContains(output, 'org.springframework:spring-context', 'FAILED')
            dependencyInsightContains(output, 'org.springframework:spring-web', 'FAILED')
            dependencyInsightContains(output, 'org.springframework:spring-webmvc', 'FAILED')

            assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

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
        forcedVersion = extSpringVersion
        coreAlignment << [false, true]
    }

}