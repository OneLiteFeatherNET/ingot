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
package com.reposilite.frontend.infrastructure

import io.javalin.http.HandlerType.DELETE
import io.javalin.http.HandlerType.GET
import io.javalin.http.HandlerType.HEAD
import io.javalin.http.HandlerType.POST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginUiReaderAccessTest {

    private val sources = setOf("/api/plugins/example/readers", "/api/plugins/example/releases/list")

    @Test
    fun `should exempt a GET of a declared source`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example/readers", sources)).describedAs("exempt").isTrue()
    }

    @Test
    fun `should exempt a HEAD of a declared source`() {
        assertThat(PluginUiReaderAccess.isExemptRead(HEAD, "/api/plugins/example/readers", sources)).describedAs("exempt").isTrue()
    }

    @Test
    fun `should not exempt a POST to a declared source`() {
        assertThat(PluginUiReaderAccess.isExemptRead(POST, "/api/plugins/example/readers", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a DELETE to a declared source`() {
        assertThat(PluginUiReaderAccess.isExemptRead(DELETE, "/api/plugins/example/readers", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a path that only extends a declared source`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example/readers/extra", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a path with a dot-dot segment`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example/releases/../readers", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a path with an empty segment`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example//readers", sources)).describedAs("double slash").isFalse()
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example/readers/", sources)).describedAs("trailing slash").isFalse()
    }

    @Test
    fun `should not exempt a percent-encoded path`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example/%2e%2e/readers", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a path of another plugin that has the same tail`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/other/readers", sources)).describedAs("exempt").isFalse()
    }

    @Test
    fun `should not exempt a look-alike plugin name that starts with the name of a declaring plugin`() {
        assertThat(PluginUiReaderAccess.isExemptRead(GET, "/api/plugins/example-other/readers", sources)).describedAs("exempt").isFalse()
    }
}
