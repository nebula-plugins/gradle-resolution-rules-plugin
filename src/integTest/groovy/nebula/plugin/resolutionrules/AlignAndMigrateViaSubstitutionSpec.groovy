package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndMigrateViaSubstitutionSpec extends AbstractAlignAndMigrateSpec {
    @Unroll
    def 'substitution and alignment'() {
        given:
        createAlignAndSubstituteRules(['other:e:4.0.0': 'test.nebula:c:1.0.1'])

        when:
        def results = runTasks(*dependencyInsightTasks())

        then:
        results.output.contains("test.nebula:a:1.0.0 -> $alignedVersion")
        results.output.contains("test.nebula:b:$alignedVersion")
        results.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")
        results.output.contains("substituted other:e:4.0.0 with test.nebula:c:1.0.1 because '★ custom substitution reason'")
        results.output.contains("belongs to platform aligned-platform")


        when:
        def dependenciesTasks = ['dependencies', '--configuration', 'compileClasspath']
        def resultsForDependencies = runTasks(*dependenciesTasks)

        then:
        resultsForDependencies.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")
    }
}
