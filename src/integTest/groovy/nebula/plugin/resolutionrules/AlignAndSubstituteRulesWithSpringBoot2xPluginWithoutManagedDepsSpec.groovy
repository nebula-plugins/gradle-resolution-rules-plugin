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
    def 'direct dep | with lower requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tspringVersion = \"$extSpringVersion\"")
        buildFile << """
            dependencies {
                implementation "org.springframework:spring-core$requestedVersion"
                implementation "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                implementation "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

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
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | with higher requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tspringVersion = \"$extSpringVersion\"")
        buildFile << """
            dependencies {
                implementation "org.springframework:spring-core$requestedVersion"
                implementation "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                implementation "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks())
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.springframework:spring-aop', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-beans', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-expression', extSpringVersion)
        dependencyInsightContains(output, 'org.springframework:spring-core', extSpringVersion)

        where:
        extSpringVersion = '5.1.8.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        managedSpringVersion = '5.1.6.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.1.4.RELEASE/spring-boot-dependencies-2.1.4.RELEASE.pom

        requestedVersion = ':\${springVersion}'
        forcedVersion = '' }

    @Unroll
    def 'direct dep | with requested version and forced'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tspringVersion = \"$extSpringVersion\"")
        buildFile << """
            dependencies {
                implementation "org.springframework:spring-core$requestedVersion"
                implementation "org.springframework.boot:spring-boot-starter:$extSpringBootVersion"
                implementation "org.springframework.boot:spring-boot-starter-web:$extSpringBootVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks())
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

        requestedVersion = ':\${springVersion}'
        forcedVersion = '4.2.4.RELEASE'
    }

    @Unroll
    def 'transitive dep | with requested version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
                "\n\tslf4jVersion = \"$extSlf4jVersion\"")
        buildFile << """
            dependencies {
                implementation "org.slf4j:slf4j-simple$requestedVersion"
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks('org.slf4j'))
        def output = result.output

        then:
        writeOutputToProjectDir(output)
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', extSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', extSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = ''
    }

    @Unroll
    def 'transitive dep | without requested version and forced'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'

        requestedVersion = ''
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with lower requested version and forced to different version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with higher requested version and forced to different version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.8.0-beta4'

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with requested version and forced to same version'() {
        given:
        // in Spring Boot 2.x plugin, the `io.spring.dependency-management` plugin is added for dependency management. We are not including it here.
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', forcedVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', forcedVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '2.1.4.RELEASE'
        extSlf4jVersion = '1.6.0'

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = extSlf4jVersion
    }

}
