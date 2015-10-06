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

data class ModuleIdentifier(val organization: String, val name: String = "") {
    val coordinates by lazy(LazyThreadSafetyMode.NONE) { "$organization:$name" }

    override fun toString(): String = coordinates

    fun withVersion(version: String): ModuleVersionIdentifier = ModuleVersionIdentifier(organization, name, version)
}

data class ModuleVersionIdentifier(val organization: String, val name: String = "", val version: String = "") {
    val coordinates by lazy(LazyThreadSafetyMode.NONE) {
        if (version.isEmpty()) "$organization:$name" else "$organization:$name:$version"
    }

    override fun toString(): String = coordinates

    fun toModuleIdentifier() = ModuleIdentifier(organization, name)

    fun hasVersion(): Boolean {
        return !version.isEmpty()
    }
}

fun String.toModuleVersionIdentifier(): ModuleVersionIdentifier {
    val parts = split(':')
    return when (parts.size()) {
        3 -> ModuleVersionIdentifier(parts[0], parts[1], parts[2])
        2 -> ModuleVersionIdentifier(parts[0], parts[1], "")
        else -> throw IllegalArgumentException("Unknown module syntax: $this")
    }
}

fun String.toModuleIdentifier(): ModuleIdentifier {
    val parts = split(':')
    assert(parts.size() == 2)
    return ModuleIdentifier(parts[0], parts[1])
}
