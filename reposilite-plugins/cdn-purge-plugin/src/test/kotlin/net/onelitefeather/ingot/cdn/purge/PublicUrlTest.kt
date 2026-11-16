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

package net.onelitefeather.ingot.cdn.purge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PublicUrlTest {

    @Test
    fun `should join the base url, the repository and the path`() {
        assertThat(publicUrl("https://repo.example.com", "releases", "com/example/app/1.0/app-1.0.jar"))
            .describedAs("public url of a deployed jar")
            .isEqualTo("https://repo.example.com/releases/com/example/app/1.0/app-1.0.jar")
    }

    @Test
    fun `should not double the slash between the base url and the repository`() {
        assertThat(publicUrl("https://repo.example.com/", "releases", "/com/example/app.jar"))
            .describedAs("public url with a trailing slash in the base and a leading slash in the path")
            .isEqualTo("https://repo.example.com/releases/com/example/app.jar")
    }

    @Test
    fun `should percent-encode each path segment`() {
        assertThat(publicUrl("https://repo.example.com", "releases", "com/example/my file#1.jar"))
            .describedAs("public url with a space and a hash in the file name")
            .isEqualTo("https://repo.example.com/releases/com/example/my%20file%231.jar")
    }

}
