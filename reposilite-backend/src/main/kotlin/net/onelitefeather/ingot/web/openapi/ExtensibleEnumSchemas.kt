/*
 * Copyright (c) 2026 OneLiteFeather
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

package net.onelitefeather.ingot.web.openapi

import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Adds `x-extensible-enum` to the component schemas of the registered [ExtensibleEnum] classes. The extension lists the
 * constant names in declaration order, so a value added to the enum shows up without changing this code. The closed
 * `enum` array the generator writes stays as it is.
 */
object ExtensibleEnumSchemas {

    const val EXTENSION = "x-extensible-enum"

    fun apply(document: ObjectNode, enums: Collection<Class<out Enum<*>>>): ObjectNode {
        val schemas = document.path("components").path("schemas") as? ObjectNode
        enums.forEach { enumClass ->
            require(enumClass.isAnnotationPresent(ExtensibleEnum::class.java)) {
                "${enumClass.name} must be annotated with @ExtensibleEnum to be declared extensible"
            }
            val schema = schemas?.get(enumClass.simpleName) as? ObjectNode
                ?: throw IllegalStateException(
                    "No schema ${enumClass.simpleName} in the OpenAPI document, but it is declared as an extensible enum"
                )
            val constants = enumClass.enumConstants?.map { it.name }
                ?: throw IllegalArgumentException("${enumClass.name} is not an enum")
            val extension = schema.putArray(EXTENSION)
            constants.forEach { extension.add(it) }
        }
        return document
    }
}
