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
import spock.lang.Unroll

class AlignRulesForceStrictlyWithSubstitutionSpec extends AbstractAlignRulesSpec {
    def setup() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:a:1.2.0')
                .addModule('test.nebula:a:1.3.0')

                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.nebula:b:1.2.0')
                .addModule('test.nebula:b:1.3.0')

                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.1.0')
                .addModule('test.nebula:c:1.2.0')
                .addModule('test.nebula:c:1.3.0')

                .addModule(new ModuleBuilder('test.other:z:1.0.0').addDependency('test.nebula:a:1.2.0').build())

                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        String reason = "★ custom substitution reason"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module": "test.nebula:a:1.2.0",
                        "with": "test.nebula:a:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:b:1.2.0",
                        "with": "test.nebula:b:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:c:1.2.0",
                        "with": "test.nebula:c:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "align": [
                    {
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
        """.stripIndent()

        buildFile << """
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            """.stripIndent()

        debug = true
    }

    @Unroll
    def 'multiple forces on different versions - force and substitution from transitive dependency | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    force = true
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none']
        if(coreAlignment) {
            tasks.add('-Dnebula.features.coreAlignmentSupport=true')
        }
        def results = runTasks(*tasks)

        then:
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0' // force syntax is the primary contributor
        results.output.contains '- Forced'
        results.output.contains "- Selected by rule : substitution from 'test.nebula:a:1.2.0' to 'test.nebula:a:1.3.0' because ★ custom substitution reason"
        results.output.contains 'aligned'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.1.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple forces on different versions - force and substitution from direct dependency | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.2.0') {
                    force = true // force to bad version
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none']
        if(coreAlignment) {
            tasks.add('-Dnebula.features.coreAlignmentSupport=true')
        }
        def results = runTasks(*tasks)

        then:
        results.output.contains 'test.nebula:a:1.2.0 -> 1.3.0' // substitution rule is the primary contributor over force syntax
        results.output.contains '- Forced'
        results.output.contains "- Selected by rule : substitution from 'test.nebula:a:1.2.0' to 'test.nebula:a:1.3.0' because ★ custom substitution reason"
        results.output.contains 'aligned'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.3.0'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.3.0'

        where:
        coreAlignment << [false, true]
    }


    @Unroll
    def 'multiple forces on different versions - strict constraint and substitution from transitive dependency | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    version { strictly '1.1.0' }
                }
                implementation('test.nebula:b:1.1.0') { // added for alignment
                    version { strictly '1.1.0' }
                }
                implementation('test.nebula:c:1.1.0') { // added for alignment
                    version { strictly '1.1.0' }
                }
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        if(coreAlignment) {
            tasks.add('-Dnebula.features.coreAlignmentSupport=true')
        }
        def results = runTasks(*tasks)

        then:
        results.output.contains 'test.nebula:a:{strictly 1.1.0} -> 1.1.0' // rich version strictly is the primary contributor
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0'
        results.output.contains '- Forced'
        results.output.contains "- Selected by rule : substitution from 'test.nebula:a:1.2.0' to 'test.nebula:a:1.3.0' because ★ custom substitution reason"
        results.output.contains 'aligned'
        results.output.contains 'test.nebula:b:{strictly 1.1.0} -> 1.1.0'
        results.output.contains 'test.nebula:c:{strictly 1.1.0} -> 1.1.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'multiple forces on different versions - strict constraint and substitution from direct dependency | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.2.0') {
                    version { strictly '1.2.0' } // strict to bad version
                }
                implementation('test.nebula:b:1.2.0') { // added for alignment
                    version { strictly '1.2.0' } // strict to bad version
                }
                implementation('test.nebula:c:1.2.0') { // added for alignment
                    version { strictly '1.2.0' } // strict to bad version
                }
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        if(coreAlignment) {
            tasks.add('-Dnebula.features.coreAlignmentSupport=true')
        }
        def results = runTasks(*tasks)

        then:
        results.output.contains 'test.nebula:a:{strictly 1.2.0} -> 1.3.0' // substitution rule is the primary contributor over rich version strictly
        results.output.contains "- Selected by rule : substitution from 'test.nebula:a:1.2.0' to 'test.nebula:a:1.3.0' because ★ custom substitution reason"
        results.output.contains 'aligned'
        results.output.contains 'test.nebula:b:{strictly 1.2.0} -> 1.3.0'
        results.output.contains 'test.nebula:c:{strictly 1.2.0} -> 1.3.0'

        where:
        coreAlignment << [false, true]
    }

}
