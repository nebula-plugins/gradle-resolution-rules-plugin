package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

 /*
    Used to verify behavior when a dependency brings in its replacement as its only direct dependency
    Example:
    foo:bar:1.2.3 is the latest release of foo:bar that brings in its new coordinates at
    better-foo:better-bar:2.0.0
    We want to make sure that alignment still takes place for all dependencies in better-foo:better-bar
  */
class AbstractAlignAndMigrateSpec extends AbstractAlignRulesSpec {
    String alignedVersion = '1.0.3'
    File mavenrepo

    def setup() {
        createTestDependencies()
        buildFile << """
        repositories {
            maven { url '${projectDir.toPath().relativize(mavenrepo.toPath()).toFile()}' }
        }
        dependencies {
            implementation 'test.nebula:a:1.0.0'
            implementation 'test.nebula:b:1.0.3'
            implementation 'other:e:4.0.0'
        }
        """.stripIndent()

        debug = true
    }

    private void createTestDependencies() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .addModule('test.nebula:a:1.0.2')
                .addModule('test.nebula:a:1.0.3')

                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.0.1')
                .addModule('test.nebula:b:1.0.2')
                .addModule('test.nebula:b:1.0.3')

                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.0.1')
                .addModule('test.nebula:c:1.0.2')
                .addModule('test.nebula:c:1.0.3')

                .addModule(new ModuleBuilder('other:e:4.0.0').addDependency('test.nebula:c:1.0.1').build()) // this is the most interesting dependency under test

                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()
    }

    Collection<String> dependencyInsightTasks(boolean coreAlignment) {
        return ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
    }
}