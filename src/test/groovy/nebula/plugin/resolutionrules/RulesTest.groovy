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

import spock.lang.Specification

/**
 * Tests for {@link RuleSet}.
 */
class RulesTest extends Specification {
    def 'json deserialised'() {
        when:
        String json = """{
                        "replace" : [
                            {
                                "module" : "asm:asm",
                                "with" : "org.ow2.asm:asm",
                                "reason" : "The asm group id changed for 4.0 and later",
                                "author" : "Danny Thomas <dmthomas@gmail.com>",
                                "date" : "2015-10-07T20:21:20.368Z"
                            }
                        ],
                        "substitute": [],
                        "reject": [],
                        "deny": [],
                        "align": [],
                        "exclude": []
                      }"""


        RuleSet rules = parseJsonText(json)

        then:
        !rules.replace.isEmpty()
        rules.replace[0].class == ReplaceRule
    }

    def 'json deserialised with one category of rules'() {
        when:
        String json = """{
                        "replace" : [
                            {
                                "module" : "asm:asm",
                                "with" : "org.ow2.asm:asm",
                                "reason" : "The asm group id changed for 4.0 and later",
                                "author" : "Danny Thomas <dmthomas@gmail.com>",
                                "date" : "2015-10-07T20:21:20.368Z"
                            }
                        ]
                      }"""


        RuleSet rules = parseJsonText(json)

        then:
        !rules.replace.isEmpty()
        rules.replace[0].class == ReplaceRule
    }

    static RuleSet parseJsonText(String json) {
        def ruleSet = JsonKt.objectMapper().readValue(json, RuleSet)
        return RulesKt.withName(ruleSet, "dummy")
    }
}
