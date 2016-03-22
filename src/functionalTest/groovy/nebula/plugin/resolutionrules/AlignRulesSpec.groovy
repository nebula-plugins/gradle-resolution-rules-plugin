/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class AlignRulesSpec extends IntegrationSpec {
    def rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'aligntest'
        '''.stripIndent()
    }

    def 'can align direct dependencies if necessary'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    def 'can align dynamic dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
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
                compile 'test.nebula:a:1.+'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.nebula:a:1.+ -> 1.0.1\n'
    }

    def 'can align dynamic range dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.0.1')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
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
                compile 'test.nebula:a:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.nebula:a:[1.0.0, 2.0.0) -> 1.0.1\n'
    }

    def 'can align transitive dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.other:c:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '\\--- test.other:c:1.0.0\n'
        result.standardOutput.contains '     \\--- test.nebula:b:1.1.0\n'
    }

    def 'can align deeper transitive dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:b:1.1.0').build())
                .addModule(new ModuleBuilder('test.other:d:1.0.0').addDependency('test.other:c:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.other:d:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '\\--- test.other:d:1.0.0\n'
        result.standardOutput.contains '     \\--- test.other:c:1.0.0\n'
        result.standardOutput.contains '          \\--- test.nebula:b:1.1.0\n'
    }

    def 'can align some dependencies in a group'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:0.42.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:0.42.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.nebula:c:0.42.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "group": "test.nebula",
                        "includes": [ "a", "b" ],
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
                compile 'test.nebula:c:0.42.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 1.1.0'
        result.standardOutput.contains '+--- test.nebula:b:1.1.0'
        result.standardOutput.contains '\\--- test.nebula:c:0.42.0'
    }

    def 'skip aligning some dependencies in a group'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:0.42.0')
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:0.42.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.nebula:c:0.42.0')
                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "group": "test.nebula",
                        "excludes": [ "a" ],
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
                compile 'test.nebula:c:0.42.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '+--- test.nebula:b:1.1.0\n'
        result.standardOutput.contains '\\--- test.nebula:c:0.42.0 -> 1.1.0'
    }
}
