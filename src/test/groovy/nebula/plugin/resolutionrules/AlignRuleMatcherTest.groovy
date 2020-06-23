/**
 *
 *  Copyright 2014-2019 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package nebula.plugin.resolutionrules

import kotlin.text.Regex
import org.junit.Test
import spock.lang.Specification

class AlignRuleMatcherTest extends Specification {
    private static final String inputGroup = "test-group"
    public static final String ruleName = "test-rule"

    @Test
    void groupMatcherMatches() {
        given:
        def alignRule = createAlignRule()

        when:
        def matches = alignRule.ruleMatches(inputGroup, "test-name")

        then:
        assert matches
    }

    @Test
    void includesMatcherMatches() {
        given:
        def includes = new ArrayList()
        includes.add(new Regex("a"))
        includes.add(new Regex("b"))

        def excludes = new ArrayList()

        def alignRule = createAlignRule(includes, excludes)

        when:
        def matches = alignRule.ruleMatches(inputGroup, "a")

        then:
        assert matches
    }

    @Test
    void excludesMatcherMatches() {
        given:
        def includes = new ArrayList()

        def excludes = new ArrayList()
        excludes.add(new Regex("y"))
        excludes.add(new Regex("z"))

        def alignRule = createAlignRule(includes, excludes)

        when:
        def matches = alignRule.ruleMatches(inputGroup, "z")

        then:
        assert !matches
    }

    @Test
    void groupDoesNotMatch() {
        given:
        def alignRule = createAlignRule()

        when:
        def matches = alignRule.ruleMatches("other-group", "test-name")

        then:
        assert !matches
    }

    @Test
    void includesDoNotMatch() {
        given:
        def includes = new ArrayList()
        includes.add(new Regex("a"))
        includes.add(new Regex("b"))

        def excludes = new ArrayList()

        def alignRule = createAlignRule(includes, excludes)

        when:
        def matches = alignRule.ruleMatches(inputGroup, "something-else")

        then:
        assert !matches
    }

    @Test
    void excludesDoNotMatch() {
        given:
        def includes = new ArrayList()

        def excludes = new ArrayList()
        excludes.add(new Regex("y"))
        excludes.add(new Regex("z"))

        def alignRule = createAlignRule(includes, excludes)

        when:
        def matches = alignRule.ruleMatches(inputGroup, "something-else")

        then:
        assert matches
    }

    private static AlignRule createAlignRule(ArrayList includes = new ArrayList(), ArrayList excludes = new ArrayList()) {
        new AlignRule(
                ruleName,
                new Regex(inputGroup),
                includes,
                excludes,
                "match...",
                "test-rule-set",
                "reason",
                "author",
                "2015-10-07T20:21:20.368Z",
                ""
        )
    }
}
