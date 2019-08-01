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
    def 'direct dep | with provided version | core alignment #coreAlignment'() {
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
    def 'direct dep | with lower requested version | core alignment #coreAlignment'() {
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
    def 'direct dep | with higher requested version | core alignment #coreAlignment'() {
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

            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
            
        } else {
            dependencyInsightContains(output, 'org.springframework:spring-aop', extSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-beans', extSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-expression', extSpringVersion)
            dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        }

        where:
        extSpringVersion = '5.1.8.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'direct dep | without requested version and forced | core alignment #coreAlignment'() {
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
    def 'direct dep | with requested version and forced to different versions | core alignment #coreAlignment'() {
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
    def 'direct dep | with requested version and forced to same version | core alignment #coreAlignment'() {
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

    @Unroll
    def 'transitive dep | with provided version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                '')
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ''
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive dep | with requested version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = ''
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive dep | without requested version and forced | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)
        } else {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ''
        forcedVersion = '1.7.10'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive dep | with lower requested version and forced to different version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)
        } else {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive dep | with higher requested version and forced to different version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
            assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')
        } else {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.8.0-beta4'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive dep | with requested version and forced to same version | core alignment #coreAlignment'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                compile "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment, 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        if (coreAlignment) {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)
        } else {
            dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
            dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)
        }

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = extSlf4jVersion
        coreAlignment << [false, true]
    }
}