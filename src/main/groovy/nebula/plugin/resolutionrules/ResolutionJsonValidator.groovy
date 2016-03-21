/*
 * Copyright 2015 Netflix, Inc.
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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.regex.Matcher

class ResolutionJsonValidator {
    static def checkValidGav(gav) {
        Matcher matcher = gav =~ /(?smx)
                                  ([^:]+)       # groupId
                                  :             # group and artifact separator
                                  ([^:]+)       # artifactId
                                  (:.+){0,1}/   // version is optional

        return matcher.matches()
    }

    static def validateJsonStream(InputStream input) {
        def json = new JsonSlurper().parse(input)
        validateJson(json)
    }

    static def validateJsonFile(File file) {
        def json = new JsonSlurper().parse(file)
        if (!json) {
            throw new InvalidRulesJsonException("$file.absolutePath is not a valid resolution json file")
        }
        validateJson(json)
    }

    static def validateJson(Object json) {
        // ensure there are exactly 5 types
        if (json.size() != 5) {
            throw new InvalidRulesJsonException('There must be exactly 5 resolution rule types defined')
        }

        // also ensure these 5 are the ones requires
        def typesProvided = json.collect { it.key }
        if (['replace', 'substitute', 'deny', 'reject', 'align'].intersect(typesProvided).size() != 5) {
            throw new InvalidRulesJsonException('All resolution rule types must be specified (replace, substitute, deny, reject, align)')
        }

        // ensure all types are lists
        if (json.any { it.value.getClass() != ArrayList }) {
            throw new InvalidRulesJsonException('All resolution rule types must be lists')
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

        def replaceErrors = json.replace.collect({
            [
                    validateModuleName(it, 'replace'), validateWith(it, 'replace'),
                    validateNonEmptyFields(it, 'replace', ['reason', 'author'])
            ]
        })
        def substErrors = json.substitute.collect({
            [
                    validateModuleName(it, 'substitute'), validateWith(it, 'substitute'),
                    validateNonEmptyFields(it, 'substitute', ['reason', 'author'])
            ]
        })
        def denyErrors = json.deny.collect({
            [
                    validateModuleName(it, 'deny'), validateNonEmptyFields(it, 'deny', ['reason', 'author'])
            ]
        })
        def rejectErrors = json.reject.collect({
            [
                    validateModuleName(it, 'reject'), validateNonEmptyFields(it, 'reject', ['reason', 'author'])
            ]
        })
        def alignErrors = json.align.collect({
            [
                    validateNonEmptyFields(it, 'align', ['group', 'reason', 'author'])
            ]
        })

        def errors = [replaceErrors, substErrors, denyErrors, rejectErrors, alignErrors].flatten().findAll { it }
        if (errors) {
            throw new InvalidRulesJsonException(errors as List<String>)
        }
    }

    static class InvalidRulesJsonException extends Exception {
        public InvalidRulesJsonException(String error) {
            super(error)
        }

        public InvalidRulesJsonException(List<String> errors) {
            super("Errors in json:\n${errors.join('\n')}")
        }
    }
}
