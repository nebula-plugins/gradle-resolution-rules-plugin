package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class EnhancedGraphOrderingSpec extends AbstractAlignRulesSpec {

    @Unroll
    def 'alignment and constraints preserve versions with #sortOrder artifact ordering'() {
        given:
        // A -> B -> C -> D, with a shortcut A -> D. B and D share a virtual platform.
        // The root constraint upgrades C without making it a direct dependency.
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.other:a:1.0.0')
                        .addDependency('test.nebula:b:1.0.0')
                        .addDependency('test.nebula:d:1.1.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('test.other:c:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.1.0').addDependency('test.other:c:1.0.0').build())
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:d:1.0.0').build())
                .addModule(new ModuleBuilder('test.other:c:2.0.0').addDependency('test.nebula:d:1.0.0').build())
                .addModule('test.nebula:d:1.0.0')
                .addModule('test.nebula:d:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''
            {
                "align": [
                    {
                        "group": "test.nebula",
                        "reason": "Align B and D",
                        "author": "Nebula maintainers",
                        "date": "2026-10-23"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """
            repositories {
                maven { url = '${mavenrepo.toURI()}' }
            }
            dependencies {
                implementation 'test.other:a:1.0.0'
                constraints {
                    implementation 'test.other:c:2.0.0'
                }
            }
            configurations.named('compileClasspath') {
                resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.${sortOrder})
            }
            configurations.named('runtimeClasspath') {
                resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.${sortOrder})
            }
            tasks.register('printArtifactOrder') {
                def compileFiles = configurations.compileClasspath.incoming.artifacts.artifactFiles
                def runtimeFiles = configurations.runtimeClasspath.incoming.artifacts.artifactFiles
                inputs.files(compileFiles, runtimeFiles)
                doLast {
                    println 'COMPILE_ARTIFACT_ORDER=' + compileFiles.files.collect { it.name }.join(',')
                    println 'RUNTIME_ARTIFACT_ORDER=' + runtimeFiles.files.collect { it.name }.join(',')
                }
            }
        """.stripIndent()

        when:
        settingsFile.text = settingsFile.text.replace('enableFeaturePreview("ENHANCED_GRAPH_ORDERING")', '')
        def legacyResult = runTasks('printArtifactOrder', '--configuration-cache')
        def legacyCompileOrder = artifactOrder(legacyResult.output, 'COMPILE_ARTIFACT_ORDER=')
        def legacyRuntimeOrder = artifactOrder(legacyResult.output, 'RUNTIME_ARTIFACT_ORDER=')
        settingsFile << '\nenableFeaturePreview("ENHANCED_GRAPH_ORDERING")\n'
        def result = runTasks('printArtifactOrder', '--configuration-cache')
        def cachedResult = runTasks('printArtifactOrder', '--configuration-cache')

        then:
        legacyResult.task(':printArtifactOrder').outcome == TaskOutcome.SUCCESS
        legacyCompileOrder.toSet() == expectedArtifacts.toSet()
        legacyRuntimeOrder.toSet() == expectedArtifacts.toSet()
        result.task(':printArtifactOrder').outcome == TaskOutcome.SUCCESS
        cachedResult.task(':printArtifactOrder').outcome == TaskOutcome.SUCCESS
        result.output.contains("COMPILE_ARTIFACT_ORDER=${expectedArtifacts.join(',')}")
        result.output.contains("RUNTIME_ARTIFACT_ORDER=${expectedArtifacts.join(',')}")
        cachedResult.output.contains("COMPILE_ARTIFACT_ORDER=${expectedArtifacts.join(',')}")
        cachedResult.output.contains("RUNTIME_ARTIFACT_ORDER=${expectedArtifacts.join(',')}")
        cachedResult.output.contains('Reusing configuration cache.')

        and:
        println "${sortOrder}: legacy compile=${legacyCompileOrder}; enhanced compile=${expectedArtifacts}"
        println "${sortOrder}: legacy runtime=${legacyRuntimeOrder}; enhanced runtime=${expectedArtifacts}"

        where:
        sortOrder          | expectedArtifacts
        'DEFAULT'          | ['a-1.0.0.jar', 'b-1.1.0.jar', 'd-1.1.0.jar', 'c-2.0.0.jar']
        'CONSUMER_FIRST'   | ['a-1.0.0.jar', 'b-1.1.0.jar', 'c-2.0.0.jar', 'd-1.1.0.jar']
        'DEPENDENCY_FIRST' | ['d-1.1.0.jar', 'c-2.0.0.jar', 'b-1.1.0.jar', 'a-1.0.0.jar']
    }
    private static List<String> artifactOrder(String output, String prefix) {
        def line = output.readLines().find { it.startsWith(prefix) }
        assert line != null
        return line.substring(prefix.length()).tokenize(',')
    }
}
