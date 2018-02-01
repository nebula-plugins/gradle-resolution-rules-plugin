package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec

abstract class AbstractAlignRulesSpec extends IntegrationTestKitSpec {
    def rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            plugins {
                id 'nebula.resolution-rules'
                id 'java'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'aligntest'
        '''.stripIndent()
    }
}
