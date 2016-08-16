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

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class AlignRulesBasicSpec extends AbstractAlignRulesSpec {
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
        result.standardOutput.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z) aligning test.nebula:b to 1.0.0'
        result.standardOutput.contains 'test.nebula:a:1.0.0\n'
        result.standardOutput.contains 'test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    def 'alignment applies to versions affected by resolution strategies'() {
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
                compile 'test.nebula:c:1.0.0'
            }
            configurations.compile.resolutionStrategy.eachDependency { details ->
                if (details.requested.name == 'a') {
                    details.useVersion '0.15.0'
                }
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencyInsight', '--configuration', 'compile', '--dependency', 'test.nebula')

        then:
        def output = result.standardOutput
        output.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-applies-to-versions-affected-by-resolution-strategies, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z) aligning test.nebula:a to 1.0.0'
        output.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-applies-to-versions-affected-by-resolution-strategies, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z) aligning test.nebula:b to 1.0.0'
        output.contains 'test.nebula:a:1.0.0 (selected by rule)\n'
        output.contains 'test.nebula:b:1.0.0 (selected by rule)\n'
        output.contains 'test.nebula:b:0.15.0 -> 1.0.0\n'
        output.contains 'test.nebula:c:1.0.0\n'
    }

    def 'resolution strategies applied in beforeResolve apply'() {
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
            configurations.all {
                incoming.beforeResolve {
                    resolutionStrategy.eachDependency {
                        it.useVersion '19.0'
                    }
                }
            }

            repositories { jcenter() }
            dependencies {
                compile 'com.google.guava:guava'
            }
        """

        when:
        runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        noExceptionThrown()
    }

    def 'can iterate and resolve configurations'() {
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

            for (Configuration conf : configurations) {
                conf.resolvedConfiguration.resolvedArtifacts
            }
        """

        when:
        runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        noExceptionThrown()
    }
}
