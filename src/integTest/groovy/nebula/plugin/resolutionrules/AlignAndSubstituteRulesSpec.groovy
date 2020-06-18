package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import nebula.test.dependencies.maven.ArtifactType
import nebula.test.dependencies.maven.Pom
import nebula.test.dependencies.repositories.MavenRepo
import spock.lang.Issue
import spock.lang.Unroll

class AlignAndSubstituteRulesSpec extends IntegrationTestKitSpec {
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
                .addModule('test.nebula:b:0.6.0')

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
    def 'statically defined dependency: sub & align from static version to higher static version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.1"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from static version to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.1"
        substituteToVersion = "latest.release"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from static version to higher minor-scoped dynamic version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.1"
        substituteToVersion = "1.+"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from static version to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.1"
        substituteToVersion = "1.0.0"
        resultingVersion = "1.0.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from range to higher static version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "[1.0.1,1.0.2]"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from range to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "[1.0.1,1.0.2]"
        substituteToVersion = "latest.release"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from range to higher minor-scoped dynamic version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "[1.0.1,1.0.2]"
        substituteToVersion = "1.+"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from range to higher static version with higher minor version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "[1.0.1,1.0.2]"
        substituteToVersion = "1.1.0"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align from range to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.1', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "[1.0.1,1.0.2]"
        substituteToVersion = "1.0.0"
        resultingVersion = "1.0.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from static version to higher static version that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.3"
        substituteToVersion = "1.1.0"
        resultingVersion = "1.0.3"  // FIXME: should resolve differently
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from static version to higher latest.release dynamic version in narrow definition that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.3"
        substituteToVersion = "latest.release"
        resultingVersion = "1.0.3"  // FIXME: should resolve differently
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from static version to higher minor-scoped dynamic version that is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.3"
        substituteToVersion = "1.+"
        resultingVersion = "1.0.3" // FIXME: should resolve differently
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from static version to conflict-resolved version that is is not substituted-away-from | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "1.0.3"
        substituteToVersion = "1.0.0"
        resultingVersion = "1.0.3" // FIXME: should resolve differently
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from range to higher static version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "(,1.1.0)"
        substituteToVersion = "1.1.0"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from range to higher latest.release dynamic version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "(,1.1.0)"
        substituteToVersion = "latest.release"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from range to higher minor-scoped static version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "(,1.1.0)"
        substituteToVersion = "1.+"
        resultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'narrowly defined dynamic dependency: sub & align from range to lower substitute-to version | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = ['1.0.+', '1.0.0']
        setupForSimplestSubstitutionAndAlignmentCases(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        substituteFromVersion = "(,1.1.0)"
        substituteToVersion = "0.5.0"
        resultingVersion = "1.0.3" // only declared dependencies are substituted. v1.0.+ is not a declared dependency
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: statically defined dependency: fail to align when lower versions are missing | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", AResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", BResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$BResultingVersion")
        }

        where:
        definedVersion = "1.0.1"
        substituteFromVersion = "[1.0.0,1.1.0)"
        substituteToVersion = "0.5.0"
        AResultingVersion = "0.5.0"
        BResultingVersion = '0.6.0'
        CResultingVersion = "FAILED"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: statically defined dependency: fail to align when higher versions are missing | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", AResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", BResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$CResultingVersion")
        }

        where:
        definedVersion = "1.0.1"
        substituteFromVersion = "[1.0.0,1.1.0)"
        substituteToVersion = "1.4.0"
        AResultingVersion = "FAILED"
        BResultingVersion = '0.6.0'
        CResultingVersion = "1.4.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: when dynamic dependency definition and substitutions leave no viable versions | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment) {
            def platformVersion = "1.4.0"
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$platformVersion")
        }

        where:
        definedVersion = "1.+"
        substituteFromVersion = "[1.0.0,)"
        substituteToVersion = "0.5.0"
        ABResultingVersion = "1.1.0"
        CResultingVersion = "1.4.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: when dynamic latest.release dependency definition and substitutions leave no viable versions for some lower aligned versions | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", AResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", BResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment) {
            def platformVersion = "1.4.0"
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$platformVersion")
        }

        where:
        definedVersion = "latest.release"
        substituteFromVersion = "[1.0.0,)"
        substituteToVersion = "0.5.0"
        AResultingVersion = "1.1.0"
        BResultingVersion = "1.1.0"
        CResultingVersion = "1.4.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: dynamically defined dependency: when dependency dynamic definition and substitutions leave no viable versions for some higher aligned dependencies | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", CResultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$CResultingVersion")
        }

        where:
        definedVersion = "1.+"
        substituteFromVersion = "[1.0.0,1.2.0)"
        substituteToVersion = "1.4.0"
        CResultingVersion = "1.4.0"
        ABResultingVersion = "1.1.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: narrowly defined dynamic dependency: when narrow dynamic dependency definition and substitutions leave no viable versions for some lower aligned dependencies | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", ABCResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABCResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", ABCResultingVersion)

        if (coreAlignment) {
            def platformVersion = "1.0.3"
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$platformVersion")
        }

        where:
        definedVersion = "1.0.+"
        substituteFromVersion = "[1.0.0,1.1.0)"
        substituteToVersion = "0.5.0"
        ABCResultingVersion = "1.0.3" // FIXME: should resolve differently
        coreAlignment << [false, true]
    }

    @Unroll
    def 'missing cases: narrowly defined dynamic dependency: when narrow dynamic dependency definition and substitutions leave no viable versions for some higher aligned dependencies | core alignment: #coreAlignment'() {
        given:
        List<String> dependencyDefinitionVersions = [definedVersion, '0.6.0', definedVersion]
        setupForSubstitutionAndAlignmentCasesWithMissingVersions(substituteFromVersion, substituteToVersion, dependencyDefinitionVersions)

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", ABCResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", ABCResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", ABCResultingVersion)

        if (coreAlignment) {
            def platformVersion = "1.0.3"
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$platformVersion")
        }

        where:
        definedVersion = "1.0.+"
        substituteFromVersion = "[1.0.0,1.1.0)"
        substituteToVersion = "1.4.0"
        ABCResultingVersion = "1.0.3" // FIXME: should resolve differently
        coreAlignment << [false, true]
    }


    @Unroll
    def 'substitute static version for other dependency latest.release and align direct deps | core alignment #coreAlignment'() {
        given:
        def module = "test.beverage:d:1.0.0"
        def with = "test.nebula:b:latest.release"
        createAlignAndSubstituteRule([(module.toString()): with])

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.beverage:d:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'substitute all versions for another dependency with static version and align direct and transitives higher | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:h:1.0.0').addDependency('test.nebula:b:1.0.2').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        def module = "test.other:g"
        def with = "test.other:h:1.0.0"
        createAlignAndSubstituteRule([(module.toString()): with])

        buildFile << """
            dependencies {
                implementation 'test.other:g:1.0.0'
                implementation 'test.nebula:a:1.1.0'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        def resultingVersion = "1.1.0"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
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
                implementation 'test.nebula:a:latest.release'
                implementation 'test.nebula:b:latest.release'
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
        writeOutputToProjectDir(result.output)
        def resultingVersion = "1.0.2"
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'only brought in transitively: sub & align from static version to lower static version that is not substituted-away-from | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.2').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.0.3').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        def substituteFromVersion = "[1.0.2,1.1.0]"
        def substituteToVersion = "1.0.1"
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())
        createAlignAndSubstituteRule(modulesAndSubstitutions)

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:latest.release'
                implementation 'test.other:also-brings-a:latest.release'
                implementation 'test.other:brings-b:latest.release'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.1"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'only brought in transitively: core alignment fails with matching static substitution and force: #description | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.1.0').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        def substituteFromVersion = "1.0.3"
        def substituteToVersion = "1.1.0"
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())
        createAlignAndSubstituteRule(modulesAndSubstitutions)

        def forceConfig = ''
        if (useForce) {
            forceConfig = """
                force 'test.nebula:a:$forcedVersion'
                force 'test.nebula:b:$forcedVersion'
                """.stripIndent()
        }

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:latest.release'
                implementation 'test.other:also-brings-a:latest.release'
                implementation 'test.other:brings-b:latest.release'
            }
            configurations.all {
                resolutionStrategy { $forceConfig }
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment && resultingVersion != 'FAILED') {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        coreAlignment | useForce | forcedVersion    | resultingVersion | description
        false         | false    | null             | '1.1.0'          | 'without a force'
        false         | true     | '1.0.3'          | '1.0.3'          | 'forced to static version'
        false         | true     | 'latest.release' | '1.1.0'          | 'forced to latest.release'

        true          | false    | null             | '1.1.0'          | 'without a force'
//         TODO: possibly use require-reject in lieu of resolutionStrategy.dependencySubstitution to fix this case
        true          | true     | '1.0.2'          | 'FAILED'         | 'forced to a static version'
        true          | true     | 'latest.release' | 'FAILED'         | 'forced to latest.release'
    }

    @Unroll
    def 'only brought in transitively: substitute with a range and align with a force | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.2').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.0.3').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        def substituteFromVersion = "[1.0.2,1.1.0]"
        def substituteToVersion = "1.0.1"
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())
        createAlignAndSubstituteRule(modulesAndSubstitutions)

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:latest.release'
                implementation 'test.other:also-brings-a:latest.release'
                implementation 'test.other:brings-b:latest.release'
            }
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:latest.release'
                    force 'test.nebula:b:latest.release'
                }
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.1"
        coreAlignment << [false, true]
    }

    @Issue("Based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/11")
    @Unroll
    def 'apply a static version via details.useVersion for 1 direct dep and align results | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:c:0.5.0')
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.5.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'a') {
                        details.useVersion '0.5.0'
                        details.because('$reason')
                    }
                }
            }
            """.stripIndent()

        when:

        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", resultingVersion)

        if (coreAlignment) {
            dependencyInsightContains(result.output, "test.nebula:a", '0.5.0')

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        } else {
            dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        }

        where:
        resultingVersion = "1.0.0"
        coreAlignment << [false, true]
    }

    @Issue("Based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/11")
    @Unroll
    def 'apply a static version via details.useVersion for each dependency and align results | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.2').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:1.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:1.0.3'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == 'test.nebula') {
                        details.useVersion '1.0.1'
                        details.because('$reason')
                    }
                }
            }
            """.stripIndent()

        when:

        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.1"
        coreAlignment << [false, true]
    }

    @Issue("Based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/11")
    @Unroll
    def 'apply a static version via details.useVersion for 1 direct dep and align results with conflict resolution involved | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:c:0.5.0')
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.2').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:1.0.0'
                implementation 'test.nebula:b:0.5.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'c') {
                        details.useVersion '0.5.0'
                        details.because('$reason')
                    }
                }
            }
            """.stripIndent()

        when:

        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:c", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.2"
        coreAlignment << [false, true]
    }

    @Issue("Based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/11")
    @Unroll
    def 'apply a static version via details.useVersion for 1 direct dep and align results without conflict resolution involved | core alignment #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:c:0.5.0')
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << """
            {
                "align": [
                    $alignRuleForTestNebula
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:1.0.0'
                implementation 'test.nebula:b:0.5.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'c') {
                        details.useVersion '0.5.0'
                        details.because('$reason')
                    }
                }
            }
            """.stripIndent()

        when:

        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            dependencyInsightContains(result.output, "test.nebula:c", '0.5.0')

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        } else {
            dependencyInsightContains(result.output, "test.nebula:c", resultingVersion)
        }

        where:
        resultingVersion = "1.0.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'statically defined dependency: sub & align all versions higher than x and align | core alignment #coreAlignment'() {
        given:
        // based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/50
        setupForGuiceAndLibraryDependency(definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment, false, 'com.google.inject'))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-assistedinject", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-grapher", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-multibindings", resultingVersion)
        dependencyInsightContains(result.output, "com.google.inject:guice", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-com.google.inject:$resultingVersion")
        }

        where:
        definedVersion = "4.2.0"
        resultingVersion = '4.1.0'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dynamically defined dependency: substituting all versions higher than x and aligning | core alignment #coreAlignment'() {
        given:
        // based on https://github.com/nebula-plugins/gradle-nebula-integration/issues/50
        // Also, substitutions apply on declared dependencies, not resolved ones
        setupForGuiceAndLibraryDependency(definedVersion)

        when:
        def result = runTasks(*tasks(coreAlignment, false, 'com.google.inject'))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-assistedinject", resultingVersionForDepsOtherThanCoreGuice)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-grapher", resultingVersionForDepsOtherThanCoreGuice)
        dependencyInsightContains(result.output, "com.google.inject.extensions:guice-multibindings", resultingVersionForDepsOtherThanCoreGuice)

        // since this test uses an external dependency that keeps incrementing, let's check that it just doesn't get the same result
        def content = "com.google.inject:guice:.*$resultingVersionForDepsOtherThanCoreGuice\n"
        assert result.output.findAll(content).size() == 0

        // just make sure there's a value here for dependencyInsight
        dependencyInsightContains(result.output, "com.google.inject:guice", '')

        if (coreAlignment) {
            def alignedPlatformPartialVersion = "4."
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-com.google.inject:$alignedPlatformPartialVersion")
        }

        where:
        definedVersion = "4.+"
        resultingVersionForDepsOtherThanCoreGuice = '4.1.0'
        coreAlignment << [false, true] // FIXME: should resolve differently
    }

    @Unroll
    def 'transitive dependencies are aligned | core alignment #coreAlignment'() {
        given:
        def substituteFromVersion = "[1.0.1,1.1.0)"
        def substituteToVersion = "1.0.0"
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:f:$substituteFromVersion".toString(), "test.nebula:f:$substituteToVersion".toString())
        createAlignAndSubstituteRule(modulesAndSubstitutions)

        buildFile << """
            dependencies {
                implementation 'test.nebula:f:1.0.3'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:f", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.0"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'alignment rule excludes are honored | core alignment #coreAlignment'() {
        given:
        def module = "[1.0.1,1.0.3)"
        def with = "1.0.3"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "test.nebula:b:$module",
                        "with" : "test.nebula:b:$with",
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
                        "excludes": ["(c|g)"],
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.0.1'
                implementation 'test.nebula:c:1.0.2'
                implementation 'test.nebula:g:1.0.1'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        def alignedResultingVersion = "1.0.3"
        dependencyInsightContains(result.output, "test.nebula:a", alignedResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", alignedResultingVersion)

        dependencyInsightContains(result.output, "test.nebula:c", '1.0.2')
        dependencyInsightContains(result.output, "test.nebula:g", '1.0.1')

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$alignedResultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'alignment rule includes are honored | core alignment #coreAlignment'() {
        given:
        def module = "[1.0.1,1.0.3)"
        def with = "1.0.3"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "test.nebula:b:$module",
                        "with" : "test.nebula:b:$with",
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
                        "includes": ["(a|b)"],
                        "excludes": [],
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.0.1'
                implementation 'test.nebula:c:1.0.2'
                implementation 'test.nebula:g:1.0.1'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        def alignedResultingVersion = "1.0.3"
        dependencyInsightContains(result.output, "test.nebula:a", alignedResultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", alignedResultingVersion)

        dependencyInsightContains(result.output, "test.nebula:c", '1.0.2')
        dependencyInsightContains(result.output, "test.nebula:g", '1.0.1')

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$alignedResultingVersion")
        }

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: sub & align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: sub & align from bom version to higher static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: sub & align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.+"
        resultingVersion = "1.0.2"  // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: sub & align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support disabled: not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.0"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.0"
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support enabled: sub & align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.1"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support enabled: sub & align from bom version to higher static version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support enabled: sub & align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.+"
        resultingVersion = "1.1.0"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support enabled: sub & align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'recs with core bom support enabled: do not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.0"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.0"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support disabled: sub & align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support disabled: sub & align from bom version to higher static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support disabled: sub & align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.+"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support disabled: sub & align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.2" // FIXME: should resolve differently
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support disabled: do not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.0"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.0"
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support enabled: sub & align from bom version to lower static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.1"
        coreBomSupport = true
        coreAlignment << [false, true]
    }


    @Unroll
    def 'enforced recs with core bom support enabled: sub & align from bom version to higher static version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support enabled: sub & align from bom version to higher minor-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.+"
        resultingVersion = "1.1.0"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support enabled: sub & align from bom version to higher patch-scoped dynamic version | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*(tasks(coreAlignment, coreBomSupport)))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.2"
        substituteToVersion = "1.0.3"
        resultingVersion = "1.0.3"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'enforced recs with core bom support enabled: do not substitute when resulting version is not in substitute-away-from range | core alignment #coreAlignment'() {
        given:
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, substituteToVersion, usingEnforcedPlatform)

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        bomVersion = "1.0.0"
        substituteToVersion = "1.0.1"
        resultingVersion = "1.0.0"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: direct static dependency: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        createMultipleSubstitutionRules()

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:1.0.1'
                implementation 'test.nebula:b:1.0.3'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.3 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.2"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: only brought in transitively: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:brings-a:1.0.0').addDependency('test.nebula:a:1.0.1').build())
                .addModule(new ModuleBuilder('test.other:also-brings-a:1.0.0').addDependency('test.nebula:a:1.0.3').build())
                .addModule(new ModuleBuilder('test.other:brings-b:1.0.0').addDependency('test.nebula:b:1.0.3').build())
                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        createMultipleSubstitutionRules()

        buildFile << """
            dependencies {
                implementation 'test.other:brings-a:latest.release'
                implementation 'test.other:brings-b:latest.release'
            }
            """.stripIndent()

        when:
        def result = runTasks(*tasks(coreAlignment))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.3 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.2"
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: recs with core bom support disabled: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        def bomVersion = "1.0.1"
        setupForBomAndAlignmentAndSubstitution(bomVersion, "")

        rulesJsonFile.delete()
        rulesJsonFile.createNewFile()
        createMultipleSubstitutionRules()

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("Recommending version 1.0.1 for dependency test.nebula:a via conflict resolution recommendation")
            assert result.output.contains("Recommending version 1.0.1 for dependency test.nebula:b via conflict resolution recommendation")

            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.1 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.1"
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: recs with core bom support enabled: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        def bomVersion = "1.0.1"
        setupForBomAndAlignmentAndSubstitution(bomVersion, "")

        rulesJsonFile.delete()
        rulesJsonFile.createNewFile()
        createMultipleSubstitutionRules()

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.1 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.2"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: enforced recs with core bom support disabled: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        def bomVersion = "1.0.1"
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, "", usingEnforcedPlatform)

        rulesJsonFile.delete()
        rulesJsonFile.createNewFile()
        createMultipleSubstitutionRules()

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.1 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.1"
        coreBomSupport = false
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple substitutions applied: enforced recs with core bom support enabled: honor multiple substitutions | core alignment: #coreAlignment'() {
        given:
        def bomVersion = "1.0.1"
        def usingEnforcedPlatform = true
        setupForBomAndAlignmentAndSubstitution(bomVersion, "", usingEnforcedPlatform)

        rulesJsonFile.delete()
        rulesJsonFile.createNewFile()
        createMultipleSubstitutionRules()

        when:
        def result = runTasks(*tasks(coreAlignment, coreBomSupport))

        then:
        writeOutputToProjectDir(result.output)
        dependencyInsightContains(result.output, "test.nebula:a", resultingVersion)
        dependencyInsightContains(result.output, "test.nebula:b", resultingVersion)

        if (coreAlignment) {
            assert result.output.contains("substituted test.nebula:a:1.0.1 with test.nebula:a:1.0.2")
            assert result.output.contains("substituted test.nebula:b:1.0.1 with test.nebula:b:1.0.2")

            assert result.output.contains("belongs to platform aligned-platform:rules-0-for-test.nebula-or-test.nebula.ext:$resultingVersion")
        }

        where:
        resultingVersion = "1.0.2"
        coreBomSupport = true
        coreAlignment << [false, true]
    }

    private File createMultipleSubstitutionRules() {
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module" : "test.nebula:a:1.0.1",
                        "with" : "test.nebula:a:1.0.2",
                        "reason" : "1.0.1 is too small",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module" : "test.nebula:b:1.0.1",
                        "with" : "test.nebula:b:1.0.2",
                        "reason" : "1.0.1 is too small",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module" : "test.nebula:a:1.1.0",
                        "with" : "test.nebula:a:1.0.3",
                        "reason" : "1.1.0 is too large",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module" : "test.nebula:b:1.1.0",
                        "with" : "test.nebula:b:1.0.3",
                        "reason" : "1.1.0 is too large",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module" : "test.nebula:a:1.0.3",
                        "with" : "test.nebula:a:1.0.2",
                        "reason" : "1.0.3 is also too large",
                        "author" : "Test user <test@example.com>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module" : "test.nebula:b:1.0.3",
                        "with" : "test.nebula:b:1.0.2",
                        "reason" : "1.0.3 is also too large",
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

    private def setupForSimplestSubstitutionAndAlignmentCases(String substituteFromVersion, String substituteToVersion, List<String> definedVersions) {
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())
        assert definedVersions.size() == 2

        createAlignAndSubstituteRule(modulesAndSubstitutions)

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:${definedVersions[0]}'
                implementation 'test.nebula:b:${definedVersions[1]}'
            }
            """.stripIndent()
    }

    private def setupForSubstitutionAndAlignmentCasesWithMissingVersions(String substituteFromVersion, String substituteToVersion, List<String> definedVersions) {
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:c:$substituteFromVersion".toString(), "test.nebula:c:$substituteToVersion".toString())
        assert definedVersions.size() == 3

        createAlignAndSubstituteRule(modulesAndSubstitutions)

        buildFile << """
            dependencies {
                implementation 'test.nebula:a:${definedVersions[0]}'
                implementation 'test.nebula:b:${definedVersions[1]}'
                implementation 'test.nebula:c:${definedVersions[2]}'
            }
            """.stripIndent()
    }

    private def setupForBomAndAlignmentAndSubstitution(String bomVersion, String substituteToVersion, boolean usingEnforcedPlatform = false) {
        def substituteFromVersion = "1.0.2"
        Map<String, String> modulesAndSubstitutions = new HashMap<>()
        modulesAndSubstitutions.put("test.nebula:a:$substituteFromVersion".toString(), "test.nebula:a:$substituteToVersion".toString())
        modulesAndSubstitutions.put("test.nebula:b:$substituteFromVersion".toString(), "test.nebula:b:$substituteToVersion".toString())

        createAlignAndSubstituteRule(modulesAndSubstitutions)

        def bomRepo = createBom(["test.nebula:a:$bomVersion", "test.nebula:b:$bomVersion"])

        buildFile.text = ""
        buildFile << """
            buildscript {
                repositories { jcenter() }
            }
            """.stripIndent()
        buildFile << baseBuildGradleFile("'nebula.dependency-recommender' version '9.0.1\'")


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
                implementation 'test.nebula:a'
                implementation 'test.nebula:b'
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
                    },
                    {
                        "module": "com.google.inject.extensions:guice-assistedinject:[4.2.0,)",
                        "with": "com.google.inject.extensions:guice-assistedinject:4.1.0",
                        "reason": "$reason",
                        "author": "Test user <test@example.com>",
                        "date": "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module": "com.google.inject.extensions:guice-grapher:[4.2.0,)",
                        "with": "com.google.inject.extensions:guice-grapher:4.1.0",
                        "reason": "$reason",
                        "author": "Test user <test@example.com>",
                        "date": "2015-10-07T20:21:20.368Z"
                    },
                    {
                        "module": "com.google.inject.extensions:guice-multibindings:[4.2.0,)",
                        "with": "com.google.inject.extensions:guice-multibindings:4.1.0",
                        "reason": "$reason",
                        "author": "Test user <test@example.com>",
                        "date": "2015-10-07T20:21:20.368Z"
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
    implementation "com.google.inject:guice:$definedVersion"
    implementation "test.nebula:a:1.0"
}
"""

        MavenRepo repo = new MavenRepo()
        repo.root = new File(projectDir, 'repo')
        Pom pom = new Pom('test.nebula', 'a', '1.0', ArtifactType.POM)
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

    private def createAlignAndSubstituteRule(Map<String, String> modulesAndSubstitutions) {
        rulesJsonFile << """
            {
                "substitute": [
            """.stripIndent()

        List<String> substitutions = new ArrayList<>()
        modulesAndSubstitutions.each { module, with ->
            substitutions.add("""
                        {
                            "module" : "$module",
                            "with" : "$with",
                            "reason" : "$reason",
                            "author" : "Test user <test@example.com>",
                            "date" : "2015-10-07T20:21:20.368Z"
                        }""".stripIndent())
        }
        rulesJsonFile << substitutions.join(',')

        rulesJsonFile << """
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

    private void writeOutputToProjectDir(String output) {
        def file = new File(projectDir, "result.txt")
        file.createNewFile()
        file << output
    }

}