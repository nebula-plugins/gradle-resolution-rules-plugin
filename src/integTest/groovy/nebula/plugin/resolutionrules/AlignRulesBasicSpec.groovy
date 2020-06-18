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
import org.gradle.api.logging.LogLevel
import spock.lang.Unroll

class AlignRulesBasicSpec extends AbstractAlignRulesSpec {

    @Unroll
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
                implementation 'test.other:c:1.0.0'
                implementation 'test.nebula:a:2.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '\\--- test.nebula:b:1.0.0 -> test.nebula.ext:b:2.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
                implementation 'test.nebula:c:0.42.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '+--- test.nebula:b:1.1.0'
        result.output.contains '\\--- test.nebula:c:0.42.0'

        where:
        coreAlignment << [false, true]
    }

    def 'dependencyInsight has extra info for alignment'() {
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
                implementation 'test.nebula:b:1.1.0'
                implementation 'test.nebula:c:0.42.0'
            }
        """.stripIndent()

        when:
        def aResult = runTasks('dependencyInsight', '--dependency', 'a')

        then:
        // 'a' is aligned
        aResult.output.contains 'test.nebula:a:1.1'
        aResult.output.contains 'aligned to 1.1.0 by rule dependencyInsight-has-extra-info-for-alignment'
        aResult.output.findAll("test.nebula:a:.* -> 1.1.0").size() > 0

        when:
        // 'b' did not need aligning
        def bResult = runTasks('dependencyInsight', '--dependency', 'b')

        then:
        bResult.output.findAll("test.nebula:b:.* -> 1.1.0").size() == 0

        when:
        def cResult = runTasks('dependencyInsight', '--dependency', 'c')

        then:
        // 'c' is aligned
        cResult.output.contains 'test.nebula:c:1.1.0'
        cResult.output.contains "aligned to 1.1.0 by rule dependencyInsight-has-extra-info-for-alignment aligning group 'test.nebula'"
        cResult.output.findAll("test.nebula:c:.* -> 1.1.0").size() > 0
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
                implementation 'test.nebula:c:0.42.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '+--- test.nebula:b:1.1.0\n'
        result.output.contains '\\--- test.nebula:c:0.42.0 -> 1.1.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
                implementation 'test.other:c:1.0.0'
                implementation 'test.other:d:0.12.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains 'test.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains 'test.nebula:b:1.1.0\n'
        result.output.contains 'test.other:c:1.0.0\n'
        result.output.contains 'test.other:d:0.12.+ -> 1.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
                implementation 'old.org:sub:latest.release'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.output.contains '\\--- old.org:sub:latest.release -> new.org:sub:0.2.0\n'

        where:
        coreAlignment << [false, true]
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
                implementation 'com.google.guava:guava:12.0'
            }
        """

        when:
        runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        noExceptionThrown()
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
                implementation 'test.example:c:latest.release'
                implementation 'test:x:1.+'
                implementation 'test:y:1.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.output.contains '+--- test.example:c:latest.release -> 0.1.0\n'
        result.output.contains '+--- test:x:1.+ -> 1.0.0\n'
        result.output.contains '\\--- test:y:1.+ -> 1.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula.one:a:1.0.0'
                implementation 'test.nebula.two:b:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula.one:a:1.0.0\n'
        result.output.contains '\\--- test.nebula.two:b:0.15.0 -> 1.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
                implementation 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '+--- test.nebula:b:0.15.0 -> 1.0.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
                implementation 'test.nebula:c:0.15.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0\n'
        result.output.contains '+--- test.nebula:b:0.15.0\n'
        result.output.contains '\\--- test.nebula:c:0.15.0\n'

        where:
        coreAlignment << [false, true]
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
            }
        """.stripIndent()

        logLevel = LogLevel.DEBUG

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test.nebula')

        then:
        !result.output.contains('Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version-0-for-test.nebula) aligning test.nebula:a to 1.0.0')
        result.output.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=alignment-does-not-apply-to-dependencies-that-already-have-the-expected-version-0-for-test.nebula) aligning test.nebula:b to 1.0.0'
        result.output.contains 'test.nebula:a:1.0.0\n'
        result.output.contains 'test.nebula:b:0.15.0 -> 1.0.0\n'
    }

    @Unroll
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
                implementation 'test.nebula:c:1.0.0'
            }
            configurations.compileClasspath.resolutionStrategy.eachDependency { details ->
                if (details.requested.name == 'a') {
                    details.useVersion '0.15.0'
                }
            }
        """.stripIndent()

        logLevel = LogLevel.DEBUG

        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]

        when:
        def debugResult = runTasks(*tasks)

        then:
        def debugOutput = debugResult.output

        // assertions in debug mode
        //debugOutput.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-applies-to-versions-affected-by-resolution-strategies, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=alignment-applies-to-versions-affected-by-resolution-strategies-0) aligning test.nebula:a to 1.0.0'
        //debugOutput.contains 'Resolution rule AlignRule(name=testNebula, group=test.nebula, includes=[], excludes=[], match=null, ruleSet=alignment-applies-to-versions-affected-by-resolution-strategies, reason=Align test.nebula dependencies, author=Example Person <person@example.org>, date=2016-03-17T20:21:20.368Z, belongsToName=alignment-applies-to-versions-affected-by-resolution-strategies-0) aligning test.nebula:b to 1.0.0'

        when:
        logLevel = LogLevel.INFO

        def result = runTasks(*tasks)

        then:
        def output = result.output

        // reasons
        output.contains 'test.nebula:b:0.15.0 -> 1.0.0'
        if(coreAlignment) {
            output.contains 'belongs to platform aligned-platform'
        } else {
            output.contains 'aligned to 1.0.0 by alignment-applies-to-versions-affected-by-resolution-strategies'
            output.contains 'with reasons: nebula.resolution-rules uses:'
        }

        // final result
        if(coreAlignment) {
            output.contains 'test.nebula:a:0.15.0\n' // the resolution strategy takes precedence in core alignment
        } else {
            output.contains 'test.nebula:a:1.0.0\n'
        }
        output.contains 'test.nebula:b:1.0.0\n'
        output.contains 'test.nebula:c:1.0.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                implementation 'com.google.guava:guava'
            }
        """

        when:
        runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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

            configurations.findAll { it.isCanBeResolved() }.collect { it.resolvedConfiguration.resolvedArtifacts }
        """

        when:
        runTasks('dependencies', '--configuration', 'compileClasspath', '--warning-mode=none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    def 'alignment is short-circuited for configurations that have no aligned dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula.b",
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
            }
        """.stripIndent()

        logLevel = LogLevel.DEBUG

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('Short-circuiting alignment for configuration \':compileClasspath\' - No align rules matched the configured configurations')
    }

    def 'alignment is short-circuited for configurations that are already aligned'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule('test.nebula:c:1.0.0')
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
                implementation 'test.nebula:c:1.0.0'
            }
        """.stripIndent()

        logLevel = LogLevel.DEBUG

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath')

        then:
        result.output.contains('Short-circuiting alignment for configuration \':compileClasspath\' - No align rules matched the configured configurations')
    }

    def 'non-transitive configurations are skipped'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.nebula.b",
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
            configurations.compileClasspath.transitive = false
            dependencies {
                implementation 'test.nebula:a:1.0.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', '--debug')

        then:
        result.output.contains('Skipping alignment for configuration \':compileClasspath\' - Configuration is not transitive')
    }

    @Unroll
    def 'configurations with artifacts can be aligned'() {
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
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:0.15.0'
            }
            
            task myZip(type: Zip)
            
            artifacts {
                runtimeElements myZip
            }        
        """.stripIndent()

        when:
        def result = runTasks('dependencies', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'aligning with circular dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('example.nebula:aligntest:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.1.0').addDependency('example.nebula:aligntest:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('example.nebula:aligntest:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.1.0').addDependency('example.nebula:aligntest:1.0.0').build())
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

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
            group = 'example.nebula'
            version = '1.1.0'
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '\\--- test.nebula:b:1.1.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'align ourselves via circular dependency'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.nebula:a:1.0.0').addDependency('example.nebula:sub0:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:a:1.1.0').addDependency('example.nebula:sub0:1.1.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.0.0').addDependency('example.nebula:sub1:1.0.0').build())
                .addModule(new ModuleBuilder('test.nebula:b:1.1.0').addDependency('example.nebula:sub1:1.1.0').build())
                .addModule('test:foo:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "example.nebula",
                        "reason": "Align example.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2018-01-30T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile.text = """\
            plugins {
                id 'nebula.resolution-rules'
            }
            allprojects {
                group = 'example.nebula'
                version = '1.2.0'
            }
            subprojects {
                apply plugin: 'nebula.resolution-rules'
                apply plugin: 'java'
                repositories {
                    ${generator.mavenRepositoryBlock}
                }
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        addSubproject('sub0', '''\
            dependencies {
                implementation 'test.nebula:a:1.0.0'
                implementation 'test.nebula:b:1.1.0'
                implementation project(':sub1')
            }
            '''.stripIndent())
        addSubproject('sub1', '''\
            dependencies {
                implementation 'test:foo:1.0.0'
            }
            '''.stripIndent())

        when:
        def result = runTasks(':sub0:dependencies', '--configuration', 'compileClasspath', ':sub1:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains 'example.nebula:sub0:1.0.0 -> project :sub0'
        result.output.contains 'example.nebula:sub1:1.1.0 -> project :sub1'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'align com.google.inject'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('com.google.inject:guice:4.2.2').build())
                .addModule(new ModuleBuilder('com.google.inject:guice:4.1.0').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-multibindings:4.1.0').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-multibindings:4.2.2').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-assistedinject:4.1.0').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-assistedinject:4.2.2').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-throwingproviders:4.1.0').build())
                .addModule(new ModuleBuilder('com.google.inject.extensions:guice-throwingproviders:4.2.2').build())
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "guice-align",
                        "group": "(com\\\\.google\\\\.inject|com\\\\.google\\\\.inject\\\\.extensions)",
                        "excludes": ["guice-(struts2-plugin|throwing-providers|assisted-inject|dagger-adapter)"],
                        "includes": [],
                        "reason": "Misaligned Guice jars cause strange runtime errors.",
                        "author": "Example Person <person@example.org>",
                        "date": "2018-01-30T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile.text = """\
            plugins {
                id 'nebula.resolution-rules'
            }
  
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'java'
            repositories {
                    ${generator.mavenRepositoryBlock}
            }
            
            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation 'com.google.inject:guice:4.2.2'
                implementation 'com.google.inject.extensions:guice-multibindings:4.1.0'
                implementation 'com.google.inject.extensions:guice-assistedinject:4.1.0'
                implementation 'com.google.inject.extensions:guice-throwingproviders:4.1.0'
                
                modules {
                    module('com.google.inject:guice-assistedinject') {
                        replacedBy('com.google.inject.extensions:guice-assistedinject')
                    }
                    module('com.google.inject:guice-throwingproviders') {
                        replacedBy('com.google.inject.extensions:guice-throwingproviders')
                    }
                }
            }
            
            
        """.stripIndent()


        when:
        def result = runTasks('dependencyInsight', '--dependency', 'guice-multibindings', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        if(coreAlignment) {
            result.output.contains 'belongs to platform aligned-platform:align-com-google-inject'
        } else {
            result.output.contains 'aligned to 4.2.2 by align-com-google-inject'
            result.output.contains 'with reasons: nebula.resolution-rules uses: align-com-google-inject.json'
        }
        result.output.contains 'com.google.inject.extensions:guice-multibindings:4.1.0 -> 4.2.2'

        where:
        coreAlignment << [false, true]
    }
}
