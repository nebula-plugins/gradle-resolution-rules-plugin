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

public class ModuleIdentifier {
    String organization
    String name

    @Override
    String toString() {
        return "$organization:$name"
    }

    public static ModuleIdentifier valueOf(String notation) {
        String[] parts = notation.split(':')
        assert (parts.size() == 2)
        return new ModuleIdentifier(organization: parts[0], name: parts[1])
    }
}

public class ModuleVersionIdentifier extends ModuleIdentifier {
    String version = ""

    public String toString() {
        return (version.isEmpty()) ? "$organization:$name" : "$organization:$name:$version"
    }

    public boolean hasVersion() {
        return !version.isEmpty()
    }

    public static ModuleVersionIdentifier valueOf(String notation) {
        String[] parts = notation.split(':')
        if (parts.size() == 3) {
            return new ModuleVersionIdentifier(organization: parts[0], name: parts[1], version: parts[2])
        } else if (parts.size() == 2) {
            return new ModuleVersionIdentifier(organization: parts[0], name: parts[1])
        } else {
            throw new IllegalArgumentException("Unknown module syntax: $notation")
        }
    }
}
