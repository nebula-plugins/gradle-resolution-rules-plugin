package nebula.plugin.resolutionrules


import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import nebula.test.dependencies.maven.ArtifactType
import nebula.test.dependencies.maven.Pom
import nebula.test.dependencies.repositories.MavenRepo
import spock.lang.Unroll

class AlignedPlatformRulesSpec extends IntegrationTestKitSpec {
    File rulesJsonFile
    File mavenrepo
    String reason = "★ custom reason"

    String alignRuleForTestNebula = """\
        {
            "group": "(test.nebula|test.nebula.ext)",
            "reason": "Align test.nebula dependencies",
            "author": "Example Person <person@example.org>",
            "date": "2016-03-17T20:21:20.368Z"
        }""".stripIndent()

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        settingsFile << """\
            rootProject.name = 'test-project'
            """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:0.5.0')

                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.0.2')
                .addModule('test.nebula:a:1.0.3')
                .addModule('test.nebula:a:1.1.0')

                .addModule('test.nebula:b:0.5.0')

                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.0.2')
                .addModule('test.nebula:b:1.0.3')
                .addModule('test.nebula:b:1.1.0')

                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.0.1')
                .addModule('test.nebula:c:1.0.2')
                .addModule('test.nebula:c:1.0.3')
                .addModule('test.nebula:c:1.1.0')

                .addModule('test.nebula:c:1.4.0')

                .addModule('test.beverage:d:1.0.0')
                .addModule(new ModuleBuilder('test.other:e:1.0.0').addDependency('test.nebula:b:1.1.0').build())

                .addModule(new ModuleBuilder('test.nebula:f:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:f:1.0.1').addDependency('test.nebula:a:1.0.1').build())
                .addModule(new ModuleBuilder('test.nebula:f:1.0.2').addDependency('test.nebula:a:1.0.2').build())
                .addModule(new ModuleBuilder('test.nebula:f:1.0.3').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.nebula:f:1.1.0').addDependency('test.nebula:a:1.1.0').build())

                .addModule('test.nebula:g:1.0.0')
                .addModule('test.nebula:g:1.0.1')
                .addModule('test.nebula:g:1.0.2')
                .addModule('test.nebula:g:1.0.3')
                .addModule('test.nebula:g:1.1.0')

                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << baseBuildGradleFile()

        debug = true
        keepFiles = true
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from static version to higher static version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.1"               | "1.0.3"             | "higher"           | false         | "1.0.3"
        "static version"      | "1.0.1"               | "1.0.3"             | "higher"           | true          | "1.0.3"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from static version to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.1"               | "latest.release"    | "higher"           | false         | "1.1.0"
        "static version"      | "1.0.1"               | "latest.release"    | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from static version to higher minor-scoped dynamic version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.1"               | "1.+"               | "higher"           | false         | "1.1.0"
        "static version"      | "1.0.1"               | "1.+"               | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'statically defined dependency: core alignment should substitute and align from static version to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.1"               | "1.0.0"             | "lower"            | false         | "1.0.1"
        "static version"      | "1.0.1"               | "1.0.0"             | "lower"            | true          | "1.0.0"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from range to higher static version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "[1.0.1,1.0.2]"       | "1.0.3"             | "higher"           | false         | "1.0.3"
        "range"               | "[1.0.1,1.0.2]"       | "1.0.3"             | "higher"           | true          | "1.0.3"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from range to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "[1.0.1,1.0.2]"       | "latest.release"    | "higher"           | false         | "1.1.0"
        "range"               | "[1.0.1,1.0.2]"       | "latest.release"    | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from range to higher major-scoped dynamic version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "[1.0.1,1.0.2]"       | "1.+"               | "higher"           | false         | "1.1.0"
        "range"               | "[1.0.1,1.0.2]"       | "1.+"               | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'statically defined dependency: alignment styles should substitute and align from range to higher static version with higher minor version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "[1.0.1,1.0.2]"       | "1.1.0"             | "higher"           | false         | "1.1.0"
        "range"               | "[1.0.1,1.0.2]"       | "1.1.0"             | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'statically defined dependency: core alignment should substitute and align from range to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.1"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "[1.0.1,1.0.2]"       | "1.0.0"             | "lower"            | false         | "1.0.1"
        "range"               | "[1.0.1,1.0.2]"       | "1.0.0"             | "lower"            | true          | "1.0.0"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: core alignment should substitute and align from static version to higher static version that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.3"               | "1.1.0"             | "higher"           | false         | "1.0.3"
        "static version"      | "1.0.3"               | "1.1.0"             | "higher"           | true          | "1.0.2"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: core alignment should substitute and align from static version to higher latest.release dynamic version in narrow definition that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.3"               | "latest.release"    | "higher"           | false         | "1.0.3"
        "static version"      | "1.0.3"               | "latest.release"    | "higher"           | true          | "1.0.2"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: core alignment should substitute and align from static version to higher major-scoped dynamic version that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.3"               | "1.+"               | "higher"           | false         | "1.0.3"
        "static version"      | "1.0.3"               | "1.+"               | "higher"           | true          | "1.0.2"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: core alignment should substitute and align from static version to conflict-resolved version that is is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "static version"      | "1.0.3"               | "1.0.0"             | "lower"            | false         | "1.0.3"
        "static version"      | "1.0.3"               | "1.0.0"             | "lower"            | true          | "1.0.2"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: alignment styles should substitute and align from range to higher static version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "(,1.1.0)"            | "1.1.0"             | "higher"           | false         | "1.1.0"
        "range"               | "(,1.1.0)"            | "1.1.0"             | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: alignment styles should substitute and align from range to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "(,1.1.0)"            | "latest.release"    | "higher"           | false         | "1.1.0"
        "range"               | "(,1.1.0)"            | "latest.release"    | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: alignment styles should substitute and align from range to higher static version with higher minor version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "(,1.1.0)"            | "1.+"               | "higher"           | false         | "1.1.0"
        "range"               | "(,1.1.0)"            | "1.+"               | "higher"           | true          | "1.1.0"
    }

    @Unroll
    def 'narrowly defined dynamic dependency: core alignment should substitute and align from range to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        def dependencyDefinitionVersion = "1.0.+"
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        substituteVersionType | substituteFromVersion | substituteToVersion | substituteUpOrDown | coreAlignment | resultingVersion
        "range"               | "(,1.1.0)"            | "0.5.0"             | "lower"            | false         | "1.0.3"
        "range"               | "(,1.1.0)"            | "0.5.0"             | "lower"            | true          | "0.5.0"
    }

    @Unroll
    def 'missing cases: statically defined dependency: core alignment fails to align when lower versions are missing | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "static version"   | "1.0.1"        | "range"        | "b:[1.0.0,1.1.0)"       | "b:0.5.0"             | "lower"     | false         | "1.0.1"            | "1.0.1"
        "static version"   | "1.0.1"        | "range"        | "b:[1.0.0,1.1.0)"       | "b:0.5.0"             | "lower"     | true          | "0.5.0"            | "FAILED"
    }

    @Unroll
    def 'missing cases: statically defined dependency: core alignment fails to align when higher versions are missing | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "static version"   | "1.0.1"        | "range"        | "c:[1.0.0,1.1.0)"       | "c:1.4.0"             | "higher"    | false         | "1.1.0"            | "1.4.0"
        "static version"   | "1.0.1"        | "range"        | "c:[1.0.0,1.1.0)"       | "c:1.4.0"             | "higher"    | true          | "FAILED"           | "1.4.0"
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: core alignment fails to align when dependency definition and substitutions leave no viable versions | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "range"            | "1.+"          | "range"        | "b:[1.0.0,)"            | "b:0.5.0"             | "lower"     | false         | "1.1.0"            | "1.4.0"
        "range"            | "1.+"          | "range"        | "b:[1.0.0,)"            | "b:0.5.0"             | "lower"     | true          | "FAILED"           | "FAILED"
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: core alignment fails to align when dependency definition and substitutions leave no viable versions for some lower aligned dependencies | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion   | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "range"            | "latest.release" | "range"        | "b:[1.0.0,)"            | "b:0.5.0"             | "lower"     | false         | "1.1.0"            | "1.4.0"
        "range"            | "latest.release" | "range"        | "b:[1.0.0,)"            | "b:0.5.0"             | "lower"     | true          | "0.5.0"            | "FAILED"
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: core alignment fails to align when dependency definition and substitutions leave no viable versions for some higher aligned dependencies | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "range"            | "1.+"          | "range"        | "c:[1.0.0,1.2.0)"       | "c:1.4.0"             | "higher"    | false         | "1.1.0"            | "1.4.0"
        "range"            | "1.+"          | "range"        | "c:[1.0.0,1.2.0)"       | "c:1.4.0"             | "higher"    | true          | "FAILED"           | "1.4.0"
    }

    @Unroll
    def 'missing cases: narrowly defined dynamic dependency: core alignment fails to align when dependency definition and substitutions leave no viable versions for some lower aligned dependencies | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "narrow range"     | "1.0.+"        | "range"        | "b:[1.0.0,1.1.0)"       | "b:0.5.0"             | "lower"     | false         | "1.0.3"            | "1.0.3"
        "narrow range"     | "1.0.+"        | "range"        | "b:[1.0.0,1.1.0)"       | "b:0.5.0"             | "lower"     | true          | "FAILED"           | "FAILED"
    }

    @Unroll
    def 'missing cases: narrowly defined dynamic dependency: core alignment fails to align when dependency definition and substitutions leave no viable versions for some higher aligned dependencies | core alignment: #coreAlignment'() {
        given:
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(subFromVersionAndModule, subToVersionAndModule, definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment && ABResultingVersion != "FAILED" && CResultingVersion != "FAILED") {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$ABResultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | ABResultingVersion | CResultingVersion
        "narrow range"     | "1.0.+"        | "range"        | "c:[1.0.0,1.1.0)"       | "c:1.4.0"             | "higher"    | false         | "1.0.3"            | "1.0.3"
        "narrow range"     | "1.0.+"        | "range"        | "c:[1.0.0,1.1.0)"       | "c:1.4.0"             | "higher"    | true          | "FAILED"           | "FAILED"
    }

    @Unroll
    def 'do not apply substitution to group if the specified dependency is not in the graph | core alignment: #coreAlignment'() {
        given:
        def module = "test.nebula:$subFromVersionAndModule"
        def with = "test.nebula:$subToVersionAndModule"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.nebula:a:$definedVersion'
                compile 'test.nebula:b:$definedVersion'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        definedVersionType | definedVersion | subVersionType | subFromVersionAndModule | subToVersionAndModule | subUpOrDown | coreAlignment | resultingVersion
        "range"            | "1.+"          | "range"        | "c:[1.0.0,1.2.0)"       | "c:1.4.0"             | "higher"    | false         | "1.1.0"
        "range"            | "1.+"          | "range"        | "c:[1.0.0,1.2.0)"       | "c:1.4.0"             | "higher"    | true          | "1.1.0"
    }

    @Unroll
    def 'substitute static version for other dependency latest.release and align direct deps | core alignment #coreAlignment'() {
        given:
        def module = "test.beverage:d:1.0.0"
        def with = "test.nebula:b:latest.release"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.beverage:d:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'substitute all versions for another dependency with static version and align direct and transitives higher | core alignment #coreAlignment'() {
        given:
        def module = "test.other:e"
        def with = "test.nebula:b:1.1.0"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.other:e:1.0.0'
                compile 'test.nebula:a:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        result.output.contains('test.other:e:1.0.0 -> test.nebula:b:1.1.0')
        result.output.contains('test.nebula:a:1.0.0 -> 1.1.0')

        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.other:e:1.0.0 -> test.nebula:b", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'substitute all versions for another dependency with static version and align direct and transitives lower | core alignment #coreAlignment'() {
        given:
        def module = "test.other:e"
        def with = "test.nebula:b:1.0.3"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.other:e:1.0.0'
                compile 'test.nebula:a:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def resultingVersion = "1.0.3"
        dependencyInsightContains(result.output, "test.other:e:1.0.0 -> test.nebula:b", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'apply a static version via a force and align results (no substitutions) | core alignment #coreAlignment'() {
        given:
        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                compile 'test.nebula:a:latest.release'
                compile 'test.nebula:b:latest.release'
            }
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:1.0.2'
                }
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def resultingVersion = "1.0.2"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'apply a static version via details.useVersion and align results #description | core alignment #coreAlignment'() {
        // This is not using a substitution rule, so alignment is not totally taking place, as some versions are not rejected
        // TODO: See about reading in the resolution strategies to catch cases like this

        given:
        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                compile 'test.nebula:a:1.0.3'
                compile 'test.nebula:b:1.0.0'
                compile 'test.nebula:c:1.0.3'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'a') {
                        details.useVersion '1.0.1'
                        details.because('$reason')
                    }
                }
            }
            """.stripIndent()

        when:

        def result = runTasks(*tasks(coreAlignment))

        then:
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersionForConstrainedVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersionForNonConstrainedVersion)
        dependencyInsightContains(result.output, "test.nebula:c", resultingVersionForNonConstrainedVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersionForNonConstrainedVersion")
        }


        where:
        coreAlignment | resultingVersionForConstrainedVersion | resultingVersionForNonConstrainedVersion | description
        false         | "1.0.3"                               | "1.0.3"                                  | "aligns all dependencies"
        true          | "1.0.1"                               | "1.0.3"                                  | "does not align all dependencies"
    }

    @Unroll
    def 'statically defined dependency: alignment styles substitute all versions higher than x and align | core alignment #coreAlignment'() {
        given:
        // based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/50
        setupForGuiceAndLibraryDependency(definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment, false, 'com.google.inject'))

        then:
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-assistedinject", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-grapher", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-multibindings", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject:guice", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("com.google.inject:guice:{require 4.1.0; reject [4.2.0,)}")

            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | definedVersion | resultingVersion
        false         | '4.2.0'        | '4.1.0'
        true          | '4.2.0'        | '4.1.0'
    }

    @Unroll
    def 'dynamically defined dependency: core alignment substitutes all versions higher than x and aligns | core alignment #coreAlignment'() {
        given:
        // based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/50
        setupForGuiceAndLibraryDependency(definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment, false, 'com.google.inject'))

        then:
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-assistedinject", resultingAlignedVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-grapher", resultingAlignedVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-multibindings", resultingAlignedVersion)

        if (resultingNonAlignedVersion != null) {
            // since this test uses an external dependency that keeps incrementing, let's check that it just doesn't get the same result
            def content = "com.google.inject:guice:.*$resultingAlignedVersion\n"
            assert result.output.findAll(content).size() == 0

            // just make sure there's a value here for dependencyInsight
            dependencyInsightContains(result.output, "com.google.inject:guice", '')
        } else {
            dependencyInsightContains(result.output, "com.google.inject:guice", resultingAlignedVersion)
        }

        if (coreAlignment) {
            assert result.output.contains("com.google.inject:guice:{require 4.1.0; reject [4.2.0,)}")

            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingAlignedVersion")
        }

        where:
        coreAlignment | definedVersion | resultingAlignedVersion | resultingNonAlignedVersion
        false         | '4.+'          | '4.1.0'                 | '4.2.2'
        true          | '4.+'          | '4.1.0'                 | null
    }

    @Unroll
    def 'transitive dependencies are aligned with same constraints as direct dependencies | core alignment #coreAlignment'() {
        given:
        def module = "test.nebula:f:[1.0.1,1.1.0)"
        def with = "test.nebula:f:1.0.0"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.nebula:f:1.0.3'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def resultingVersion = "1.0.0"
        dependencyInsightContains(result.output, "test.nebula:f", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)

        if (coreAlignment) {
            // for direct dependency
            assert result.output.contains("test.nebula:f:{require 1.0.3; reject [1.0.1,1.1.0)}")

            // for transitive dependencies
            assert result.output.contains("test.nebula:a:{require 1.0.0; reject [1.0.1,1.1.0)}")

            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'substitution rule excludes are honored | core alignment #coreAlignment'() {
        given:
        def module = "test.nebula:a:[1.0.0,1.0.3)"
        def with = "test.nebula:a:1.0.3"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "$module",
                        "with" : "$with",
                        "reason" : "$reason",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    }
                ],
                "align": [
                   {
                        "group": "(test.nebula|test.nebula.ext)",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "includes": [],
                        "excludes": ["(b|c)"],
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                compile 'test.nebula:a:1.0.1'
                compile 'test.nebula:b:1.0.1'
                compile 'test.nebula:c:1.0.2'
                compile 'test.nebula:g:1.0.1'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def alignedResultingVersion = "1.0.3"
        dependencyInsightContains(result.output, "test.nebula:a", alignedResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:g", alignedResultingVersion)

        dependencyInsightContains(result.output, "test.nebula:b", '1.0.1')
        dependencyInsightContains(result.output, "test.nebula:c", '1.0.2')

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$alignedResultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'substitution rule includes are honored | core alignment #coreAlignment'() {
        given:
        def module = "test.nebula:a:[1.0.0,1.0.3)"
        def with = "test.nebula:a:1.0.3"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "$module",
                        "with" : "$with",
                        "reason" : "$reason",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    }
                ],
                "align": [
                   {
                        "group": "(test.nebula|test.nebula.ext)",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "includes": ["(a|g)"],
                        "excludes": [],
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                compile 'test.nebula:a:1.0.1'
                compile 'test.nebula:b:1.0.1'
                compile 'test.nebula:c:1.0.2'
                compile 'test.nebula:g:1.0.1'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        def alignedResultingVersion = "1.0.3"
        dependencyInsightContains(result.output, "test.nebula:a", alignedResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:g", alignedResultingVersion)

        dependencyInsightContains(result.output, "test.nebula:b", '1.0.1')
        dependencyInsightContains(result.output, "test.nebula:c", '1.0.2')

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$alignedResultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: core alignment should substitute and align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.1"             | "1.0.2"          | false
        true          | "1.0.2"    | true                                  | "1.0.1"             | "1.0.1"          | false
    }

    @Unroll
    def 'recs with core bom support disabled: core alignment should substitute and align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.+"               | "1.0.2"          | false
        true          | "1.0.2"    | true                                  | "1.+"               | "1.1.0"          | false
    }

    @Unroll
    def 'recs with core bom support disabled: core alignment should substitute and align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.3"             | "1.0.2"          | false
        true          | "1.0.2"    | true                                  | "1.0.3"             | "1.0.3"          | false
    }

    @Unroll
    def 'recs with core bom support disabled: alignment styles should not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | false
        true          | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | false
    }

    @Unroll
    def 'recs with core bom support enabled: core alignment should substitute and align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.1"             | "1.0.2"          | true
        true          | "1.0.2"    | true                                  | "1.0.1"             | "1.0.1"          | true
    }

    @Unroll
    def 'recs with core bom support enabled: core alignment should substitute and align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.+"               | "1.0.2"          | true
        true          | "1.0.2"    | true                                  | "1.+"               | "1.1.0"          | true
    }

    @Unroll
    def 'recs with core bom support enabled: core alignment should substitute and align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.3"             | "1.0.2"          | true
        true          | "1.0.2"    | true                                  | "1.0.3"             | "1.0.3"          | true
    }

    @Unroll
    def 'recs with core bom support enabled: alignment styles should not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | true
        true          | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | true
    }

    @Unroll
    def 'enforced recs with core bom support disabled: core alignment should substitute and align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.1"             | "1.0.2"          | false
        true          | "1.0.2"    | true                                  | "1.0.1"             | "1.0.1"          | false
    }

    @Unroll
    def 'enforced recs with core bom support disabled: alignment styles should not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | false
        true          | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | false
    }

    @Unroll
    def 'enforced recs with core bom support enabled: core alignment should substitute and align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.2"    | true                                  | "1.0.1"             | "1.0.2"          | true
        true          | "1.0.2"    | true                                  | "1.0.1"             | "1.0.1"          | true
    }

    @Unroll
    def 'enforced recs with core bom support enabled: alignment styles should not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        assert result.output.findAll("test.nebula:a:$resultingVersion").size() >= 1
        assert result.output.findAll("test.nebula:b:$resultingVersion").size() >= 1

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0:$resultingVersion")
        }

        where:
        coreAlignment | bomVersion | bomVersionCollidesWithSubsitutionRule | substituteToVersion | resultingVersion | coreBomSupport
        false         | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | true
        true          | "1.0.0"    | false                                 | "1.0.1"             | "1.0.0"          | true
    }

    private String baseBuildGradleFile(String additionalPlugin = '') {
        def pluginToAdd = ''
        if (additionalPlugin != '') {
            pluginToAdd = "\n\tid $additionalPlugin"
        }
        """
        plugins {
            id 'java'
            id 'nebula.resolution-rules'$pluginToAdd
        }
        repositories {
            maven { url '${projectDir.toPath().relativize(mavenrepo.toPath()).toFile()}' }
        }
        dependencies {
            resolutionRules files("${projectDir.toPath().relativize(rulesJsonFile.toPath()).toFile()}")
        }
        """.stripIndent()
    }

    private def setupForSimplestSubstitutionAndAlignmentCases(String substituteFromVersion, String substituteToVersion, String dependencyDefinitionVersion) {
        def module = "test.nebula:b:$substituteFromVersion"
        def with = "test.nebula:b:$substituteToVersion"

        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.nebula:a:$dependencyDefinitionVersion'
                compile 'test.nebula:b:$dependencyDefinitionVersion'
            }
            """.stripIndent()
    }

    private def setupForSubstitutionAndAlignmentCasesWithMissingVersions(String subFromVersionAndModule, String subToVersionAndModule, String definedVersion) {
        def module = "test.nebula:$subFromVersionAndModule"
        def with = "test.nebula:$subToVersionAndModule"
        createAlignAndSubstituteRule(module, with)

        buildFile << """
            dependencies {
                compile 'test.nebula:a:$definedVersion'
                compile 'test.nebula:b:$definedVersion'
                compile 'test.nebula:c:$definedVersion'
            }
            """.stripIndent()
    }

    private def setupForBomAndAlignmentAndSubstitution(String bomVersion, String substituteToVersion, boolean usingEnforcedPlatform = false) {
        def module = "test.nebula:a:[1.0.2]"
        def with = "test.nebula:a:$substituteToVersion"
        createAlignAndSubstituteRule(module, with)

        def bomRepo = createBom(["test.nebula:a:$bomVersion", "test.nebula:b:$bomVersion"])

        buildFile.text = ""
        buildFile << """
            buildscript {
                repositories { jcenter() }
            }
            """.stripIndent()
        buildFile << baseBuildGradleFile("'nebula.dependency-recommender' version '7.5.5\'")


        if (!usingEnforcedPlatform) {
            buildFile << """
            dependencyRecommendations {
                mavenBom module: 'test.nebula.bom:testbom:latest.release'
            }
            """.stripIndent()
        } else {
            buildFile << """
            dependencyRecommendations {
                mavenBom module: 'test.nebula.bom:testbom:latest.release', enforced: true
            }
            """.stripIndent()
        }

        buildFile << """
            dependencies {
                compile 'test.nebula:a'
                compile 'test.nebula:b'
            }
            repositories {
                 maven { url '${bomRepo.root.absoluteFile.toURI()}' }
            }
            """.stripIndent()
    }

    def setupForGuiceAndLibraryDependency(String definedVersion) {
        rulesJsonFile << """
            {
                "align": [
                    {
                        "group": "com.google.inject",
                        "reason": "Align guice",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "substitute": [
                    {
                        "module" : "com.google.inject:guice:[4.2.0,)",
                        "with" : "com.google.inject:guice:4.1.0",
                        "reason" : "$reason",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()

        buildFile << """
repositories {
    mavenCentral()
    maven {
        url 'repo'
    }
}

dependencies {
    //at the time of writing resolves to 4.2.2
    compile "com.google.inject:guice:$definedVersion"
    compile "sample:module:1.0"
}
"""

        MavenRepo repo = new MavenRepo()
        repo.root = new File(projectDir, 'repo')
        Pom pom = new Pom('sample', 'module', '1.0', ArtifactType.POM)
        pom.addDependency('com.google.inject.extensions', 'guice-grapher', '4.1.0')
        repo.poms.add(pom)
        repo.generate()
    }

    private def createBom(List<String> dependencies) {
        MavenRepo repo = new MavenRepo()
        repo.root = new File(projectDir, 'build/bomrepo')
        Pom pom = new Pom('test.nebula.bom', 'testbom', '1.0.0', ArtifactType.POM)

        dependencies.each { dependency ->
            def depParts = dependency.split(':')
            assert depParts.size() == 3

            pom.addManagementDependency(depParts[0], depParts[1], depParts[2])
        }

        repo.poms.add(pom)
        repo.generate()
        return repo
    }

    private def createAlignAndSubstituteRule(String module, String with) {
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "$module",
                        "with" : "$with",
                        "reason" : "$reason",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    }
                ],
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()
    }

    private static def tasks(Boolean usingCoreAlignment, Boolean usingCoreBomSupport = false, String groupForInsight = 'test.nebula') {
        return [
                'dependencyInsight',
                '--dependency',
                groupForInsight,
                "-Dnebula.features.coreAlignmentSupport=$usingCoreAlignment",
                "-Dnebula.features.coreBomSupport=$usingCoreBomSupport"
        ]
    }

    private static void dependencyInsightContains(String resultOutput, String groupAndName, String resultingVersion) {
        def content = "$groupAndName:.*$resultingVersion\n"
        assert resultOutput.findAll(content).size() >= 1
    }

}
