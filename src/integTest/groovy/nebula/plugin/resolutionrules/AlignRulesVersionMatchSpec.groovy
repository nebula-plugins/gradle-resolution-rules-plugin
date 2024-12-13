package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.junit.Ignore

@Ignore("we do not currently use VersionMatchers")
class AlignRulesVersionMatchSpec extends AbstractAlignRulesSpec {
    def 'match excluding differences in version results in no alignment'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "match": "EXCLUDE_SUFFIXES",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:b:1.0.0-1'
            }
        """.stripIndent()

        when:
        def output = runTasks('dependencies', '--configuration', 'compileClasspath').output

        then:
        output.contains '\\--- test.nebula:b:1.0.0-1\n'
        output.contains '\\--- test.nebula:a:1.0.0\n'
    }

    def 'match regex version alignment'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0-1').addDependency('test.nebula:a:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "match": "^(\\\\d+\\\\.)?(\\\\d+\\\\.)?(\\\\*|\\\\d+)",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:b:1.0.0-1'
            }
        """.stripIndent()

        when:
        def output = runTasks('dependencies', '--configuration', 'compileClasspath').output

        then:
        output.contains '\\--- test.nebula:b:1.0.0-1\n'
        output.contains '\\--- test.nebula:a:1.0.0\n'
    }
}
