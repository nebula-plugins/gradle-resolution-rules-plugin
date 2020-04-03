package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndMigrateViaSubstitutionSpec extends AbstractAlignAndMigrateSpec {
    @Unroll
    def 'substitution and alignment | core alignment: #coreAlignment'() {
        given:
        createAlignAndSubstituteRules(['other:e:4.0.0': 'test.nebula:c:1.0.1'])

        when:
        def results = runTasks(*dependencyInsightTasks(coreAlignment))

        then:
        results.output.contains("test.nebula:a:1.0.0 -> $alignedVersion")
        results.output.contains("test.nebula:b:$alignedVersion")
        results.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")
        results.output.contains("substitution from 'other:e:4.0.0' to 'test.nebula:c:1.0.1' because ★ custom substitution reason")

        if(coreAlignment) {
            results.output.contains("belongs to platform aligned-platform")
        } else {
            results.output.contains("by rules aligning group")
        }

        when:
        def dependenciesTasks = ['dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def resultsForDependencies = runTasks(*dependenciesTasks)

        then:
        resultsForDependencies.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")

        where:
        coreAlignment << [false, true]
    }
}