package nebula.plugin.resolutionrules

import nebula.test.IntegrationSpec

abstract class AbstractAlignRulesSpec extends IntegrationSpec {
    def rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            ${applyPlugin(ResolutionRulesPlugin)}
            apply plugin: 'java'

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'aligntest'
        '''.stripIndent()
    }
}
