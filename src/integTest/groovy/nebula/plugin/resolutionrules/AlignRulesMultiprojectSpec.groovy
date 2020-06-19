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
 *
 */
package nebula.plugin.resolutionrules


import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class AlignRulesMultiprojectSpec extends IntegrationTestKitSpec {
    def rulesJsonFile
    def aDir
    def bDir

    def setup() {
        definePluginOutsideOfPluginBlock = true
        debug = true
        keepFiles = true
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            subprojects {
                apply plugin: 'nebula.resolution-rules'
                group = 'test.nebula'
            }

            project(':a') {
                apply plugin: 'java'
            }
            
            project(':b') {
                apply plugin: 'java-library'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'aligntest'
        '''.stripIndent()

        aDir = addSubproject('a')
        bDir = addSubproject('b')
    }

    @Unroll
    def 'align rules do not interfere with a multiproject that produces the jars being aligned'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                        {
                            "name": "testNebula",
                            "group": "test.nebula",
                            "includes": ["a", "b"],
                            "reason": "Align test.nebula dependencies",
                            "author": "Example Person <person@example.org>",
                            "date": "2016-03-17T20:21:20.368Z"
                        }
                ]
            }
        '''.stripIndent()

        // project b depends on a
        new File(bDir, 'build.gradle') << '''\
            dependencies {
                implementation project(':a')
            }
        '''.stripIndent()

        buildFile << '''\
            subprojects {
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
            }
        '''.stripIndent()

        when:
        def results = runTasks(':b:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        results.output.contains('\\--- project :a\n')

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'cycle like behavior'() {
        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        new File(aDir, 'build.gradle') << '''\
            dependencies {
                testImplementation project(':b')
            }
        '''.stripIndent()

        new File(bDir, 'build.gradle') << '''\
            dependencies {
                implementation project(':a')
            }
        '''.stripIndent()

        when:
        def results = runTasks(':a:dependencies', ':b:dependencies', 'assemble', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'can align project dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('other.nebula:a:0.42.0')
                .addModule('other.nebula:a:1.0.0')
                .addModule('other.nebula:a:1.1.0')
                .addModule('other.nebula:b:0.42.0')
                .addModule('other.nebula:b:1.0.0')
                .addModule('other.nebula:b:1.1.0')
                .addModule('other.nebula:c:0.42.0')
                .addModule('other.nebula:c:1.0.0')
                .addModule('other.nebula:c:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "group": "other.nebula",
                        "includes": [ "a", "b" ],
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            subprojects {
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
            }

            project(':a') {
                dependencies {
                   implementation project(':b')
                }
            }

            project(':b') {
                dependencies {
                    api 'other.nebula:a:1.0.0'
                    api 'other.nebula:b:1.1.0'
                    api 'other.nebula:c:0.42.0'
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':a:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- other.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '+--- other.nebula:b:1.1.0'
        result.output.contains '\\--- other.nebula:c:0.42.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'root project can depend on subprojects'() {
        def graph = new DependencyGraphBuilder()
                .addModule('other.nebula:a:0.42.0')
                .addModule('other.nebula:a:1.0.0')
                .addModule('other.nebula:a:1.1.0')
                .addModule('other.nebula:b:0.42.0')
                .addModule('other.nebula:b:1.0.0')
                .addModule('other.nebula:b:1.1.0')
                .addModule('other.nebula:c:0.42.0')
                .addModule('other.nebula:c:1.0.0')
                .addModule('other.nebula:c:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "group": "other.nebula",
                        "includes": [ "a", "b" ],
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            apply plugin: 'java'

            subprojects {
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
            }

            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            project(':a') {
                dependencies {
                   implementation project(':b')
                }
            }

            project(':b') {
                dependencies {
                    api 'other.nebula:a:1.0.0'
                    api 'other.nebula:b:1.1.0'
                    api 'other.nebula:c:0.42.0'
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':a:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- other.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '+--- other.nebula:b:1.1.0'
        result.output.contains '\\--- other.nebula:c:0.42.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'alignment for multiprojects works - parallel #parallelMode - core alignment #coreAlignment'() {
        def graph = new DependencyGraphBuilder()
                .addModule('other.nebula:a:0.42.0')
                .addModule('other.nebula:a:1.0.0')
                .addModule('other.nebula:a:1.1.0')
                .addModule('other.nebula:a:2.0.0')
                .addModule('other.nebula:b:0.42.0')
                .addModule('other.nebula:b:1.0.0')
                .addModule('other.nebula:b:1.1.0')
                .addModule('other.nebula:b:2.0.0')
                .addModule('other.nebula:c:0.42.0')
                .addModule('other.nebula:c:1.0.0')
                .addModule('other.nebula:c:1.1.0')
                .addModule('other.nebula:c:2.0.0')
                .addModule('other.nebula:d:0.42.0')
                .addModule('other.nebula:d:1.0.0')
                .addModule('other.nebula:d:1.1.0')
                .addModule('other.nebula:e:0.42.0')
                .addModule('other.nebula:e:1.0.0')
                .addModule('other.nebula:e:1.1.0')
                .addModule('other.nebula:f:0.42.0')
                .addModule('other.nebula:f:1.0.0')
                .addModule('other.nebula:f:1.1.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                        {
                            "name": "otherNebula",
                            "group": "other.nebula",
                            "reason": "Align other.nebula dependencies",
                            "author": "Example Person <person@example.org>",
                            "date": "2016-03-17T20:21:20.368Z"
                        }
                ]
            }
        '''.stripIndent()

        buildFile << """ \
            subprojects {
                task dependenciesForAll(type: DependencyReportTask) {}
                task dependencyInsightForAll(type: DependencyInsightReportTask) {}
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
            }
            project(':c') {
                apply plugin: 'java'
            }
            """.stripIndent()

        def aBuildFile = new File(aDir, 'build.gradle')
        aBuildFile << """
            dependencies {
                    implementation 'other.nebula:a:1.0.0'
                    implementation 'other.nebula:b:1.1.0'
                    implementation 'other.nebula:c:0.42.0'
            }
            """.stripIndent()

        def bBuildFile = new File(bDir, 'build.gradle')
        bBuildFile << """
            dependencies {
                    implementation 'other.nebula:d:1.0.0'
                    implementation 'other.nebula:e:1.0.0'
                    implementation 'other.nebula:f:0.42.0'
            }
            """.stripIndent()

        addSubproject('c', """
            dependencies {
                    implementation 'other.nebula:a:2.0.0' // same module, different version as in :a
                    implementation 'other.nebula:b:1.1.0' // same module, same version as in :a
                    implementation 'other.nebula:c:0.42.0'
            }
            """.stripIndent())

        when:
        def tasks = ['dependenciesForAll', '--configuration', 'compileClasspath',
                     "-Dnebula.features.coreAlignmentSupport=$coreAlignment", '--warning-mode', 'none']
        if (parallelMode) {
            tasks.add('--parallel')
        }
        def result = runTasks(*tasks)
        // we see warnings about `The configuration :resolutionRules was resolved without accessing the project in a safe manner.` when used in parallel mode

        then:
        // subproject a
        result.output.contains 'other.nebula:a:1.0.0 -> 1.1.0\n'
        result.output.contains 'other.nebula:b:1.1.0\n'
        result.output.contains 'other.nebula:c:0.42.0 -> 1.1.0\n'

        // subproject b
        result.output.contains 'other.nebula:d:1.0.0\n'
        result.output.contains 'other.nebula:e:1.0.0\n'
        result.output.contains 'other.nebula:f:0.42.0 -> 1.0.0\n'

        // subproject c
        result.output.contains 'other.nebula:a:2.0.0\n'
        result.output.contains 'other.nebula:b:1.1.0 -> 2.0.0\n'
        result.output.contains 'other.nebula:c:0.42.0 -> 2.0.0\n'

        when:
        def dependencyInsightTasks = ['dependencyInsightForAll', '--configuration', 'compileClasspath',
                                      '--dependency', 'other.nebula',
                                      "-Dnebula.features.coreAlignmentSupport=$coreAlignment", '--warning-mode', 'none']
        if (parallelMode) {
            dependencyInsightTasks.add('--parallel')
        }
        def dependencyInsightResult = runTasks(*dependencyInsightTasks)

        then:
        !dependencyInsightResult.output.contains('unspecified')
        if (coreAlignment) {
            dependencyInsightResult.output.contains('- By conflict resolution : between versions 1.1.0 and 0.42.0\n')
            dependencyInsightResult.output.contains('- By conflict resolution : between versions 1.1.0 and 1.0.0\n')
            dependencyInsightResult.output.contains('- By conflict resolution : between versions 2.0.0 and 0.42.0\n')
            dependencyInsightResult.output.contains('- By conflict resolution : between versions 2.0.0 and 1.1.0\n')
        }

        where:
        coreAlignment | parallelMode
        false         | false
        false         | true
        true          | false
        true          | true
    }
}
