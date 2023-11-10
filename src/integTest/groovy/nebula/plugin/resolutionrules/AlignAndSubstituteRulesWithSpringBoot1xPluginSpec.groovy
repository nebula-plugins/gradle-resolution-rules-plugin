package nebula.plugin.resolutionrules

import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Unroll

//Spring boot plugin 1.x is using removed runtime configuration. Unless backported for Gradle 7.0 it cannot be used
@IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
class AlignAndSubstituteRulesWithSpringBoot1xPluginSpec extends AbstractRulesWithSpringBootPluginSpec {
    File rulesJsonFile

    def setup() {
        System.setProperty('ignoreDeprecations', 'true')
        rulesJsonFile = new File(projectDir, "rules.json")
        keepFiles = true
    }

    @Unroll
    def 'direct dep | with provided version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
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
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ''
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | with lower requested version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
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
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | with higher requested version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
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
        extSpringVersion = '5.1.8.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = ''
    }

    @Unroll
    def 'direct dep | without requested version and forced'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
        addSpringDependenciesWhenUsingManagedDependencies(requestedVersion)

        when:
        def result = runTasks(*tasks())
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
    }

    @Unroll
    def 'direct dep | with requested version and forced to different versions'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
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
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = '4.2.9.RELEASE'
    }

    @Unroll
    def 'direct dep | with requested version and forced to the same version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForDirectDependencyScenario(extSpringBootVersion, forcedVersion, '', "\n\tspringVersion = \"$extSpringVersion\"")
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
        extSpringBootVersion = '1.5.6.RELEASE'
        managedSpringVersion = '4.3.10.RELEASE' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$springVersion'
        forcedVersion = extSpringVersion
    }

    @Unroll
    def 'transitive dep | with provided version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
        setupForTransitiveDependencyScenario(extSpringBootVersion, forcedVersion, '',
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
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ''
        forcedVersion = ''
    }

    @Unroll
    def 'transitive dep | with requested version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = ''
    }

    @Unroll
    def 'transitive dep | without requested version and forced'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', managedSlf4jVersion)
        dependencyInsightContains(output, 'org.slf4j:slf4j-api', managedSlf4jVersion)

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ''
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with lower requested version and forced to different version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with higher requested version and forced to different version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.7.26'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = '1.7.10'
    }

    @Unroll
    def 'transitive dep | with requested version and forced to same version'() {
        given:
        // in Spring Boot 1.x plugin, dependency management is added automatically
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
        dependencyInsightContains(output, 'org.slf4j:slf4j-simple', 'FAILED')
        assert output.contains('Multiple forces on different versions for virtual platform aligned-platform')

        where:
        extSpringVersion = '4.2.4.RELEASE'
        extSpringBootVersion = '1.5.6.RELEASE'
        extSlf4jVersion = '1.6.0'
        managedSlf4jVersion = '1.7.25' // from https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/1.5.6.RELEASE/spring-boot-dependencies-1.5.6.RELEASE.pom

        requestedVersion = ':\$slf4jVersion'
        forcedVersion = extSlf4jVersion
    }
}
