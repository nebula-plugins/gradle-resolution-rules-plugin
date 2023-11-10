package nebula.plugin.resolutionrules


abstract class AbstractAlignRulesSpec extends AbstractIntegrationTestKitSpec {
    def rulesJsonFile

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.resolution-rules'
                id 'java'
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
        """.stripIndent()
    }

    protected def createAlignAndReplaceRules(Map<String, String> modulesAndReplacements) {
        String reason = "★ custom replacement reason"
        rulesJsonFile << """
{
    "replace": [
""".stripIndent()

        List<String> replacements = new ArrayList<>()
        modulesAndReplacements.each { module, with ->
            replacements.add("""        {
            "module" : "$module",
            "with" : "$with",
            "reason" : "$reason",
            "author" : "Test user <test@example.com>",
            "date" : "2020-02-27T10:31:14.321Z"
        }""")

        }
        rulesJsonFile << replacements.join(',')

        rulesJsonFile << """
    ],
    "align": [
        {
            "group": "(test.nebula|test.nebula.ext)",
            "reason": "Align test.nebula dependencies",
            "author": "Example Person <person@example.org>",
            "date": "2020-02-27T10:31:14.321Z"
        }
    ]
}
""".stripIndent()
    }

    protected def createAlignAndSubstituteRules(Map<String, String> modulesAndSubstitutions) {
        String reason = "★ custom substitution reason"
        rulesJsonFile << """
{
    "substitute": [
""".stripIndent()

        List<String> substitutions = new ArrayList<>()
        modulesAndSubstitutions.each { module, with ->
            substitutions.add("""        {
            "module" : "$module",
            "with" : "$with",
            "reason" : "$reason",
            "author" : "Test user <test@example.com>",
            "date" : "2020-02-27T10:31:14.321Z"
        }""")
        }

        rulesJsonFile << substitutions.join(',')

        rulesJsonFile << """
    ],
    "align": [
        {
            "group": "(test.nebula|test.nebula.ext)",
            "reason": "Align test.nebula dependencies",
            "author": "Example Person <person@example.org>",
            "date": "2020-02-27T10:31:14.321Z"
        }
    ]
}
""".stripIndent()
    }
}
