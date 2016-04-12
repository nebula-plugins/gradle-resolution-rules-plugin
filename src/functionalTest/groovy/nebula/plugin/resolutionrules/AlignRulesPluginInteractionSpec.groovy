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

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class AlignRulesPluginInteractionSpec extends IntegrationSpec {
    def 'alignment interaction with dependency-recommender'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.a:a:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesJsonFile = new File(projectDir, 'rules.json')

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
            buildscript {
                repositories { jcenter() }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:3.1.0'
                }
            }

            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'
            apply plugin: 'nebula.dependency-recommender'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencyRecommendations {
               map recommendations: ['test.a:a': '1.42.2']
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.a:a'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.a:a: -> 1.42.2\n'
    }

    def 'alignment interaction with dependency-recommender reverse order of application'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.a:a:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesJsonFile = new File(projectDir, 'rules.json')

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
            buildscript {
                repositories { jcenter() }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:3.1.0'
                }
            }

            apply plugin: 'nebula.dependency-recommender'
            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'


            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencyRecommendations {
               map recommendations: ['test.a:a': '1.42.2']
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.a:a'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.a:a: -> 1.42.2\n'
    }

    def 'align rules work with spring-boot'() {
        def rulesJsonFile = new File(projectDir, 'rules.json')
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
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath('org.springframework.boot:spring-boot-gradle-plugin:1.3.3.RELEASE')
                }
            }
            apply plugin: 'spring-boot'
            ${applyPlugin(ResolutionRulesPlugin)}

            repositories { jcenter() }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile('org.springframework.boot:spring-boot-starter-web')
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        noExceptionThrown()
    }

    def 'align rules work with extra-configurations and publishing'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.a:a:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesJsonFile = new File(projectDir, 'rules.json')

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "testNebula",
                        "group": "test.a",
                        "reason": "Align test.a dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-04-01T20:21:20.368Z"
                    }
                ]
            }
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories { jcenter() }

                dependencies {
                    classpath 'com.netflix.nebula:gradle-extra-configurations-plugin:3.0.3'
                }
            }

            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'
            apply plugin: 'nebula.provided-base'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                provided 'test.a:a:1.+'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.a:a:1.+ -> 1.42.2\n'
    }

    def 'publishing, provided, and dependency-recommender interacting with resolution-rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.a:a:1.42.2')
                .addModule('test.a:b:1.2.1')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesJsonFile = new File(projectDir, 'rules.json')

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
            buildscript {
                repositories { jcenter() }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:3.1.0'
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.4.4'
                    classpath 'com.netflix.nebula:gradle-extra-configurations-plugin:3.0.3'
                }
            }

            apply plugin: 'nebula.dependency-recommender'
            apply plugin: 'nebula.maven-publish'
            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'
            apply plugin: 'nebula.provided-base'


            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencyRecommendations {
               map recommendations: ['test.a:a': '1.42.2', 'test.a:b': '1.2.1']
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.a:a'
                provided 'test.a:b'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '+--- test.a:a: -> 1.42.2\n'
        result.standardOutput.contains '\\--- test.a:b: -> 1.2.1\n'
    }

    @spock.lang.Ignore
    def 'cycle like behavior'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:c:1.42.2')
                .addModule('test.nebula:d:1.2.1')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesJsonFile = new File(projectDir, 'rules.json')

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                ]
            }
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories { jcenter() }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.4.4'
                    classpath 'com.netflix.nebula:gradle-extra-configurations-plugin:3.0.3'
                }
            }
            subprojects {
                apply plugin: 'nebula.maven-publish'
                apply plugin: 'nebula.provided-base'
                ${applyPlugin(ResolutionRulesPlugin)}
                apply plugin: 'java'



                repositories {
                    ${mavenrepo.mavenRepositoryBlock}
                }

                dependencies {
                    resolutionRules files('$rulesJsonFile')
                }
            }
        """.stripIndent()

        def aDir = addSubproject('a', '''\
            dependencies {
                compile 'test.nebula:c:1.+'
                testCompile project(':b')
            }
        '''.stripIndent())
        def bDir = addSubproject('b', '''\
            dependencies {
                compile 'test.nebula:d:[1.0.0, 2.0.0)'
                compile project(':a')
            }
        '''.stripIndent())

        when:
        def results = runTasksSuccessfully(':a:dependencies', ':b:dependencies', 'assemble')

        then:
        noExceptionThrown()
    }
}
