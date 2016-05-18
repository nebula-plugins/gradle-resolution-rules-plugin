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

    def 'can align direct dependencies from ivy repositories'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .build()
        GradleDependencyGenerator ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        ivyrepo.generateTestIvyRepo()

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
                ${ivyrepo.ivyRepositoryBlock}
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

    def 'align rules do not replace changes made by other rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:2.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula.ext:b:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula.ext:b:2.0.0').addDependency('test.nebula:a:2.0.0').build())
                .addModule(new ModuleBuilder('test.other:c:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "substitute": [
                    {
                        "module": "test.nebula:b",
                        "with": "test.nebula.ext:b:1.0.0",
                        "reason": "Library was published with incorrect coordinates",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "align": [
                    {
                        "group": "(test.nebula|test.nebula.ext)",
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
                compile 'test.other:c:1.0.0'
                compile 'test.nebula:a:2.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.nebula:b:1.0.0 -> test.nebula.ext:b:2.0.0\n'
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
                        "name": "testNebula",
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
                        "name": "testNebula",
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

    def 'dependencies with cycles do not lead to infinite loops'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.other:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.other:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.1.0').addDependency('test.other:b:1.0.0').build())
                .addModule('test.nebula:b:1.0.0')
                .addModule(new ModuleBuilder('test.nebula:b:1.1.0').addDependency('test.other:b:1.0.0').build())
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
                compile 'test.nebula:a:1.1.0'
                compile 'test.nebula:b:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.1.0\n'
        result.standardOutput.contains '|    \\--- test.other:b:1.0.0\n'
        result.standardOutput.contains '|         \\--- test.nebula:b:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains '|              \\--- test.other:b:1.0.0 (*)\n'
        result.standardOutput.contains '\\--- test.nebula:b:1.0.0 -> 1.1.0 (*)\n'
    }

    def 'able to omit dependency versions to take what is given transitively'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule('test.nebula:b:1.0.0')
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '|    \\--- test.nebula:b:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:b: -> 1.0.0\n'
    }

    def 'a project can build in presence of align rules for jars it produces'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula",
                        "includes": ["aligntest", "b"],
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << '''\
            group = 'test.nebula'
            version = '0.1.0'

            apply plugin: 'maven-publish'

            publishing {
                publications {
                    test(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven {
                        name 'repo'
                        url 'build/repo'
                    }
                }
            }
        '''.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        noExceptionThrown()
    }

    def 'multiple align rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.other:c:0.12.2')
                .addModule('test.other:c:1.0.0')
                .addModule('test.other:d:0.12.2')
                .addModule('test.other:d:1.0.0')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

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
                    },
                    {
                        "name": "testOther",
                        "group": "test.other",
                        "reason": "Aligning test",
                        "author": "Example Tester <test@example.org>",
                        "date": "2016-04-05T19:19:49.495Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.1.0'
                compile 'test.other:c:1.0.0'
                compile 'test.other:d:0.12.+'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains 'test.nebula:a:1.0.0 -> 1.1.0\n'
        result.standardOutput.contains 'test.nebula:b:1.1.0\n'
        result.standardOutput.contains 'test.other:c:1.0.0\n'
        result.standardOutput.contains 'test.other:d:0.12.+ -> 1.0.0\n'
    }

    def 'substitute and align work together'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('old.org:sub:0.1.0')
                .addModule('new.org:sub:0.2.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [],
                "substitute": [
                    {
                        "module" : "old.org:sub",
                        "with" : "new.org:sub:latest.release",
                        "reason" : "swap old.org to new.org",
                        "author" : "Example Person <person@example.org>",
                        "date" : "2016-03-18T20:21:20.368Z"
                    }
                ],
                "replace": [],
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:0.15.0'
                compile 'old.org:sub:latest.release'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.standardOutput.contains '\\--- old.org:sub:latest.release -> new.org:sub:0.2.0\n'
    }

    def 'jcenter align'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "com.google.guava",
                        "reason": "Align guava",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories { jcenter() }
            dependencies {
                compile 'com.google.guava:guava:12.0'
            }
        """

        when:
        runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        noExceptionThrown()
    }

    def 'unresolvable dependencies cause warnings to be output'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "com.google.guava",
                        "reason": "Align guava",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            repositories { jcenter() }
            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                compile 'com.google.guava:guava:oops'
            }
        """

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains("Resolution rules could not resolve all dependencies to align in configuration 'compile' should also fail to resolve")
    }

    def 'can add additional resolution rules outside of plugin'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.example:c:0.1.0')
                .addModule('test.example:c:0.2.0')
                .addModule('test:x:1.0.0')
                .addModule('test:x:1.0.1')
                .addModule('test:y:1.0.0')
                .addModule('test:y:1.0.1')
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
            configurations.all {
                resolutionStrategy {
                    force 'test.example:c:0.1.0'
                    eachDependency { details ->
                        if (details.requested.group == 'test') {
                            details.useTarget group: details.requested.group, name: details.requested.name, version: '1.0.0'
                        }
                    }
                }
            }
            dependencies {
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:0.15.0'
                compile 'test.example:c:latest.release'
                compile 'test:x:1.+'
                compile 'test:y:1.+'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.standardOutput.contains '+--- test.example:c:latest.release -> 0.1.0\n'
        result.standardOutput.contains '+--- test:x:1.+ -> 1.0.0\n'
        result.standardOutput.contains '\\--- test:y:1.+ -> 1.0.0\n'
    }

    def 'regular expressions supported in groups'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula.one:a:1.0.0')
                .addModule('test.nebula.one:a:0.15.0')
                .addModule('test.nebula.two:b:1.0.0')
                .addModule('test.nebula.two:b:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula.*",
                        "reason": "Align test.nebula.* dependencies",
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
                compile 'test.nebula.one:a:1.0.0'
                compile 'test.nebula.two:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula.one:a:1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula.two:b:0.15.0 -> 1.0.0\n'
    }

    def 'regular expressions supported in includes'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
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
                        "includes": ["(a|b)"],
                        "reason": "Align test.nebula a and b dependencies",
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
                compile 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.standardOutput.contains '\\--- test.nebula:c:0.15.0\n'
    }

    def 'regular expressions supported in excludes'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
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
                        "excludes": ["(b|c)"],
                        "reason": "Align test.nebula a and b dependencies",
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
                compile 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:1.0.0\n'
        result.standardOutput.contains '+--- test.nebula:b:0.15.0\n'
        result.standardOutput.contains '\\--- test.nebula:c:0.15.0\n'
    }

    def 'alignment does not apply to dependencies that already have the expected version'() {
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencyInsight', '--configuration', 'compile', '--dependency', 'test.nebula')

        then:
        result.standardOutput.contains 'Resolution rules ruleset alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version rule [group: test.nebula] aligning test.nebula:b to 1.0.0'
        result.standardOutput.contains 'test.nebula:a:1.0.0\n'
        result.standardOutput.contains 'test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    def 'alignment uses forced version, rather than highest version, when a force is present'() {
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
                compile 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.0.0'
                compile 'test.nebula:c:0.15.0'
                compile 'test.nebula.other:a:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains 'Found force(s) [test.nebula:a:0.15.0] that supersede resolution ruleset alignment-uses-forced-version-rather-than-highest-version-when-a-force-is-present align rule [group: test.nebula]. Will use 0.15.0 instead of 1.0.0'
        result.standardOutput.contains '+--- test.nebula:a:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:b:1.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:c:0.15.0\n'
        result.standardOutput.contains '\\--- test.nebula.other:a:1.0.0\n'
    }

    def 'alignment uses lowest forced version, when multiple forces are present'() {
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
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:2.0.0'
                compile 'test.nebula:c:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:2.0.0'
                force 'test.nebula:b:1.0.0'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.nebula:a:2.0.0 -> 0.15.0\n'
        result.standardOutput.contains '+--- test.nebula:b:2.0.0 -> 0.15.0\n'
        result.standardOutput.contains '\\--- test.nebula:c:1.0.0 -> 0.15.0\n'
    }

    def 'alignment outputs warnings and honors static force when dynamic forces are present'() {
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
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:2.0.0'
                compile 'test.nebula:c:1.0.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains('Resolution rules ruleset alignment-outputs-warnings-and-honors-static-force-when-dynamic-forces-are-present align rule [group: test.nebula] is unable to honor forced versions [latest.release, 1.+]. For a force to take precedence on an align rule, it must use a static version')
        standardOutput.contains '+--- test.nebula:a:2.0.0 -> 0.15.0\n'
        standardOutput.contains '+--- test.nebula:b:2.0.0 -> 0.15.0\n'
        standardOutput.contains '\\--- test.nebula:c:1.0.0 -> 0.15.0\n'
    }

    def 'alignment outputs warnings and falls back to default logic, when only dynamic forces are present'() {
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
                compile 'test.nebula:a:2.0.0'
                compile 'test.nebula:b:1.0.0'
                compile 'test.nebula:c:0.15.0'
            }
            configurations.compile.resolutionStrategy {
                force 'test.nebula:a:latest.release'
                force 'test.nebula:b:1.+'
                force 'test.nebula:c:2.+'
            }
        """.stripIndent()

        when:
        def standardOutput = runTasksSuccessfully('dependencies', '--configuration', 'compile').standardOutput

        then:
        standardOutput.contains('Resolution rules ruleset alignment-outputs-warnings-and-falls-back-to-default-logic-when-only-dynamic-forces-are-present align rule [group: test.nebula] is unable to honor forced versions [latest.release, 1.+, 2.+]. For a force to take precedence on an align rule, it must use a static version')
        standardOutput.contains('No static forces found for ruleset alignment-outputs-warnings-and-falls-back-to-default-logic-when-only-dynamic-forces-are-present align rule [group: test.nebula]. Falling back to default alignment logic')
        standardOutput.contains '+--- test.nebula:a:2.0.0\n'
        standardOutput.contains '+--- test.nebula:b:1.0.0 -> 2.0.0\n'
        standardOutput.contains '\\--- test.nebula:c:0.15.0 -> 2.0.0\n'
    }
}
