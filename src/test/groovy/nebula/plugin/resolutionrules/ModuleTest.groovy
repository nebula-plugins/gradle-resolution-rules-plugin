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

class ModuleTest extends Specification {
    def 'module version identifier parsed'() {
        given:
        def module = ModuleVersionIdentifier.valueOf('com.sun.jersey:jersey-bundle')

        expect:
        module.organization == 'com.sun.jersey'
        module.name == 'jersey-bundle'
        module.toString() == 'com.sun.jersey:jersey-bundle'
        !module.hasVersion()
    }

    def 'invalid module identifier throws IAE'() {
        when:
        ModuleVersionIdentifier.valueOf('com.sun.jersey')

        then:
        thrown(IllegalArgumentException)
    }
}
