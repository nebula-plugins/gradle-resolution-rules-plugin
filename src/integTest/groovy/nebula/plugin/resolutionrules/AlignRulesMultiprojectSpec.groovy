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
    File aDir
    File bDir

    def setup() {
        // Avoid deprecation warnings during parallel resolution while we look for a solution
        System.setProperty('ignoreDeprecations', 'true')
        System.setProperty('ignoreMutableProjectStateWarnings', 'true')

        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.resolution-rules'
            }
            allprojects {
                group = 'test.nebula'
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
        aDir.toPath().resolve("build.gradle").toFile() << """\
            plugins {
                id("java")
                id 'com.netflix.nebula.resolution-rules'
            }
"""
        bDir.toPath().resolve("build.gradle").toFile() << """\
            plugins {
                id("java-library")
                id 'com.netflix.nebula.resolution-rules'
            }
"""

    }

    @Unroll
    def 'align rules do not interfere with a multiproject that produces the jars being aligned (parallel #parallel)'() {
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
                plugins.withId("java") {
                    apply plugin: 'maven-publish'
    
                    publishing {
                        publications {
                            test(MavenPublication) {
                                from components.java
                            }
                        }
                        repositories {
                            maven {
                                name = 'repo'
                                url = 'build/repo'
                            }
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        def tasks = [':b:dependencies', '--configuration', 'compileClasspath']
        if (parallel) {
            tasks += "--parallel"
        }
        def results = runTasks(*tasks)

        then:
        results.output.contains("\\--- project ':a'\n")

        where:
        parallel << [false, true]
    }

    @Unroll
    def 'cycle like behavior (parallel #parallel)'() {
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
        def tasks = [':a:dependencies', ':b:dependencies', 'assemble']
        if (parallel) {
            tasks += "--parallel"
        }
        runTasks(*tasks)

        then:
        noExceptionThrown()

        where:
        parallel << [true, false]
    }

    @Unroll
    def 'can align project dependencies (parallel #parallel)'() {
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
                    maven { url = '${mavenrepo.absolutePath}' }
                }
            }
        """.stripIndent()

        aDir.toPath().resolve("build.gradle").toFile() << """\
dependencies {
   implementation project(':b')
}
"""
        bDir.toPath().resolve("build.gradle").toFile() << """\
dependencies {
    api 'other.nebula:a:1.0.0'
    api 'other.nebula:b:1.1.0'
    api 'other.nebula:c:0.42.0'
}
"""

        when:
        def tasks = [':a:dependencies', '--configuration', 'compileClasspath']
        if (parallel) {
            tasks += "--parallel"
        }
        def result = runTasks(*tasks)

        then:
        result.output.contains '+--- other.nebula:a:1.0.0 -> 1.1.0'
        result.output.contains '+--- other.nebula:b:1.1.0'
        result.output.contains '\\--- other.nebula:c:0.42.0'

        where:
        parallel << [true, false]
    }
}
