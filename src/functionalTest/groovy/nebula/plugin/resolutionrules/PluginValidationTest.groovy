package nebula.plugin.resolutionrules

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import nebula.test.IntegrationSpec

import static org.codehaus.groovy.runtime.StackTraceUtils.extractRootCause

class PluginValidationTest extends IntegrationSpec {

    def rulesJsonFile
    def rulesJson
    def rulesTemplate = """\
        {
            "replace" : [
                {
                    "module" : "asm:asm",
                    "with" : "org.ow2.asm:asm",
                    "reason" : "The asm group id changed for 4.0 and later",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                }
            ],
            "substitute": [
                {
                    "module" : "bouncycastle:bcprov-jdk15",
                    "with" : "org.bouncycastle:bcprov-jdk15:latest.release",
                    "reason" : "The latest version of BC is required, using the new coordinate",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                }
            ],
            "deny": [
                {
                    "module": "com.google.guava:guava:19.0-rc2",
                    "reason" : "Guava 19.0-rc2 is not permitted",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                },
                {
                    "module": "com.sun.jersey:jersey-bundle",
                    "reason" : "jersey-bundle is a far jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                }
            ],
            "reject": [
                {
                    "module": "com.google.guava:guava:12.0",
                    "reason" : "Guava 12.0 significantly regressed LocalCache performance",
                    "author" : "Danny Thomas <dmthomas@gmail.com>",
                    "date" : "2015-10-07T20:21:20.368Z"
                }
            ]
        }
        """

    def setup() {
        rulesJsonFile = new File(projectDir, "${moduleName}.json")

        rulesJson = new JsonSlurper().parseText(rulesTemplate)

        buildFile << """\
             apply plugin: 'java'
             apply plugin: 'nebula.resolution-rules-producer'

             repositories {
                 jcenter()
             }

             checkResolutionRulesSyntax {
                 rules files("$rulesJsonFile")
             }
        """.stripIndent()
    }

    def 'check validation works with valid rules'() {
        rulesJsonFile.text = JsonOutput.toJson(rulesJson)

        expect:
        runTasksSuccessfully('checkResolutionRulesSyntax')
    }

    def 'check validation for no module name'() {
        def jsonWithoutModuleNames = rulesJson.each { k, arr ->
            arr.each {
                it.remove('module')
            }
        }

        rulesJsonFile.text = JsonOutput.toJson(jsonWithoutModuleNames)

        when:
        def result = runTasks('checkResolutionRulesSyntax')

        then:
        def rootCause = extractRootCause(result.failure)
        rootCause.message.count("does not have a 'module' property") == 5
    }

    def 'check module name is validated'() {
        rulesJson.with {
            replace[0].module = 'asmasm' // invalid groupId:artifactId
            substitute[0].module = '' // empty group id
        }

        rulesJsonFile.text = JsonOutput.toJson(rulesJson)

        when:
        def result = runTasks('checkResolutionRulesSyntax')

        then:
        def rootCause = extractRootCause(result.failure)
        rootCause.message.with {
            contains("'asmasm' must be formatted")
            count("does not have a 'module' property") == 1
        }
    }

    def 'check that "with", "reason" and "author" fields are validated'() {
        rulesJson.with {
            replace[0].remove('with')
            replace[0].author = ''
            substitute[0].with = ''
            substitute[0].remove('author')
            reject[0].remove('reason')
        }

        rulesJsonFile.text = JsonOutput.toJson(rulesJson)

        when:
        def result = runTasks('checkResolutionRulesSyntax')

        then:
        def rootCause = extractRootCause(result.failure)
        rootCause.message.with {
            count("does not have a 'with' property") == 2
            count("does not have a 'author' property") == 2
            contains("does not have a 'reason' property")
        }
    }

    def 'check that it fails when types are missing'() {
        rulesJson.remove('replace')

        rulesJsonFile.text = JsonOutput.toJson(rulesJson)

        when:
        def result = runTasks('checkResolutionRulesSyntax')

        then:
        def rootCause = extractRootCause(result.failure)
        rootCause.message.contains('There must be exactly 4 resolution rule types defined')
    }

    def 'check that validation task mandates lists for types'() {
        rulesJson.replace = 'What is a string doing here instead of a List?'

        rulesJsonFile.text = JsonOutput.toJson(rulesJson)

        when:
        def result = runTasks('checkResolutionRulesSyntax')

        then:
        def rootCause = extractRootCause(result.failure)
        rootCause.message.contains('All resolution rule types must be lists')
    }


}