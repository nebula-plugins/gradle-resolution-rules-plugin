package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndSubstituteRulesWithSpringBoot2xPluginAndManagedDepsSpec extends AbstractRulesWithSpringBootPluginSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        debug = true
        keepFiles = true
        System.setProperty('ignoreDeprecations', 'true')
    }

    @Unroll
    def 'direct dep | with provided version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ''
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | with lower requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)
        output.findAll("org.springframework.*:${managedSpringVersion}\n").size() >= 1
        output.findAll("org.springframework.*:${extSpringVersion}\n").size() == 0

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | with higher requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)
        output.findAll("org.springframework.*:${extSpringVersion}\n").size() >= 1
        output.findAll("org.springframework.*:${managedSpringVersion}\n").size() == 0

        where:
        extSpringVersion = '5.3.24'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | without requested version and forced'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', managedSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', managedSpringVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ''
        forcedVersion = '4.2.9.RELEASE'
    }

    @Unroll
    def 'direct dep | with requested version and forced to different versions'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-core', 'FAILED')

        dependencyInsightContains(output, 'org.springframework:spring-context', 'FAILED')
        dependencyInsightContains(output, 'org.springframework:spring-web', 'FAILED')
        dependencyInsightContains(output, 'org.springframework:spring-webmvc', 'FAILED')

        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = '4.2.9.RELEASE'
    }

    @Unroll
    def 'direct dep | with requested version and forced to same version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-core', 'FAILED')

        dependencyInsightContains(output, 'org.springframework:spring-context', 'FAILED')
        dependencyInsightContains(output, 'org.springframework:spring-web', 'FAILED')
        dependencyInsightContains(output, 'org.springframework:spring-webmvc', 'FAILED')

        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')


        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        managedSpringVersion = '5.3.20' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = extSpringVersion
    }

    @Unroll
    def 'transitive dep | with provided version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                '')
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.36' //  from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom
        requestedVersion = ''
        forcedVersion = ''
    }

    @Unroll
    def 'transitive dep | with requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.36' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom
        requestedVersion = ':\$slf4jVersion'
        forcedVersion = ''
    }

    @Unroll
    def 'transitive dep | without requested version and forced'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.36' //  from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/5.3.20/spring-boot-dependencies-5.3.20.pom

        requestedVersion = ''
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with lower requested version and forced to different version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with higher requested version and forced to different version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.8.0-beta4'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with requested version and forced to same version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion,
                "\napply plugin: \"io.spring.dependency-management\"",
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation  "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks( 'org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.7.0'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.26' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = extSlf4jVersion
    }
}
