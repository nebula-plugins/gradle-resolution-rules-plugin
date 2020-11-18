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
import spock.lang.Issue
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class AlignRulesPluginInteractionSpec extends IntegrationTestKitSpec {
    def setup() {
        definePluginOutsideOfPluginBlock = true
        debug = true
        keepFiles = true
    }

    @Unroll
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
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1'
                }
            }

            apply plugin: 'nebula.resolution-rules'
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
                implementation 'test.a:a'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '\\--- test.a:a -> 1.42.2\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1'
                }
            }

            apply plugin: 'nebula.dependency-recommender'
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'java'


            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencyRecommendations {
               map recommendations: ['test.a:a': '1.42.2']
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation 'test.a:a'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '\\--- test.a:a -> 1.42.2\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'alignment interaction with dependency-recommender transitive project dependencies'() {
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

        addSubproject('a')
        addSubproject('b')

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1'
                }
            }
            allprojects {
                apply plugin: 'nebula.dependency-recommender'
                apply plugin: 'nebula.resolution-rules'

                repositories {
                    ${mavenrepo.mavenRepositoryBlock}
                }
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }

            subprojects {
                dependencyRecommendations {
                   map recommendations: ['test.a:a': '1.42.2']
                }
            }
            
            project(':a') {
                apply plugin: 'java'
            
                dependencies {
                    implementation project(':b')
                }
            }
            
            project(':b') {
                apply plugin: 'java-library'
            
                dependencies {
                    api 'test.a:a'
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':a:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '\\--- test.a:a -> 1.42.2\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'align rules work with spring-boot version #springVersion - core alignment #coreAlignment'() {
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
                    classpath('org.springframework.boot:spring-boot-gradle-plugin:${springVersion}')
                }
            }
            apply plugin: 'spring-boot'
            apply plugin: 'nebula.resolution-rules'

            repositories { jcenter() }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation('org.springframework.boot:spring-boot-starter-web')
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        springVersion = '1.4.0.RELEASE'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'spring-boot interaction for version #springVersion - core alignment #coreAlignment'() {
        def rulesFolder = new File(projectDir, 'rules')
        rulesFolder.mkdirs()
        def rulesJsonFile = new File(rulesFolder, 'rules.json')

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

        def mavenForRules = new File(projectDir, 'repo')
        mavenForRules.mkdirs()
        def locked = new File(mavenForRules, 'test/rules/resolution-rules/1.0.0')
        locked.mkdirs()
        createRulesJar([rulesFolder], projectDir, new File(locked, 'resolution-rules-1.0.0.jar'))
        createPom('test.rules', 'resolution-rules', '1.0.0', locked)

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath('org.springframework.boot:spring-boot-gradle-plugin:${springVersion}')
                }
            }

            apply plugin: 'spring-boot'
            apply plugin: 'nebula.resolution-rules'

            repositories {
                jcenter()
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.0.0'
                implementation 'org.springframework.boot:spring-boot-starter-web'
            }
        """.stripIndent()

        writeHelloWorld('example')

        when:
        def result = runTasks('compileJava', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        springVersion = '1.4.0.RELEASE'
        coreAlignment << [false, true]
    }

    @Unroll
    def 'transitive aligns with spring dependency management'() {
        def rulesJsonFile = new File(projectDir, 'rules.json')

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [],
                "align": [
                    {
                        "name": "align-aws-java-sdk",
                        "group": "com.amazonaws",
                        "includes": ["aws-java-sdk", "aws-java-sdk-.*"],
                        "excludes": ["aws-java-sdk-(handwritten-samples|sample-extractor|samples-pom|generated-samples|samples|archetype|swf-libraries)"],
                        "reason": "Align AWS Java SDK libraries",
                        "author": "Danny Thomas <dannyt@netflix.com>",
                        "date": "2016-04-28T22:31:14.321Z"
                    }
                ]
            }
        '''.stripIndent()


        buildFile << """\
            buildscript {
                dependencies {
                    classpath 'io.spring.gradle:dependency-management-plugin:0.6.1.RELEASE'
                }
                repositories {
                    jcenter()
                }
            }

            apply plugin: 'java'
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'io.spring.dependency-management'
            
            dependencyManagement {
                imports {
                    mavenBom 'org.springframework.boot:spring-boot-starter-parent:1.4.3.RELEASE'
                    mavenBom 'org.springframework.cloud:spring-cloud-dependencies:Camden.SR3'
                }
            }
            
            repositories {
                jcenter()
            }
            
            dependencies {
                resolutionRules files('$rulesJsonFile')

                implementation 'com.amazonaws:aws-java-sdk-s3'
                implementation 'com.netflix.servo:servo-aws:0.12.12'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains('+--- com.amazonaws:aws-java-sdk-s3 -> 1.11.18')

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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

            apply plugin: 'nebula.resolution-rules'
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
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '\\--- test.a:a:1.+ -> 1.42.2\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1'
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.4.4'
                    classpath 'com.netflix.nebula:gradle-extra-configurations-plugin:3.0.3'
                }
            }

            apply plugin: 'nebula.dependency-recommender'
            apply plugin: 'nebula.maven-publish'
            apply plugin: 'nebula.resolution-rules'
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
                implementation 'test.a:a'
                provided 'test.a:b'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '+--- test.a:a -> 1.42.2\n'
        result.output.contains '\\--- test.a:b -> 1.2.1\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
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
                repositories {
                    jcenter()
                }

                dependencies {
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.4.4'
                    classpath 'com.netflix.nebula:gradle-extra-configurations-plugin:3.1.0'
                }
            }
            allprojects {
                apply plugin: 'nebula.resolution-rules'
            
                repositories {
                    ${mavenrepo.mavenRepositoryBlock}
                }
            }
            
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }

            subprojects {
                apply plugin: 'nebula.maven-publish'
                apply plugin: 'nebula.provided-base'
                apply plugin: 'java'
            }
        """.stripIndent()

        def aDir = addSubproject('a', '''\
            dependencies {
                implementation 'test.nebula:c:1.+'
                testImplementation project(':b')
            }
        '''.stripIndent())
        def bDir = addSubproject('b', '''\
            dependencies {
                implementation 'test.nebula:d:[1.0.0, 2.0.0)'
                implementation project(':a')
            }
        '''.stripIndent())

        when:
        def results = runTasks(':a:dependencies', ':b:dependencies', 'assemble', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        noExceptionThrown()

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'able to lock rules'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.41.5')
                .addModule('test.nebula:a:1.42.2')
                .addModule('test.nebula:b:1.41.5')
                .addModule('test.nebula:b:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "$projectDir/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesFolder = new File(projectDir, 'rules')
        rulesFolder.mkdirs()
        def rulesJsonFile = new File(rulesFolder, 'rules.json')

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

        def mavenForRules = new File(projectDir, 'repo')
        mavenForRules.mkdirs()
        def locked = new File(mavenForRules, 'test/rules/resolution-rules/1.0.0')
        locked.mkdirs()
        createRulesJar([rulesFolder], projectDir, new File(locked, 'resolution-rules-1.0.0.jar'))
        createPom('test.rules', 'resolution-rules', '1.0.0', locked)

        rulesJsonFile.text = '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [], "align": []
            }
        '''.stripIndent()
        def newer = new File(mavenForRules, 'test/rules/resolution-rules/1.1.0')
        newer.mkdirs()
        createRulesJar([rulesFolder], projectDir, new File(newer, 'resolution-rules-1.1.0.jar'))
        createPom('test.rules', 'resolution-rules', '1.1.0', newer)

        def dependencyLock = new File(projectDir, 'dependencies.lock')

        dependencyLock << '''\
        {
            "resolutionRules": {
                "test.rules:resolution-rules": { "locked": "1.0.0" }
            }
        }
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }

            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.+'
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencyInsight', '--dependency', 'a', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def resultsForRules = runTasks('dependencyInsight', '--dependency', 'test.rules', '--configuration', 'resolutionRules', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        results.output.contains 'test.nebula:a:1.41.5 -> 1.42.2\n'
        results.output.contains 'test.nebula:b:1.42.2\n'

        resultsForRules.output.contains 'test.rules:resolution-rules:1.+ -> 1.0.0\n'
        resultsForRules.output.contains 'Selected by rule : test.rules:resolution-rules locked to 1.0.0'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dependency-lock when applied after wins out over new locked alignment rules - coreAlignment #coreAlignment'() {
        def (GradleDependencyGenerator mavenrepo, File mavenForRules, File jsonRuleFile) = dependencyLockAlignInteractionSetupWithLockedResolutionRulesConfiguration()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }

            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.+'
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def resultsForRules = runTasks('dependencies', '--configuration', 'resolutionRules', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        // results using resolution rules that do not yet align test.nebula
        results.output.contains 'test.nebula:a:1.41.5\n'
        results.output.contains 'test.nebula:b:1.42.2\n'
        resultsForRules.output.contains 'test.rules:resolution-rules:1.+ -> 1.0.0\n'

        when:
        def resultsIgnoringLocks = runTasks('-PdependencyLock.ignore=true', 'dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def resultsForRulesIgnoringLocks = runTasks('-PdependencyLock.ignore=true', 'dependencies', '--configuration', 'resolutionRules', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        // final results if we ignore locks
        resultsIgnoringLocks.output.contains 'test.nebula:a:1.41.5 -> 1.42.2\n'
        resultsIgnoringLocks.output.contains 'test.nebula:b:1.42.2\n'
        resultsForRulesIgnoringLocks.output.contains 'test.rules:resolution-rules:1.+ -> 1.1.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dependency-lock plugin applied after resolution-rules plugin with non-locked resolution rules - #description - core alignment #coreAlignment'() {
        // note: this is a more unusual case. Typically resolution rules are distributed like a library, version controlled, and locked like other dependencies
        def (GradleDependencyGenerator mavenrepo, File rulesJsonFile) = dependencyLockAlignInteractionSetupWithUnlockedResolutionRulesConfiguration()
        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
            """.stripIndent()

        when:
        def results
        def tasks = ['dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        if(coreAlignment) {
            results = runTasksAndFail(*tasks)
        } else {
            results = runTasks(*tasks)
        }

        then:
        if (coreAlignment) {
            assert results.output.contains('Dependency lock state is out of date:')
            assert results.output.contains("Resolved 'test.nebula:a:1.42.2' instead of locked version '1.41.5' for project")

            assert results.output.contains('+--- test.nebula:a:1.41.5 -> 1.42.2\n')
            assert results.output.contains('\\--- test.nebula:b:1.42.2\n')
        } else {
            assert results.output.contains('+--- test.nebula:a:1.41.5\n')
            assert results.output.contains('\\--- test.nebula:b:1.42.2\n')
        }

        when:
        def ignoreLocksResults = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment", '-PdependencyLock.ignore=true')

        then:
        ignoreLocksResults.output.contains '+--- test.nebula:a:1.41.5 -> 1.42.2\n'
        ignoreLocksResults.output.contains '\\--- test.nebula:b:1.42.2\n'

        when:
        runTasks('generateLock', 'saveLock', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def locksUpdatedResults = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        locksUpdatedResults.output.contains '+--- test.nebula:a:1.41.5 -> 1.42.2\n'
        locksUpdatedResults.output.contains '\\--- test.nebula:b:1.42.2\n'

        where:
        coreAlignment | description
        false         | 'locks win out over new alignment rules'
        true          | 'fail due to dependency lock state is out of date'
    }

    @Unroll
    def 'dependency-lock causes alignment to short circuit if dependencies are aligned by the lock file - core alignment #coreAlignment'() {
        def (GradleDependencyGenerator mavenrepo, File jsonRuleFile) = dependencyLockAlignInteractionSetupWithUnlockedResolutionRulesConfiguration()

        assert jsonRuleFile.exists()
        assert jsonRuleFile.text.contains('"group": "test.nebula"')

        def dependencyLock = new File(projectDir, 'dependencies.lock')
        dependencyLock.delete()
        dependencyLock << '''\
        {
            "compileClasspath": {
                "test.nebula:a": { "locked": "1.41.5" },
                "test.nebula:b": { "locked": "1.41.5" }
            }
        }
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }

            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencies {
                resolutionRules files('$jsonRuleFile')
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        !results.output.contains('aligning test.nebula:a to [1.41.5,1.42.2]')
        results.output.contains '+--- test.nebula:a:1.41.5\n'
        results.output.contains '\\--- test.nebula:b:1.42.2 -> 1.41.5'

        where:
        coreAlignment << [false, true]
    }

    private List dependencyLockAlignInteractionSetupWithLockedResolutionRulesConfiguration() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.41.5')
                .addModule('test.nebula:a:1.42.2')
                .addModule('test.nebula:b:1.41.5')
                .addModule('test.nebula:b:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "$projectDir/testrepogen")
        mavenrepo.generateTestMavenRepo()

        def rulesFolder = new File(projectDir, 'rules')
        rulesFolder.mkdirs()
        def rulesJsonFile = new File(rulesFolder, 'rules.json')

        rulesJsonFile << '''\
            {
                "deny": [], "reject": [], "substitute": [], "replace": [], "align": []
            }
        '''.stripIndent()

        def mavenForRules = new File(projectDir, 'repo')
        mavenForRules.mkdirs()
        def locked = new File(mavenForRules, 'test/rules/resolution-rules/1.0.0')
        locked.mkdirs()
        createRulesJar([rulesFolder], projectDir, new File(locked, 'resolution-rules-1.0.0.jar'))
        createPom('test.rules', 'resolution-rules', '1.0.0', locked)

        rulesJsonFile.text = '''\
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
        def newer = new File(mavenForRules, 'test/rules/resolution-rules/1.1.0')
        newer.mkdirs()
        createRulesJar([rulesFolder], projectDir, new File(newer, 'resolution-rules-1.1.0.jar'))
        createPom('test.rules', 'resolution-rules', '1.1.0', newer)

        def mavenMetadataXml = new File(mavenForRules, 'test/rules/resolution-rules/maven-metadata.xml')
        mavenMetadataXml.createNewFile()
        mavenMetadataXml << '''<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>test.rules</groupId>
  <artifactId>resolution-rules</artifactId>
  <versioning>
    <latest>1.1.0</latest>
    <release>1.1.0</release>
    <versions>
      <version>1.0.0</version>
      <version>1.1.0</version>
    </versions>
    <lastUpdated>20200320014943</lastUpdated>
  </versioning>
</metadata>
'''

        def dependencyLock = new File(projectDir, 'dependencies.lock')
        dependencyLock << '''\
        {
            "compileClasspath": {
                "test.nebula:a": { "locked": "1.41.5" },
                "test.nebula:b": { "locked": "1.42.2" }
            },
            "resolutionRules": {
                "test.rules:resolution-rules": { "locked": "1.0.0" }
            }
        }
        '''.stripIndent()
        [mavenrepo, mavenForRules, rulesJsonFile]
    }

    private List dependencyLockAlignInteractionSetupWithUnlockedResolutionRulesConfiguration() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.41.5')
                .addModule('test.nebula:a:1.42.2')
                .addModule('test.nebula:b:1.41.5')
                .addModule('test.nebula:b:1.42.2')
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "$projectDir/testrepogen")
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

        def dependencyLock = new File(projectDir, 'dependencies.lock')

        dependencyLock << '''\
        {
            "compileClasspath": {
                "test.nebula:a": { "locked": "1.41.5" },
                "test.nebula:b": { "locked": "1.42.2" }
            }
        }
        '''.stripIndent()
        [mavenrepo, rulesJsonFile]
    }

    @Unroll
    @Issue('#55')
    def 'alignment does not infinite loop on force to non existent version'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

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

        addSubproject('common', '''\
            apply plugin: 'java-library'

            dependencyRecommendations {
                map recommendations: [
                    'test.nebula:a': '0.15.0',
                    'test.nebula:b': '0.15.0',
                    'test.nebula:c': '0.15.0'
                ]
            }
            
            dependencies {
                api 'test.nebula:a'
                api 'test.nebula:b'
                api 'test.nebula:c'
            }
            '''.stripIndent())

        addSubproject('app', '''\
            apply plugin: 'java'

            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:c:1.0.0'
            }
            
            dependencies {
                implementation project(':common')
                implementation 'test.nebula:a:1.0.0'
            }
            '''.stripIndent())

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies { classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1' }
            }
            
            allprojects {
                apply plugin: 'nebula.resolution-rules'
                apply plugin: 'nebula.dependency-recommender'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }

            subprojects {
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':app:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '|    +--- test.nebula:b FAILED'
        result.output.contains '|    \\--- test.nebula:c -> 1.0.0 FAILED'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    @Issue('#55')
    def 'alignment does not infinite loop on force to non existent version with recommender strictMode'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:0.15.0')
                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:0.15.0')
                .addModule('test.nebula:c:0.15.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

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

        addSubproject('common', '''\
            apply plugin: 'java-library'
            
            dependencyRecommendations {
                map recommendations: [
                    'test.nebula:a': '0.15.0',
                    'test.nebula:b': '0.15.0',
                    'test.nebula:c': '0.15.0'
                ]
            }
            
            dependencies {
                api 'test.nebula:a'
                api 'test.nebula:b'
                api 'test.nebula:c'
            }
            '''.stripIndent())

        addSubproject('app', '''\
            apply plugin: 'java'

            configurations.compileClasspath.resolutionStrategy {
                force 'test.nebula:c:1.0.0'
            }
            
            dependencies {
                implementation project(':common')
                implementation 'test.nebula:a:1.0.0'
            }
            '''.stripIndent())

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies { classpath 'com.netflix.nebula:nebula-dependency-recommender:9.0.1' }
            }
            
            allprojects {
                apply plugin: 'nebula.resolution-rules'
                apply plugin: 'nebula.dependency-recommender'
                dependencyRecommendations {
                    strictMode = true
                }
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }

            subprojects {
                apply plugin: 'java'
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':app:dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        result.output.contains '|    +--- test.nebula:b FAILED'
        result.output.contains '|    \\--- test.nebula:c -> 1.0.0 FAILED'
        def expectedMessage = 'Dependency test.nebula:a omitted version with no recommended version. General causes include a dependency being removed from the recommendation source or not applying a recommendation source to a project that depends on another project using a recommender.'
        result.output.contains(expectedMessage)

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dependency-lock when applied before wins out over new locked alignment rules - core alignment #coreAlignment'() {
        def (GradleDependencyGenerator mavenrepo, File mavenForRules, File jsonRuleFile) = dependencyLockAlignInteractionSetupWithLockedResolutionRulesConfiguration()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }

            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.+'
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def resultsForRules = runTasks('dependencies', '--configuration', 'resolutionRules', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        // results using resolution rules that do not yet align test.nebula
        results.output.contains 'test.nebula:a:1.41.5\n'
        results.output.contains 'test.nebula:b:1.42.2\n'
        resultsForRules.output.contains 'test.rules:resolution-rules:1.+ -> 1.0.0\n'

        when:
        def resultsIgnoringLocks = runTasks('-PdependencyLock.ignore=true', 'dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def resultsForRulesIgnoringLocks = runTasks('-PdependencyLock.ignore=true', 'dependencies', '--configuration', 'resolutionRules', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        // final results if we ignore locks
        resultsIgnoringLocks.output.contains 'test.nebula:a:1.41.5 -> 1.42.2\n'
        resultsIgnoringLocks.output.contains 'test.nebula:b:1.42.2\n'
        resultsForRulesIgnoringLocks.output.contains 'test.rules:resolution-rules:1.+ -> 1.1.0\n'

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dependency-lock plugin applied before resolution-rules plugin with non-locked resolution rules - #description - core alignment #coreAlignment'() {
        // note: this is a more unusual case. Typically resolution rules are distributed like a library, version controlled, and locked like other dependencies
        def (GradleDependencyGenerator mavenrepo, File rulesJsonFile) = dependencyLockAlignInteractionSetupWithUnlockedResolutionRulesConfiguration()
        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:11.+'
                }
            }
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'nebula.resolution-rules'
            apply plugin: 'java'
            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
                implementation 'test.nebula:a:1.41.5'
                implementation 'test.nebula:b:1.42.2'
            }
            """.stripIndent()

        when:
        def results
        def tasks = ['dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        if(coreAlignment) {
            results = runTasksAndFail(*tasks)
        } else {
            results = runTasks(*tasks)
        }

        then:
        if (coreAlignment) {
            assert results.output.contains('Dependency lock state is out of date:')
            assert results.output.contains("Resolved 'test.nebula:a:1.42.2' instead of locked version '1.41.5' for project")

            assert results.output.contains('+--- test.nebula:a:1.41.5 -> 1.42.2\n')
            assert results.output.contains('\\--- test.nebula:b:1.42.2\n')
        } else {
            // plugin ordering is important. Dependency lock plugin must be applied after the resolution rules plugin.
            // This test case is simply showcasing the current behavior.
            assert results.output.contains('+--- test.nebula:a:1.41.5 -> 1.42.2\n')
            // this does not honor the locked versions
            assert results.output.contains('\\--- test.nebula:b:1.42.2\n')
        }

        when:
        def ignoreLocksResults = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment", '-PdependencyLock.ignore=true')

        then:
        ignoreLocksResults.output.contains '+--- test.nebula:a:1.41.5 -> 1.42.2\n'
        ignoreLocksResults.output.contains '\\--- test.nebula:b:1.42.2\n'
        !ignoreLocksResults.output.contains('FAILED')

        when:
        runTasks('generateLock', 'saveLock', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")
        def locksUpdatedResults = runTasks('dependencies', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment")

        then:
        locksUpdatedResults.output.contains '+--- test.nebula:a:1.41.5 -> 1.42.2\n'
        locksUpdatedResults.output.contains '\\--- test.nebula:b:1.42.2\n'

        where:
        coreAlignment | description
        false         | 'locks do not win. Plugin ordering is important.'
        true          | 'fail due to dependency lock state is out of date'
    }

    private createRulesJar(Collection<File> files, File unneededRoot, File destination) {
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, '1.0')
        JarOutputStream target = new JarOutputStream(new FileOutputStream(destination), manifest)
        files.each { add(it, unneededRoot, target) }
        target.close()
    }

    private createPom(String group, String name, String version, File dir) {
        def pom = new File(dir, "${name}-${version}.pom")
        pom.text = """\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>${group}</groupId>
              <artifactId>${name}</artifactId>
              <version>${version}</version>
            </project>
        """.stripIndent()
    }

    private void add(File source, File unneededRoot, JarOutputStream target) throws IOException {
        def prefix = "${unneededRoot.path}/"
        if (source.isDirectory()) {
            String dirName = source.path - prefix
            if (!dirName.endsWith('/')) {
                dirName += '/'
            }
            def entry = new JarEntry(dirName)
            target.putNextEntry(entry)
            target.closeEntry()
            source.listFiles().each { nested ->
                add(nested, unneededRoot, target)
            }
        } else {
            def entry = new JarEntry(source.path - prefix)
            target.putNextEntry(entry)
            target << source.bytes
            target.closeEntry()
        }
    }
}
