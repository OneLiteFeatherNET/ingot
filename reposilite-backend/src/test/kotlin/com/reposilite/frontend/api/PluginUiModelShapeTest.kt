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
package com.reposilite.frontend.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The constructors of the blocks that plugins compiled against earlier versions call. A later edit that changes one
 * of them breaks those plugins at run time, so the parameter types are fixed here.
 */
internal class PluginUiModelShapeTest {

    private fun parameterTypesOf(block: Class<*>): List<String> =
        block.declaredConstructors
            .filterNot { it.isSynthetic }
            .single()
            .parameterTypes
            .map { it.simpleName }

    @Test
    fun `should keep the constructor of the text block`() {
        assertThat(parameterTypesOf(PluginUiBlock.Text::class.java)).describedAs("parameters").containsExactly("String")
    }

    @Test
    fun `should keep the constructor of the key value block`() {
        assertThat(parameterTypesOf(PluginUiBlock.KeyValue::class.java)).describedAs("parameters").containsExactly("String")
    }

    @Test
    fun `should keep the constructor of the table block`() {
        assertThat(parameterTypesOf(PluginUiBlock.Table::class.java)).describedAs("parameters").containsExactly("String", "List")
    }

    @Test
    fun `should keep the constructor of the form block`() {
        assertThat(parameterTypesOf(PluginUiBlock.Form::class.java))
            .describedAs("parameters")
            .containsExactly("String", "String", "String", "Map", "Map")
    }

    @Test
    fun `should keep the constructor of the action block`() {
        assertThat(parameterTypesOf(PluginUiBlock.Action::class.java))
            .describedAs("parameters")
            .containsExactly("String", "String", "String", "String", "boolean")
    }

    @Test
    fun `should keep the constructor of the status block`() {
        assertThat(parameterTypesOf(PluginUiBlock.Status::class.java)).describedAs("parameters").containsExactly("String", "int")
    }
}
