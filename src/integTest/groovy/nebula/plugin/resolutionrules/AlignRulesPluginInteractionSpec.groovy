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
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

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
                    classpath 'com.netflix.nebula:nebula-dependency-recommender:3.1.0'
                }
            }
            allprojects {
                apply plugin: 'nebula.dependency-recommender'
                ${applyPlugin(ResolutionRulesPlugin)}

                repositories {
                    ${mavenrepo.mavenRepositoryBlock}
                }
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }

            subprojects {
                apply plugin: 'java'
    
                dependencyRecommendations {
                   map recommendations: ['test.a:a': '1.42.2']
                }
            }
            
            project(':a') {
                dependencies {
                    compile project(':b')
                }
            }
            
            project(':b') {
                dependencies {
                    compile 'test.a:a'
                }
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully(':a:dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '\\--- test.a:a: -> 1.42.2\n'
    }

    @Unroll
    def 'align rules work with spring-boot version #springVersion'() {
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

        where:
        springVersion << ['1.4.0.RELEASE']
    }

    @Unroll
    def 'spring-boot interaction for version #springVersion'() {
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
            ${applyPlugin(ResolutionRulesPlugin)}

            repositories {
                jcenter()
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.+'
                compile 'org.springframework.boot:spring-boot-starter-web'
            }
        """.stripIndent()

        writeHelloWorld('example')

        when:
        def result = runTasksSuccessfully('compileJava', '--info')

        then:
        noExceptionThrown()

        where:
        springVersion << ['1.4.0.RELEASE']
    }

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
            ${applyPlugin(ResolutionRulesPlugin)}
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

                compile 'com.amazonaws:aws-java-sdk-s3'
                compile 'com.netflix.servo:servo-aws:0.12.12'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains('+--- com.amazonaws:aws-java-sdk-s3: -> 1.11.18')
    }

    def 'transitive aligns with Android Support dependency management'() {
        def rulesJsonFile = new File(projectDir, 'rules.json')

        rulesJsonFile << '''\
            {
                "align": [
                        {
                            "name": "Android Support",
                            "group": "com.android.support",
                            "reason": "Align android support libraries",
                            "author": "Josh Kasten <jkasten@gmail.com>",
                            "date": "2017-10-13"
                        }
                ],
                "replace": [],"substitute": [],"deny": [],"reject": []
            }
        '''.stripIndent()

        def gradleProps = new File(projectDir, 'gradle.properties')
        gradleProps <<'''\
            android.enableAapt2=false
        '''.stripIndent()


        new File("${projectDir}/src/main/").mkdirs()
        def androidManifest = new File("${projectDir}/src/main/", 'AndroidManifest.xml')
        androidManifest << '''\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.app.example">
            </manifest>
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories {
                      jcenter()
                      maven { url 'https://maven.google.com' }
                }
                dependencies {
                   classpath 'com.android.tools.build:gradle:3.0.0-beta6'
                }
            }
            
            allprojects {
                repositories {
                    jcenter()
                    maven { url 'https://maven.google.com' }
                }
            }

            apply plugin: 'com.android.application'
            ${applyPlugin(ResolutionRulesPlugin)}

            android {
                compileSdkVersion 26
                buildToolsVersion '26.0.1'
                 defaultConfig {
                    applicationId 'com.app.example'

                    minSdkVersion 15
                    targetSdkVersion 26
                    versionCode 1
                    versionName "1.0"
                }
            }
            
            dependencies {
                resolutionRules files('${rulesJsonFile.toString().replace("\\", "\\\\")}')

                compile 'com.android.support:appcompat-v7:26.0.0'
                compile 'com.android.support:support-v4:26.1.0'
            }
            
            configurations.all { config ->
               println 'configuration:' + config
            }
        """.stripIndent()

        when:
        def compileResult = runTasksSuccessfully('dependencies', '--configuration', 'compile')
        def processDebugResources = runTasks('processDebugResources')

        then:
        compileResult.standardOutput.contains('+--- com.android.support:appcompat-v7:26.0.0 -> 26.1.0')
        processDebugResources.standardOutput.contains('Merging result: SUCCESS')
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
                ${applyPlugin(ResolutionRulesPlugin)}
            
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
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:4.3.0'
                }
            }

            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
                maven { url '${mavenForRules.absolutePath}' }
            }

            dependencies {
                resolutionRules 'test.rules:resolution-rules:1.+'
                compile 'test.nebula:a:1.41.5'
                compile 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('dependencyInsight', '--dependency', 'a')

        then:
        results.standardOutput.contains 'test.nebula:a:1.41.5 -> 1.42.2\n'
        results.standardOutput.contains 'nebula.dependency-lock locked with: dependencies.lock\n'
        results.standardOutput.contains 'nebula.resolution-rules uses: resolution-rules-1.0.0.jar\n'
    }

    def 'dependency-lock when applied after wins out over new alignment rules'() {
        def (GradleDependencyGenerator mavenrepo, File rulesJsonFile) = dependencyLockAlignInteractionSetup()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:4.3.0'
                }
            }

            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.nebula:a:1.41.5'
                compile 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        results.standardOutput.contains '+--- test.nebula:a:1.41.5\n'
        results.standardOutput.contains '\\--- test.nebula:b:1.42.2\n'
    }

    def 'dependency-lock causes alignment to short circuit if dependencies are aligned by the lock file'() {
        def (GradleDependencyGenerator mavenrepo, File rulesJsonFile) = dependencyLockAlignInteractionSetup()

        def dependencyLock = new File(projectDir, 'dependencies.lock')
        dependencyLock.delete()
        dependencyLock << '''\
        {
            "compile": {
                "test.nebula:a": { "locked": "1.41.5" },
                "test.nebula:b": { "locked": "1.41.5" }
            }
        }
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:4.9.4'
                }
            }

            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'nebula.dependency-lock'
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.nebula:a:1.41.5'
                compile 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('dependencies', '--configuration', 'compile', '--debug')

        then:
        !results.standardOutput.contains('aligning test.nebula:a to [1.41.5,1.42.2]')
        results.standardOutput.contains '+--- test.nebula:a:1.41.5\n'
        results.standardOutput.contains '\\--- test.nebula:b:1.42.2 -> 1.41.5'
    }

    private List dependencyLockAlignInteractionSetup() {
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
            "compile": {
                "test.nebula:a": { "locked": "1.41.5" },
                "test.nebula:b": { "locked": "1.42.2" }
            }
        }
        '''.stripIndent()
        [mavenrepo, rulesJsonFile]
    }

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
            dependencyRecommendations {
                map recommendations: [
                    'test.nebula:a': '0.15.0',
                    'test.nebula:b': '0.15.0',
                    'test.nebula:c': '0.15.0'
                ]
            }
            
            dependencies {
                compile 'test.nebula:a'
                compile 'test.nebula:b'
                compile 'test.nebula:c'
            }
            '''.stripIndent())

        addSubproject('app', '''\
            configurations.compile.resolutionStrategy {
                force 'test.nebula:c:1.0.0'
            }
            
            dependencies {
                compile project(':common')
                compile 'test.nebula:a:1.0.0'
            }
            '''.stripIndent())

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies { classpath 'com.netflix.nebula:nebula-dependency-recommender:4.2.0' }
            }
            
            allprojects {
                ${applyPlugin(ResolutionRulesPlugin)}
                apply plugin: 'nebula.dependency-recommender'
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
        def result = runTasksSuccessfully(':app:dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '|    +--- test.nebula:b: FAILED'
        result.standardOutput.contains '|    \\--- test.nebula:c: -> 1.0.0 FAILED'
    }

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
            dependencyRecommendations {
                map recommendations: [
                    'test.nebula:a': '0.15.0',
                    'test.nebula:b': '0.15.0',
                    'test.nebula:c': '0.15.0'
                ]
            }
            
            dependencies {
                compile 'test.nebula:a'
                compile 'test.nebula:b'
                compile 'test.nebula:c'
            }
            '''.stripIndent())

        addSubproject('app', '''\
            configurations.compile.resolutionStrategy {
                force 'test.nebula:c:1.0.0'
            }
            
            dependencies {
                compile project(':common')
                compile 'test.nebula:a:1.0.0'
            }
            '''.stripIndent())

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies { classpath 'com.netflix.nebula:nebula-dependency-recommender:4.2.0' }
            }
            
            allprojects {
                ${applyPlugin(ResolutionRulesPlugin)}
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
        def result = runTasks(':app:dependencies', '--configuration', 'compile')

        then:
        result.standardOutput.contains '|    +--- test.nebula:b: FAILED'
        result.standardOutput.contains '|    \\--- test.nebula:c: -> 1.0.0 FAILED'
        result.standardError.contains 'Dependency test.nebula:a omitted version with no recommended version. General causes include a dependency being removed from the recommendation source or not applying a recommendation source to a project that depends on another project using a recommender.'
    }

    @Ignore
    def 'dependency-lock when applied before wins out over new alignment rules'() {
        def (GradleDependencyGenerator mavenrepo, File rulesJsonFile) = dependencyLockAlignInteractionSetup()

        buildFile << """\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:gradle-dependency-lock-plugin:4.3.0'
                }
            }

            apply plugin: 'nebula.dependency-lock'
            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'

            repositories {
                ${mavenrepo.mavenRepositoryBlock}
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
                compile 'test.nebula:a:1.41.5'
                compile 'test.nebula:b:1.42.2'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        results.standardOutput.contains '+--- test.nebula:a:1.41.5\n'
        results.standardOutput.contains '\\--- test.nebula:b:1.42.2\n'
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
