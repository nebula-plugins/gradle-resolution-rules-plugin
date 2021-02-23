package nebula.plugin.resolutionrules


import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Unroll

/**
 * Substitutions apply to declared dependencies, not resolved ones
 * See: https://github.com/nebula-plugins/gradle-nebula-integration/issues/50#issuecomment-433934842
 */
class SubstituteRulesWithRangesSpec extends IntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")

        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:apricot:1.0')
                .addModule('test.nebula:apricot:1.2')
                .addModule('test.nebula:apricot:1.4')
                .addModule('test.nebula:apricot:1.4.0-dev.1+mybranch.e1c43c7') // version in the form of <major>.<minor>.<patch>-dev.#+<branchname>.<hash>
                .addModule('test.nebula:apricot:1.6')
                .addModule('test.nebula:apricot:1.8')

                .addModule('test.nebula:apricot:2.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        buildFile << """
                     apply plugin: 'java'
                     apply plugin: 'nebula.resolution-rules'

                     repositories {
                         mavenCentral()
                        ${mavenrepo.mavenRepositoryBlock}
                     }

                     dependencies {
                         resolutionRules files("$rulesJsonFile")
                     }
                     """.stripIndent()

        definePluginOutsideOfPluginBlock = true
        debug = true
        keepFiles = true
    }

    @Unroll
    def 'substitutions apply to declared dependencies when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, substituteToVersion)

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> $substituteToVersion")

        where:
        definedVersion                 | substituteFromRange | substituteToVersion | description
        '1.0'                          | "(,1.4]"            | '1.6'               | "x is less than or equal to"
        '1.0'                          | "(,1.4)"            | '1.6'               | "x is less than"
        '1.8'                          | "[1.6,)"            | '1.4'               | "x is greater than or equal to"
        '1.8'                          | "(1.6,)"            | '1.4'               | "x is greater than"
        '1.4'                          | "(1.2,1.6)"         | '1.8'               | "x is less than and greater than"
        '1.4'                          | "[1.2,1.6]"         | '1.8'               | "x is less than or equal to and greater than or equal to"
        '1.4.0-dev.1+mybranch.e1c43c7' | "[1.2,1.6]"         | '1.8'               | "version string contains a 'plus'"
    }

    @Unroll
    def 'do not substitute declared dependencies outside of range when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, '1.0')

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion\n")

        where:
        definedVersion | substituteFromRange | description
        '1.8'          | "(,1.4]"            | "x is not less than or equal to"
        '1.8'          | "(,1.4)"            | "x is not less than"
        '1.2'          | "[1.6,)"            | "x is not greater than or equal to"
        '1.2'          | "(1.6,)"            | "x is not greater than"
        '1.8'          | "(1.2,1.6)"         | "x is not less than and greater than"
        '1.8'          | "[1.2,1.6]"         | "x is not less than or equal to and greater than or equal to"
    }

    @Unroll
    def 'do not substitute dynamic major.+ dependency when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, substituteToVersion)

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> 1.8\n")

        where:
        definedVersion | substituteFromRange | substituteToVersion | description
        '1.+'          | "[1.6,)"            | '1.4'               | "x is greater than or equal to"
        '1.+'          | "(1.6,)"            | '1.4'               | "x is greater than"
        '1.+'          | "(1.2,2.0)"         | '1.0'               | "x is less than and greater than"
        '1.+'          | "[1.2,2.0]"         | '1.0'               | "x is less than or equal to and greater than or equal to"
    }

    @Unroll
    def 'do not substitute dynamic major.+ dependency outside of range when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, '1.0')

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> 1.8")

        where:
        definedVersion | substituteFromRange | description
        '1.+'          | "(,1.4]"            | "x is not less than or equal to"
        '1.+'          | "(,1.4)"            | "x is not less than"
        '1.+'          | "[2.0,)"            | "x is not greater than or equal to"
        '1.+'          | "(2.0,)"            | "x is not greater than"
        '1.+'          | "(1.2,1.6)"         | "x is not less than and greater than"
        '1.+'          | "[1.2,1.6]"         | "x is not less than or equal to and greater than or equal to"
    }

    @Unroll
    def 'do not substitute dynamic latest.release dependency when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, substituteToVersion)

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> 2.0\n")

        where:
        definedVersion   | substituteFromRange | substituteToVersion | description
        'latest.release' | "[1.6,)"            | '1.4'               | "x is greater than or equal to"
        'latest.release' | "(1.6,)"            | '1.4'               | "x is greater than"
    }

    @Unroll
    def 'do not substitute dynamic latest.release dependency outside of range when #description'() {
        given:
        createSubstitutionRule(substituteFromRange, '1.0')

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:apricot:$definedVersion'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> 2.0\n")

        where:
        definedVersion   | substituteFromRange | description
        'latest.release' | "(,1.4]"            | "x is not less than or equal to"
        'latest.release' | "(,1.4)"            | "x is not less than"
        'latest.release' | "(1.2,1.6)"         | "x is not less than and greater than"
        'latest.release' | "[1.2,1.6]"         | "x is not less than or equal to and greater than or equal to"
    }

    @Unroll
    def 'substitutions apply to transitive dependencies where #description'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:blueberry:5.0').addDependency("test.nebula:apricot:$definedVersion").build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        createSubstitutionRule(substituteFromRange, substituteToVersion)

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:blueberry:5.0'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion -> $substituteToVersion")

        where:
        definedVersion                 | substituteFromRange | substituteToVersion | description
        '1.0'                          | "(,1.4]"            | '1.6'               | "x is less than or equal to"
        '1.0'                          | "(,1.4)"            | '1.6'               | "x is less than"
        '1.8'                          | "[1.6,)"            | '1.4'               | "x is greater than or equal to"
        '1.8'                          | "(1.6,)"            | '1.4'               | "x is greater than"
        '1.4'                          | "(1.2,1.6)"         | '1.8'               | "x is less than and greater than"
        '1.4'                          | "[1.2,1.6]"         | '1.8'               | "x is less than or equal to and greater than or equal to"
        '1.4.0-dev.1+mybranch.e1c43c7' | "[1.2,1.6]"         | '1.8'               | "version string contains a 'plus'"
    }

    @Unroll
    def 'do not substitute transitive declared dependencies outside of range when #description'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:blueberry:5.0').addDependency("test.nebula:apricot:$definedVersion").build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        createSubstitutionRule(substituteFromRange, '1.0')

        buildFile << """
                     dependencies {
                        implementation 'test.nebula:blueberry:5.0'
                     }
                     """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'apricot')

        then:
        result.output.contains("test.nebula:apricot:$definedVersion\n")

        where:
        definedVersion | substituteFromRange | description
        '1.8'          | "(,1.4]"            | "x is not less than or equal to"
        '1.8'          | "(,1.4)"            | "x is not less than"
        '1.2'          | "[1.6,)"            | "x is not greater than or equal to"
        '1.2'          | "(1.6,)"            | "x is not greater than"
        '1.8'          | "(1.2,1.6)"         | "x is not less than and greater than"
        '1.8'          | "[1.2,1.6]"         | "x is not less than or equal to and greater than or equal to"
    }

    private File createSubstitutionRule(String substituteFromRange, substituteToVersion) {
        assert substituteFromRange != null

        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module": "test.nebula:apricot:$substituteFromRange",
                        "with": "test.nebula:apricot:$substituteToVersion",
                        "reason" : "this version is better",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()
    }
}
