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
        setupProjectAndDependencies()
        debug = true
    }

    @Unroll
    def 'force to good version while substitution is triggered by a transitive dependency'() {
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
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.1.0\n'

        results.output.contains 'aligned'
        results.output.contains '- Forced'
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'force to bad version triggers a substitution'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.2.0') {
                    force = true // force to bad version triggers a substitution
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none']
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assert results.output.contains('test.nebula:a:1.2.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')
        results.output.contains 'aligned'
        results.output.contains('- Forced')
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'force to a good version while substitution is triggered by a direct dependency'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    force = true // force to good version
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.2.0' // bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none']
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.2.0 -> 1.1.0\n'

        results.output.contains 'aligned'
        results.output.toLowerCase().contains 'forced'
        results.output.contains "- Selected by rule : substituted test.nebula:c:1.2.0 with test.nebula:c:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'resolution strategy force to good version while substitution is triggered by a transitive dependency'() {
        buildFile << """\
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:1.1.0'
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.1.0'
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        assert results.output.contains('Multiple forces on different versions for virtual platform')
        assert results.output.contains('test.nebula:a:1.1.0 FAILED')

        results.output.contains 'aligned'
        results.output.contains '- Forced'
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"

    }

    @Unroll
    def 'resolution strategy force to bad version triggers a substitution'() {
        buildFile << """\
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:1.2.0' // force to bad version triggers a substitution
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assert results.output.contains('test.nebula:a:1.2.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')
        results.output.contains 'aligned'
        results.output.contains('- Forced')
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'resolution strategy force to a good version while substitution is triggered by a direct dependency'() {
        buildFile << """\
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:1.1.0'
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.1.0'
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.2.0' // bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.2.0 -> 1.1.0\n'

        results.output.contains 'aligned'
        results.output.toLowerCase().contains 'forced'
        results.output.contains "- Selected by rule : substituted test.nebula:c:1.2.0 with test.nebula:c:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'dependency with strict version declaration to a good version while a substitution is triggered by a transitive dependency'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    version { strictly '1.1.0' }
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // strictly rich version constraint to an okay version is the primary contributor
        assert results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0\n')
        assert results.output.contains('test.nebula:a:1.2.0 -> 1.1.0\n')
        assert results.output.contains('test.nebula:b:1.0.0 -> 1.1.0\n')
        assert results.output.contains('test.nebula:c:1.0.0 -> 1.1.0\n')
        assert results.output.contains('- Forced')

        results.output.contains 'aligned'
        results.output.contains("- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'")
    }

    @Unroll
    def 'dependency with strict version declaration to a bad version triggers a substitution'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a') {
                    version { strictly '1.2.0' } // strict to bad version
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        results.output.contains 'test.nebula:a:{strictly 1.2.0} -> 1.3.0'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.3.0'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.3.0'

        results.output.contains 'aligned'
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'dependency with strict version declaration to a good version while substitution is triggered by a direct dependency'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a') {
                    version { strictly '1.1.0' }
                }
                implementation('test.nebula:b') { // added for alignment
                    version { strictly '1.1.0' }
                }
                implementation'test.nebula:c:1.2.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // rich version strictly declaration to an okay version is the primary contributor; the substitution rule was a secondary contributor
        assert results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0')
        assert results.output.contains('test.nebula:b:{strictly 1.1.0} -> 1.1.0')
        assert results.output.contains('test.nebula:c:1.2.0 -> 1.1.0')
        assert results.output.contains('- Forced')

        results.output.contains 'aligned'
        results.output.contains("- Selected by rule : substituted test.nebula:c:1.2.0 with test.nebula:c:1.3.0 because '★ custom substitution reason'")
    }

    @Unroll
    def 'dependency constraint with strict version declaration to a good version while a substitution is triggered by a transitive dependency'() {
        buildFile << """\
            dependencies {
                constraints {
                    implementation('test.nebula:a') {
                        version { strictly("1.1.0") }
                        because '☘︎ custom constraint: test.nebula:a should be 1.1.0'
                    }
                }
                implementation 'test.other:z:1.0.0' // brings in bad version
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // strictly rich version constraint to an okay version is the primary contributor
        results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0\n')
        results.output.contains('test.nebula:a:1.2.0 -> 1.1.0\n')
        results.output.contains('test.nebula:b:1.0.0 -> 1.1.0\n')
        results.output.contains('test.nebula:c:1.0.0 -> 1.1.0\n')
        results.output.contains('- Forced')

        results.output.contains 'aligned'
        results.output.contains("- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'")
        results.output.contains 'By ancestor'
    }

    @Unroll
    def 'dependency constraint with strict version declaration to a bad version triggers a substitution'() {
        buildFile << """\
            dependencies {
                constraints {
                    implementation('test.nebula:a') {
                        version { strictly("1.2.0") }
                        because '☘︎ custom constraint: test.nebula:a should be 1.2.0'
                    }
                }
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        results.output.contains 'test.nebula:a:{strictly 1.2.0} -> 1.3.0'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.3.0'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.3.0'

        results.output.contains 'aligned'
        results.output.contains "- Selected by rule : substituted test.nebula:a:1.2.0 with test.nebula:a:1.3.0 because '★ custom substitution reason'"
    }

    @Unroll
    def 'dependency constraint with strict version declaration to a good version while substitution is triggered by a direct dependency'() {
        buildFile << """\
            dependencies {
                constraints {
                    implementation('test.nebula:a') {
                        version { strictly("1.1.0") }
                        because '☘︎ custom constraint: test.nebula:a should be 1.1.0'
                    }
                    implementation('test.nebula:b') {
                        version { strictly("1.1.0") }
                        because '☘︎ custom constraint: test.nebula:b should be 1.1.0'
                    }
                }
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation'test.nebula:c:1.2.0' // brings in bad version
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula']
        def results = runTasks(*tasks)

        then:
        // rich version strictly declaration to an okay version is the primary contributor; the substitution rule was a secondary contributor
        assert results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0')
        assert results.output.contains('test.nebula:b:{strictly 1.1.0} -> 1.1.0')
        assert results.output.contains('test.nebula:c:1.2.0 -> 1.1.0')
        assert results.output.contains('- Forced')
        assert results.output.contains('By ancestor')

        results.output.contains 'aligned'
        results.output.contains("- Selected by rule : substituted test.nebula:c:1.2.0 with test.nebula:c:1.3.0 because '★ custom substitution reason'")
    }

    void setupProjectAndDependencies() {
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
    }
}
