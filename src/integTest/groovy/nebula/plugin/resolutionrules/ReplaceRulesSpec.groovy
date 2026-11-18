package nebula.plugin.resolutionrules


import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class ReplaceRulesSpec extends IntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "rules.json")

        def graph = new DependencyGraphBuilder()
                .addModule('from:old-forest:1.0.0')
                .addModule('from:old-forest:2.0.0')
                .addModule('to:new-forest:1.0.0')
                .addModule('to:new-forest:2.0.0')
                .addModule(new ModuleBuilder('brings-from:brings-old:4.0.0').addDependency('from:old-forest:1.0.0').build())
                .addModule(new ModuleBuilder('brings-to:brings-new:5.0.0').addDependency('to:new-forest:1.0.0').build())
                .build()
        def mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/repo")
        mavenrepo.generateTestMavenRepo()

        buildFile << """
            plugins { 
                id 'java'
                id 'nebula.resolution-rules'
            }
            repositories {
                maven { url '${mavenrepo.mavenRepoDirPath}' }
            }
            dependencies {
                resolutionRules files("$rulesJsonFile")
            }
            """.stripIndent()

        rulesJsonFile << """
            {
                "replace": [
                    {
                        "module" : "from:old-forest",
                        "with" : "to:new-forest",
                        "reason" : "The old dependency should be replaced with the new one",
                        "author" : "Example Person <person@example.org>",
                        "date" : "2021-07-07T17:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()
    }

    def 'replacement works from one dependency to another'() {
        given:
        buildFile << """
            dependencies {
                implementation 'from:old-forest:1.0.0'
                implementation 'to:new-forest:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:1.0.0 -> to:new-forest:1.0.0')
        result.output.contains('replaced from:old-forest -> to:new-forest because \'The old dependency should be replaced with the new one\' by rule')
    }

    def 'does nothing when only one related dependency is on the configuration'() {
        given:
        buildFile << """
            dependencies {
                implementation 'from:old-forest:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:1.0.0\n')
    }

    def 'replacement works from higher version module to lower version replacement with'() {
        given:
        buildFile << """
            dependencies {
                implementation 'from:old-forest:2.0.0'
                implementation 'to:new-forest:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:2.0.0 -> to:new-forest:1.0.0')
        result.output.contains('replaced from:old-forest -> to:new-forest because \'The old dependency should be replaced with the new one\' by rule')
    }

    def 'replacement works from lower version module to higher version replacement with'() {
        given:
        buildFile << """
            dependencies {
                implementation 'brings-from:brings-old:4.0.0'
                implementation 'to:new-forest:2.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:1.0.0 -> to:new-forest:2.0.0')
        result.output.contains('replaced from:old-forest -> to:new-forest because \'The old dependency should be replaced with the new one\' by rule')
    }

    def 'replacement works with direct and transitive dependencies'() {
        given:
        buildFile << """
            dependencies {
                implementation 'brings-from:brings-old:4.0.0'
                implementation 'to:new-forest:2.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:1.0.0 -> to:new-forest:2.0.0')
        result.output.contains('replaced from:old-forest -> to:new-forest because \'The old dependency should be replaced with the new one\' by rule')
    }

    def 'replacement works with only-brought-in-transitively dependencies'() {
        given:
        buildFile << """
            dependencies {
                implementation 'brings-from:brings-old:4.0.0'
                implementation 'brings-to:brings-new:5.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'forest')

        then:
        result.output.contains('from:old-forest:1.0.0 -> to:new-forest:1.0.0')
        result.output.contains('replaced from:old-forest -> to:new-forest because \'The old dependency should be replaced with the new one\' by rule')
    }
}
