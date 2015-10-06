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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.slf4j.LoggerFactory
import java.util.*
import java.util.zip.ZipFile

class ResolutionRulesPlugin : Plugin<Project> {
    val logger = LoggerFactory.getLogger(ResolutionRulesPlugin::class.java)
    val configurationName = "resolutionRules"

    override fun apply(project: Project) {
        val configuration = project.configurations.create(configurationName)
        project.afterEvaluate {
            val rules = rulesFromConfiguration(configuration)
            applyRules(rules, project)
        }
    }

    private fun rulesFromConfiguration(configuration: Configuration): Rules {
        val rules = ArrayList<Rules>()
        val mapper = createMapper()
        val files = configuration.resolve()
        if (files.isEmpty()) {
            logger.warn("No resolution rules have been added to the '{}' configuration", configuration.name)
        }
        for (file in files) {
            if (file.extension == "json") {
                logger.info("Using $file as a dependency rules source")
                rules.add(mapper.readValue(file, Rules::class.java))
            } else if (file.extension == "jar" || file.extension == "zip") {
                logger.info("Using $file as a dependency rules source")
                val jar = ZipFile(file)
                try {
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.endsWith(".json")) {
                            rules.add(mapper.readValue(jar.getInputStream(entry), Rules::class.java))
                        }
                    }
                } finally {
                    jar.close()
                }
            } else {
                logger.error("Unsupported rules file extension for $file")
            }
        }
        return rules.flattenRules()
    }

    fun createMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(KotlinModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .registerModule(JodaModule())
        return mapper
    }

    private fun applyRules(rules: Rules, project: Project) {
        rules.projectRules().forEach { it.apply(project) }
        project.configurations.all(object : Action<Configuration> {
            override fun execute(configuration: Configuration) {
                if (configuration.name == configurationName) {
                    return
                }
                if (configuration.state != Configuration.State.UNRESOLVED) {
                    logger.warn("Configuration '{}' has been resolved. Dependency resolution rules will not be applied", configuration.name)
                    return
                }
                rules.configurationRules().forEach {
                    it.apply(configuration)
                }
            }
        })
    }
}
