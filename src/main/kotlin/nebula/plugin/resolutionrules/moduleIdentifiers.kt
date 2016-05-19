/*
 * Copyright 2016 Netflix, Inc.
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

open class ModuleIdentifier(val organization: String, val name: String) {
    override fun toString(): String {
        return "$organization:$name"
    }

    companion object Factory {
        fun valueOf(notation: String): ModuleIdentifier {
            val parts = notation.split(':')
            assert (parts.size == 2)
            return ModuleIdentifier(parts[0], parts[1])
        }
    }
}

class ModuleVersionIdentifier(organization: String, name: String, val version: String) : ModuleIdentifier(organization, name) {
    override fun toString(): String {
        return if (version.isEmpty()) "$organization:$name" else "$organization:$name:$version"
    }

    fun hasVersion() = !version.isEmpty()

    companion object Factory {
        @JvmStatic
        fun valueOf(notation: String): ModuleVersionIdentifier {
            val parts = notation.split(':')
            if (parts.size == 3) {
                return ModuleVersionIdentifier(parts[0], parts[1], parts[2])
            } else if (parts.size == 2) {
                return ModuleVersionIdentifier(parts[0], parts[1], "")
            } else {
                throw IllegalArgumentException("Unknown module syntax: $notation")
            }
        }
    }
}
