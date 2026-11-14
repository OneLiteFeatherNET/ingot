/*
 * Copyright (c) 2023 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.storage

import com.fasterxml.classmate.ResolvedType
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.databind.deser.ValueInstantiator
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.contains
import com.github.victools.jsonschema.generator.FieldScope
import com.github.victools.jsonschema.generator.SchemaGenerationContext
import com.reposilite.configuration.shared.EnumResolver
import com.reposilite.configuration.shared.SubtypeResolver
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

class StorageProviderSerdes {

    companion object {

        private val STORAGE_PROVIDERS: Map<String, Class<out StorageProviderSettings>> =
            ServiceLoader.load(StorageProviderFactory::class.java).associate { factory -> factory.type to factory.settingsType }

    }

    class StorageProviderDeserializationProblemHandler : DeserializationProblemHandler() {

        /**
         * The configured mappers, each with a copy that reads decimals as exact BigDecimal, so that an unresolved block keeps the number
         * text it was written with, trailing zeros included. The copy keeps every other feature of its mapper.
         */
        private val exactTreeReaders = ConcurrentHashMap<ObjectMapper, ObjectMapper>()

        override fun handleMissingInstantiator(ctx: DeserializationContext, type: Class<*>, instantiator: ValueInstantiator, parser: JsonParser, message: String): Any =
            when (type) {
                StorageProviderSettings::class.java -> parser.parseStorageProvider() ?: super.handleMissingInstantiator(ctx, type, instantiator, parser, message)
                else -> super.handleMissingInstantiator(ctx, type, instantiator, parser, message)
            }

        private fun JsonParser.parseStorageProvider(): Any? =
            codec?.let {
                val json = exactTreeReader(it).readTree<JsonNode>(this)

                val type = json?.get("type")
                    ?.asText()
                    ?: if (json?.contains("bucketName") == true) "s3" else "fs" // currently jsonforms doesn't send 'type' when oneOf is changed in UI, so we have to guess

                val settingsType = STORAGE_PROVIDERS[type]
                return when {
                    settingsType != null -> it.treeToValue(json, settingsType)
                    // a type that is not built in may be a plugin type, which is bound once the plugins have loaded
                    json is ObjectNode -> UnresolvedStorageProviderSettings(type, json)
                    else -> it.treeToValue(json, null)
                }
            }

        /** The configured mapper's copy for a mapper, or the codec itself when it is not a mapper (an ObjectReader, for example). */
        private fun exactTreeReader(codec: ObjectCodec): ObjectCodec =
            if (codec is ObjectMapper) {
                exactTreeReaders.computeIfAbsent(codec) {
                    it.copy()
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
                }
            } else {
                codec
            }

    }

    class StorageProviderEnumResolver : EnumResolver {

        override fun resolve(scope: FieldScope): Collection<String>? =
            if (scope.name == "type")
                STORAGE_PROVIDERS.filterValues { it == scope.declaringType.erasedType }.keys
            else null

    }

    class StorageProviderSubtypeResolver : SubtypeResolver {

        override fun resolve(declaredType: ResolvedType, context: SchemaGenerationContext): List<ResolvedType>? =
            if (declaredType.erasedType == StorageProviderSettings::class.java)
                STORAGE_PROVIDERS.values.map { context.typeContext.resolveSubtype(declaredType, it) }
            else null

    }

}
