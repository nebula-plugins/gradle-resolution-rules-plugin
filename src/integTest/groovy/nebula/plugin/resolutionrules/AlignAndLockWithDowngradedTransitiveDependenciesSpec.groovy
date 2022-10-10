package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Ignore
import spock.lang.Unroll

class AlignAndLockWithDowngradedTransitiveDependenciesSpec extends IntegrationTestKitSpec {
    def rulesJsonFile
    static def STATIC_MAJOR_MINOR_PATCH_2_9_9 = "2.9.9"
    static def STATIC_MAJOR_MINOR_PATCH_MICRO_PATCH_2_9_9_3 = "2.9.9.3"
    static def DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS = "2.9.+"
    static def DYNAMIC_RANGE = "[2.9.9,2.10.0)"

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            buildscript {
                repositories {
                    maven {
                        url "https://plugins.gradle.org/m2/"
                    }
                }
                dependencies {
                    classpath "com.netflix.nebula:gradle-dependency-lock-plugin:12.+"
                }
            }
            plugins {
                id 'com.netflix.nebula.resolution-rules'
                id 'java'
            }
            apply plugin: 'nebula.dependency-lock'
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        keepFiles = true
        debug = true

        rulesJsonFile << jacksonAlignmentAndSubstitutionRule()
    }

    @Unroll
    def 'use downgraded version via a static major.minor.patch force on a transitive dependency | core locking #coreLocking'() {
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    force 'com.fasterxml.jackson.core:jackson-core:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsNotUsed(results.output, afterLockingResults.output, '2.9.9')

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via a dynamic major.minor.+ force on a transitive dependency | core locking #coreLocking'() {
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    force 'com.fasterxml.jackson.core:jackson-core:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.10')
        dependenciesAreAligned(afterLockingResults.output, '2.9.10')
        micropatchVersionIsNotUsed(results.output, afterLockingResults.output, '2.9.10')

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via a static major.minor.patch.micropatch forces on a transitive dependency| core locking #coreLocking'() {
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    force 'com.fasterxml.jackson.core:jackson-databind:$STATIC_MAJOR_MINOR_PATCH_MICRO_PATCH_2_9_9_3'
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsUsed(results.output, afterLockingResults.output, '2.9.9') // hurray!

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via a static major.minor.patch strict constraint on a transitive dependency| core locking #coreLocking'() {
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            dependencies {
                implementation('com.fasterxml.jackson.core:jackson-core') {
                    version { strictly '$STATIC_MAJOR_MINOR_PATCH_2_9_9' } // add constraint
                }
                // add dependencies at the constraint version to the dependency graph
                implementation 'com.fasterxml.jackson.core:jackson-annotations:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.core:jackson-core:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.core:jackson-databind:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-smile:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.datatype:jackson-datatype-guava:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.datatype:jackson-datatype-joda:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
                implementation 'com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$STATIC_MAJOR_MINOR_PATCH_2_9_9'
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsNotUsed(results.output, afterLockingResults.output, '2.9.9')

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via a dynamic major.minor.+ strict constraint on a transitive dependency| core locking #coreLocking'() {
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            dependencies {
                implementation('com.fasterxml.jackson.core:jackson-core') {
                    version {
                        strictly '$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS' // add constraint
                    }
                }
                // add dependencies at the constraint version to the dependency graph
                implementation 'com.fasterxml.jackson.core:jackson-annotations:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.core:jackson-core:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.core:jackson-databind:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-smile:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.datatype:jackson-datatype-guava:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.datatype:jackson-datatype-joda:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
                implementation 'com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS'
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.10')
        dependenciesAreAligned(afterLockingResults.output, '2.9.10')
        micropatchVersionIsNotUsed(results.output, afterLockingResults.output, '2.9.10')

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via matching forces with static major.minor.patch version| core locking #coreLocking'() {
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    eachDependency { DependencyResolveDetails details ->
                        if (details.requested.group.startsWith('com.fasterxml.jackson')) {
                            details.useVersion "$STATIC_MAJOR_MINOR_PATCH_2_9_9"
                        }
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsUsed(results.output, afterLockingResults.output, '2.9.9') // hurray!

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via matching forces with dynamic major.minor.+ version| core locking #coreLocking'() {
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    eachDependency { DependencyResolveDetails details ->
                        if (details.requested.group.startsWith('com.fasterxml.jackson')) {
                            details.useVersion "$DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS"
                        }
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.10')
        dependenciesAreAligned(afterLockingResults.output, '2.9.10')
        micropatchVersionIsUsed(results.output, afterLockingResults.output, '2.9.10') // hurray!

        where:
        coreLocking << [false, true]
    }

    @Unroll
    def 'use downgraded version via virtual platform constraint with static major.minor.patch version | core locking #coreLocking'() {
        // note: platform constraints like this are only possible with core Gradle alignment
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            dependencies {
                constraints {
                    implementation("aligned-platform:${moduleName}-0-for-com.fasterxml.jackson.core-or-dataformat-or-datatype-or-jaxrs-or-jr-or-module") {
                        version { strictly("$STATIC_MAJOR_MINOR_PATCH_2_9_9") }
                        because("this version is required for compatibility")
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsNotUsed(results.output, afterLockingResults.output, '2.9.9')

        where:
        coreLocking | _
        false       | _
        true        | _
    }

    @Unroll
    @Ignore("This does not end up with aligned dependencies. This is raised to Gradle")
    def 'use downgraded version via virtual platform constraint with static major.minor.patch.micropatch version | core locking #coreLocking'() {
        // note: platform constraints like this are only possible with core Gradle alignment
        given:
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            dependencies {
                constraints {
                    implementation("aligned-platform:${moduleName}-0-for-com.fasterxml.jackson.core-or-dataformat-or-datatype-or-jaxrs-or-jr-or-module") {
                        version { strictly("$STATIC_MAJOR_MINOR_PATCH_MICRO_PATCH_2_9_9_3") }
                        because("this version is required for compatibility")
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasks(*insightTasks(coreLocking))
        runTasks(*lockingTasks(coreLocking))
        def afterLockingResults = runTasks(*insightTasks(coreLocking))

        then:
        dependenciesAreAligned(results.output, '2.9.9')
        dependenciesAreAligned(afterLockingResults.output, '2.9.9')
        micropatchVersionIsUsed(results.output, afterLockingResults.output, '2.9.9')

        where:
        coreLocking | _
        false       | _
        true        | _
    }

    @Unroll
    def 'use downgraded version via virtual platform constraint with dynamic version #version'() {
        // note: platform constraints like this are only possible with core Gradle alignment
        // this test verifies the current non-working behavior so that we can track when it changes
        setupDependenciesAndAdjustBuildFile()
        buildFile << """
            dependencies {
                constraints {
                    implementation("aligned-platform:${moduleName}-0-for-com.fasterxml.jackson.core-or-dataformat-or-datatype-or-jaxrs-or-jr-or-module") {
                        version { strictly("$version") }
                        because("this version is required for compatibility")
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasksAndFail('dependencyInsight', '--dependency', 'com.fasterxml.jackson', "--single-path")

        then:
        results.output.contains('> fromIndex = -1')

        where:
        version                           | _
        DYNAMIC_MAJOR_MINOR_PLUS_2_9_PLUS | _
        DYNAMIC_RANGE                     | _
    }

    private static def insightTasks(boolean coreLocking) {
        ['dependencies', '--configuration', 'compileClasspath', *flags(coreLocking)]
    }

    private static def lockingTasks(boolean coreLocking) {
        if (coreLocking) {
            return ['dependencies', '--write-locks', '--configuration', 'compileClasspath', *flags(coreLocking)]
        }
        return ['generateLock', 'saveLock', *flags(coreLocking)]
    }

    private static def flags(boolean coreLocking) {
        return ["-Dnebula.features.coreLockingSupport=${coreLocking}"]
    }

    private void setupDependenciesAndAdjustBuildFile(String version = "2.10.5") {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:apricot:1.0.0')
                        .addDependency("com.fasterxml.jackson.core:jackson-annotations:$version")
                        .addDependency("com.fasterxml.jackson.core:jackson-core:$version")
                        .addDependency("com.fasterxml.jackson.core:jackson-databind:$version")
                        .addDependency("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$version")
                        .addDependency("com.fasterxml.jackson.dataformat:jackson-dataformat-smile:$version")
                        .addDependency("com.fasterxml.jackson.datatype:jackson-datatype-guava:$version")
                        .addDependency("com.fasterxml.jackson.datatype:jackson-datatype-joda:$version")
                        .addDependency("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$version")
                        .build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "$projectDir/testrepogen").generateTestMavenRepo()

        buildFile << """
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
                mavenCentral()
            }
            dependencies {
                implementation 'test.nebula:apricot:1.0.0'
            }
        """.stripIndent()
    }

    private static String jacksonAlignmentAndSubstitutionRule() {
        '''\
            {
                "align": [
                        {
                            "group": "com\\\\.fasterxml\\\\.jackson\\\\.(core|dataformat|datatype|jaxrs|jr|module)",
                            "excludes": [
                                "jackson-datatype-jdk7",
                                "jackson-module-scala_2.12.0-RC1",
                                "jackson-module-scala_2.12.0-M5",
                                "jackson-module-scala_2.12.0-M4",
                                "jackson-module-scala_2.9.3",
                                "jackson-module-scala_2.9.2",
                                "jackson-module-scala_2.9.1",
                                "jackson-module-swagger",
                                "jackson-module-scala",
                                "jackson-datatype-hibernate",
                                "jackson-dataformat-ion"
                            ],
                            "includes": [],
                            "reason": "Align all Jackson libraries",
                            "match": "^(\\\\d+\\\\.)?(\\\\d+\\\\.)?(\\\\*|\\\\d+)?(\\\\.pr\\\\d+)?",
                            "author": "author",
                            "date": "2016-05-19"
                        }
                ],
                "replace": [],
                "substitute": [
                    {
                        "module": "com.fasterxml.jackson.core:jackson-databind:[2.9.9,2.9.9.3)",
                        "with": "com.fasterxml.jackson.core:jackson-databind:2.9.9.3",
                        "reason": "There is a vulnerability, see...",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "deny": [],
                "exclude": [],
                "reject": []
            }
        '''.stripIndent()
    }

    private static void dependenciesAreAligned(String output, String alignedVersion) {
        assert output.findAll("com.fasterxml.jackson.core:jackson-annotations:.*$alignedVersion\n").size() > 0
        assert output.findAll("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:.*$alignedVersion\n").size() > 0
        assert output.findAll("com.fasterxml.jackson.dataformat:jackson-dataformat-smile:.*$alignedVersion\n").size() > 0
        assert output.findAll("com.fasterxml.jackson.datatype:jackson-datatype-guava:.*$alignedVersion\n").size() > 0
        assert output.findAll("com.fasterxml.jackson.datatype:jackson-datatype-joda:.*$alignedVersion\n").size() > 0
        assert output.findAll("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:.*$alignedVersion\n").size() > 0

        assert output.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion\n").size() > 0 ||
                output.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion.[0-9]+\n").size() > 0

    }

    private static void micropatchVersionIsUsed(String output1, String output2, String alignedVersion) {
        assert output1.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion.[0-9]+\n").size() > 0
        assert output2.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion.[0-9]+\n").size() > 0
    }

    private static void micropatchVersionIsNotUsed(String output1, String output2, String alignedVersion) {
        assert output1.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion.[0-9]+\n").size() == 0
        assert output2.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion.[0-9]+\n").size() == 0

        assert output1.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion\n").size() > 0
        assert output2.findAll("com.fasterxml.jackson.core:jackson-databind:.*$alignedVersion\n").size() > 0
    }
}
