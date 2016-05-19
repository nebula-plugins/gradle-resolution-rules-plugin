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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.MapType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

fun objectMapper(): ObjectMapper {
    val objectMapper = jacksonObjectMapper()

    val module = SimpleModule()
    module.setDeserializerModifier(NullToEmptyCollectionModifier())
    objectMapper.registerModule(module)
    return objectMapper
}

// From https://github.com/FasterXML/jackson-databind/issues/347#issuecomment-213153541
class NullToEmptyCollectionModifier : BeanDeserializerModifier() {
    // null as Map -> emptyMap
    override fun modifyMapDeserializer(config: DeserializationConfig?, type: MapType?,
                                       beanDesc: BeanDescription?, deserializer: JsonDeserializer<*>):
            JsonDeserializer<*>? =
            object : JsonDeserializer<Map<Any, Any>>(), ContextualDeserializer,
                    ResolvableDeserializer {
                @Suppress("UNCHECKED_CAST")
                override fun deserialize(jp: JsonParser, ctx: DeserializationContext?):
                        Map<Any, Any>? =
                        deserializer.deserialize(jp, ctx) as Map<Any, Any>?

                override fun createContextual(ctx: DeserializationContext?,
                                              property: BeanProperty?):
                        JsonDeserializer<*>? =
                        modifyMapDeserializer(config, type, beanDesc,
                                (deserializer as ContextualDeserializer)
                                        .createContextual(ctx, property))

                override fun resolve(ctx: DeserializationContext?) {
                    (deserializer as? ResolvableDeserializer)?.resolve(ctx)
                }

                override fun getNullValue(ctxt: DeserializationContext?) = emptyMap<Any, Any>()
            }

    // null as List -> emptyList
    override fun modifyCollectionDeserializer(config: DeserializationConfig?,
                                              type: CollectionType?, beanDesc: BeanDescription?,
                                              deserializer: JsonDeserializer<*>):
            JsonDeserializer<*>? =
            object : JsonDeserializer<List<Any>>(), ContextualDeserializer {
                @Suppress("UNCHECKED_CAST")
                override fun deserialize(jp: JsonParser, ctx: DeserializationContext?):
                        List<Any>? =
                        deserializer.deserialize(jp, ctx) as List<Any>?

                override fun createContextual(ctx: DeserializationContext?,
                                              property: BeanProperty?):
                        JsonDeserializer<*>? =
                        modifyCollectionDeserializer(config, type, beanDesc,
                                (deserializer as ContextualDeserializer)
                                        .createContextual(ctx, property))

                override fun getNullValue(ctxt: DeserializationContext?) = emptyList<Any>()
            }

}
