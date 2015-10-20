package nebula.plugin.resolutionrules

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.util.regex.Matcher

class ResolutionJsonValidator extends DefaultTask {

    @InputFiles
    def rules =  project.files("${project.rootDir.absolutePath}/src/resolutionRules/resolution-rules.json")

    def checkValidGav(gav) {
        Matcher matcher = gav =~ /(?smx)
                                  ([^:]+)       # groupId
                                  :             # group and artifact separator
                                  ([^:]+)       # artifactId
                                  (:.+){0,1}/   // version is optional

        return matcher.matches()
    }

    def validateModuleName = { entry, action ->
        if (!entry.module) {
            return "* ${action}: ${JsonOutput.toJson(entry)} does not have a 'module' property"
        }

        if (!checkValidGav(entry.module)) {
            return "* ${action}: '${entry.module}' must be formatted as groupId:artifactId[:version]"
        }
    }

    def validateWith = { entry, action ->
        if (!entry.with) {
            return "* ${action}: ${entry} does not have a 'with' property"
        }

        if (!checkValidGav(entry.with)) {
            return "* ${action}: '${entry.with}' must be formatted as groupId:artifactId[:version]"

        }
    }

    def validateNonEmptyFields = { entry, action, fields ->
        def errors = []
        fields.each { field -> 
            if (!entry[field]) {
                errors << "* ${action}: ${entry} does not have a '${field}' property"
            }
        }
        return errors
    }

    def validateJsonFile(File file) {
        def json = new JsonSlurper().parseText(file.text)
        if (!json) {
            throw new GradleException("$file.absolutePath is not a valid resolution json file")
        }

        // ensure there are exactly 4 types
        if (json.size() != 4) {
            throw new GradleException('There must be exactly 4 resolution rule types defined')
        }

        // also ensure these 4 are the ones requires
        def typesProvided = json.collect { it.key }
        if (['replace', 'substitute', 'deny', 'reject'].intersect(typesProvided).size() != 4) {
            throw new GradleException('All resolution rule types must be specified (replace, substitute, deny, reject)')
        }

        // ensure all types are lists
        if (json.any { it.value.getClass() != java.util.ArrayList }) {
            throw new GradleException('All resolution rule types must be lists')
        }

        def replaceErrors = json.replace.collect({[
                validateModuleName(it, 'replace'), validateWith(it, 'replace'), validateNonEmptyFields(it, 'replace', ['reason', 'author'])
        ]})
        def substErrors = json.substitute.collect({[
             validateModuleName(it, 'substitute'), validateWith(it, 'substitute'),
             validateNonEmptyFields(it, 'substitute', ['reason', 'author'])
        ]})
        def denyErrors = json.deny.collect({[
                validateModuleName(it, 'deny'), validateNonEmptyFields(it, 'deny', ['reason', 'author'])
        ]})
        def rejectErrors = json.reject.collect({[
                validateModuleName(it, 'reject'), validateNonEmptyFields(it, 'reject', ['reason', 'author'])
        ]})

        def errors = [replaceErrors, substErrors, denyErrors, rejectErrors].flatten().findAll { it }
        if (errors) {
            throw new GradleException("Errors in file ${file.absolutePath}!\n${errors.join('\n')}")
        }
    }

    @TaskAction
    def validate() {
        rules.files.each { 
            validateJsonFile(it)
        }
    }

}

