package nebula.plugin.resolutionrules


import spock.lang.Unroll

class AlignAndMigrateViaReplacementSpec extends AbstractAlignAndMigrateSpec {
    @Unroll
    def 'align and migrate via replacement | core alignment: #coreAlignment'() {
        given:
        createAlignAndReplaceRules(['other:e': 'test.nebula:c'])

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def results = runTasks(*tasks)
        then:
        results.output.contains("test.nebula:a:1.0.0 -> $alignedVersion")
        results.output.contains("test.nebula:b:$alignedVersion")

        if(coreAlignment) {
            results.output.contains("other:e:4.0.0 -> test.nebula:c:1.0.1") // not aligned :/
            results.output.contains("belongs to platform aligned-platform")
        } else {
            results.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")
            results.output.contains("by rules aligning group")
        }

        when:
        def dependenciesTasks = ['dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def resultsForDependencies = runTasks(*dependenciesTasks)

        then:
        if(coreAlignment) {
            resultsForDependencies.output.contains("other:e:4.0.0 -> test.nebula:c:1.0.1") // not aligned :/
        } else {
            resultsForDependencies.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")
        }


        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'align and migrate via replacement with brought in dependency as direct as well | core alignment: #coreAlignment'() {
        given:
        createAlignAndReplaceRules(['other:e': 'test.nebula:c'])
        buildFile << """
        dependencies {
            implementation 'test.nebula:c:1.0.1'
        }
        """

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def results = runTasks(*tasks)
        then:
        results.output.contains("test.nebula:a:1.0.0 -> $alignedVersion")
        results.output.contains("test.nebula:b:$alignedVersion")
        results.output.contains("other:e:4.0.0 -> test.nebula:c:$alignedVersion")

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