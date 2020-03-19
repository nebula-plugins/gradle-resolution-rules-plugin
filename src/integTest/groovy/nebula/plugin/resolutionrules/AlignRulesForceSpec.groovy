package nebula.plugin.resolutionrules

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class AlignRulesForceSpec extends AbstractAlignRulesSpec {
    def setup() {
        keepFiles = true
        debug = true
    }

    @Unroll
    def 'alignment uses #name forced version - core alignment #coreAlignment'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .addModule('test.nebula.other:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
                implementation 'test.nebula.other:a:1.0.0'
            }
            $force
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def result = runTasks(*tasks)

        def debugResult
        if (!coreAlignment) {
            debugResult = runTasks(*tasks, '--debug')
        }

        then:
        if (!coreAlignment) {
            def supersedingForcesInformation = "Found force(s) [test.nebula:a:0.15.0] that supersede resolution rule"
            assert debugResult.output.contains(supersedingForcesInformation)
            assert debugResult.output.contains("reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=$moduleName-0-for-test.nebula). Will use 0.15.0")
        }
        result.output.contains '+--- test.nebula:a:1.0.0 -> 0.15.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0 -> 0.15.0\n'
        result.output.contains '+--- test.nebula:c:0.15.0\n'
        result.output.contains '--- test.nebula.other:a:1.0.0\n'

        where:
        coreAlignment | name            | force
        false         | "all"           | "configurations.all { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
        false         | "configuration" | "configurations.compileClasspath { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
        false         | "dependency"    | "dependencies { implementation ('test.nebula:a:0.15.0') { force = true } }"

        true          | "all"           | "configurations.all { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
        true          | "configuration" | "configurations.compileClasspath { resolutionStrategy { force 'test.nebula:a:0.15.0' } }"
        true          | "dependency"    | "dependencies { implementation ('test.nebula:a:0.15.0') { force = true } }"
    }

    @Unroll
    def 'when multiple forces are present then #outcome'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:2.0.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:2.0.0'
                force 'test.nebula:b:1.0.0'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        if (coreAlignment) {
            assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:2.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:1.0.0')
        } else {
            assert result.output.contains('+--- test.nebula:a:2.0.0 -> 0.15.0\n')
            assert result.output.contains('+--- test.nebula:b:2.0.0 -> 0.15.0\n')
            assert result.output.contains('\\--- test.nebula:c:1.0.0 -> 0.15.0\n')
        }

        where:
        coreAlignment | outcome
        false         | 'Nebula alignment uses lowest forced version'
        true          | 'Core alignment fails due to multiple forces'
    }

    @Unroll
    def 'when dynamic forces are present then #outcome'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.00.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:2.0.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def result = runTasks(*tasks)
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def debugResult
        if (!coreAlignment) {
            debugResult = runTasks(*tasks, '--debug')
        }

        then:
        if (coreAlignment) {
            assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:2.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:1.0.0')
        } else {
            def supersedingForcesInformation = "Found force(s) [test.nebula:a:latest.release, test.nebula:b:1.+, test.nebula:c:0.15.0] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=$moduleName, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=$moduleName-0-for-test.nebula). Will use 0.15.0"
            assert debugResult.output.contains(supersedingForcesInformation)
            assert result.output.contains('+--- test.nebula:a:2.0.0 -> 0.15.0\n')
            assert result.output.contains('+--- test.nebula:b:2.0.0 -> 0.15.0\n')
            assert result.output.contains('\\--- test.nebula:c:1.0.0 -> 0.15.0\n')
        }

        where:
        coreAlignment | outcome
        false         | 'Nebula alignment outputs warnings and honors static force'
        true          | 'Core alignment fails due to multiple forces'
    }

    @Unroll
    def 'alignment with latest.release force'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def result = runTasks(*tasks)

        def debugResult
        if (!coreAlignment) {
            debugResult = runTasks(*tasks, '--debug')
        }

        then:
        if (!coreAlignment) {
            def supersedingForcesInformation = "Found force(s) [test.nebula:a:latest.release] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=$moduleName, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=$moduleName-0-for-test.nebula). Will use highest dynamic version 2.0.0 that matches most specific selector latest.release"
            assert debugResult.output.contains(supersedingForcesInformation)
        }
        result.output.contains '+--- test.nebula:a:2.0.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0 -> 2.0.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0 -> 2.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'alignment with sub-version force'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:1.+'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def result = runTasks(*tasks)

        def debugResult
        if (!coreAlignment) {
            debugResult = runTasks(*tasks, '--debug')
        }

        then:
        if (!coreAlignment) {
            def supersedingForcesInformation = "Found force(s) [test.nebula:a:1.+] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=$moduleName, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=$moduleName-0-for-test.nebula). Will use highest dynamic version 1.0.0 that matches most specific selector 1.+"
            assert debugResult.output.contains(supersedingForcesInformation)
        }
        result.output.contains '+--- test.nebula:a:2.0.0 -> 1.0.0\n'
        result.output.contains '+--- test.nebula:b:1.0.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0 -> 1.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'with multiple specific dynamic versions then #outcome'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:3.0.0')
                .addModule('test.nebula:a:2.0.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:2.0.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:2.0.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                implementation 'test.nebula:a:2.0.0'
                implementation 'test.nebula:b:1.0.0'
                implementation 'test.nebula:c:0.15.0'
            }
            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:[1.0, 2.0)'
            }
        """.stripIndent()

        when:
        def tasks = ['dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        def result = runTasks(*tasks)
        def dependencyInsightResult = runTasks('dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def debugResult
        if (!coreAlignment) {
            debugResult = runTasks(*tasks, '--debug')
        }

        then:
        if (coreAlignment) {
            assert dependencyInsightResult.output.contains('Multiple forces on different versions for virtual platform ')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:a:2.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:b:1.0.0')
            assert dependencyInsightResult.output.contains('Could not resolve test.nebula:c:0.15.0')
        } else {
            def supersedingForcesInformation = "Found force(s) [test.nebula:a:latest.release, test.nebula:b:1.+, test.nebula:c:[1.0, 2.0)] that supersede resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=$moduleName, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=$moduleName-0-for-test.nebula). Will use highest dynamic version 1.0.0 that matches most specific selector [1.0, 2.0)"
            assert debugResult.output.contains(supersedingForcesInformation)
            assert result.output.contains('+--- test.nebula:a:2.0.0 -> 1.0.0\n')
            assert result.output.contains('+--- test.nebula:b:1.0.0\n')
            assert result.output.contains('\\--- test.nebula:c:0.15.0 -> 1.0.0\n')
        }

        where:
        coreAlignment | outcome
        false         | 'Nebula alignment uses the most specific dynamic version'
        true          | 'Core alignment fails due to multiple forces'
    }
}
