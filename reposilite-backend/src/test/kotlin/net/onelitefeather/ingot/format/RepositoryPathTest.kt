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
@file:Suppress("FunctionName")

package net.onelitefeather.ingot.format

import io.javalin.http.HttpStatus.BAD_REQUEST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The canonical form of a raw repository request path: the one path both the authorization and the storage see. Each test
 * names one input and its expected canonical path or refusal.
 */
internal class RepositoryPathTest {

    private fun canonical(raw: String): String? =
        RepositoryPath.canonicalize(raw).orNull()?.authorizationPath

    private fun refusal(raw: String): Int? =
        RepositoryPath.canonicalize(raw).let { if (it.isErr) it.error.status else null }

    @Test
    fun `a plain path stays as it is`() {
        assertThat(canonical("/releases/com/acme/lib/1.0/lib-1.0.jar")).describedAs("plain path")
            .isEqualTo("/releases/com/acme/lib/1.0/lib-1.0.jar")
    }

    @Test
    fun `the repository and the location are split at the first segment`() {
        val path = RepositoryPath.canonicalize("/releases/com/acme/lib-1.0.jar").get()

        assertThat(path.repository).describedAs("repository").isEqualTo("releases")
        assertThat(path.location.toString()).describedAs("location").isEqualTo("com/acme/lib-1.0.jar")
    }

    @Test
    fun `an escaped character is decoded`() {
        assertThat(canonical("/rel%65ases/com/onelite%66eather/lib-1.0.jar")).describedAs("escaped letters")
            .isEqualTo("/releases/com/onelitefeather/lib-1.0.jar")
    }

    @Test
    fun `a doubly escaped character is decoded once only`() {
        assertThat(canonical("/releases/com/acme%2565/lib-1.0.jar")).describedAs("double escape")
            .isEqualTo("/releases/com/acme%65/lib-1.0.jar")
    }

    @Test
    fun `a plus sign stays a plus sign`() {
        assertThat(canonical("/releases/com/acme/lib/1.0+build/lib-1.0+build.jar")).describedAs("plus in a version")
            .isEqualTo("/releases/com/acme/lib/1.0+build/lib-1.0+build.jar")
    }

    @Test
    fun `doubled slashes collapse`() {
        assertThat(canonical("//releases//com///acme/lib-1.0.jar")).describedAs("doubled slashes")
            .isEqualTo("/releases/com/acme/lib-1.0.jar")
    }

    @Test
    fun `a trailing slash is trimmed`() {
        assertThat(canonical("/releases/com/acme/")).describedAs("trailing slash").isEqualTo("/releases/com/acme")
    }

    @Test
    fun `mixed case is kept`() {
        assertThat(canonical("/Releases/Com/Acme/Lib-1.0.jar")).describedAs("mixed case").isEqualTo("/Releases/Com/Acme/Lib-1.0.jar")
    }

    @Test
    fun `a malformed escape is refused`() {
        assertThat(refusal("/releases/com/acme%zz/lib-1.0.jar")).describedAs("%zz").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com/acme/lib-1.0.jar%")).describedAs("trailing %").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a parent segment is refused`() {
        assertThat(refusal("/releases/com/acme/../other/lib-1.0.jar")).describedAs("..").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `an escaped parent segment is refused`() {
        assertThat(refusal("/releases/com/acme/%2e%2e/other/lib-1.0.jar")).describedAs("%2e%2e").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com/acme/%2E./other/lib-1.0.jar")).describedAs("%2E.").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a current segment is refused`() {
        assertThat(refusal("/releases/com/./acme/lib-1.0.jar")).describedAs(".").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com/%2e/acme/lib-1.0.jar")).describedAs("%2e").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `an escaped slash inside a segment is refused`() {
        assertThat(refusal("/releases/com%2Facme/lib-1.0.jar")).describedAs("%2F").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases%2fcom/acme/lib-1.0.jar")).describedAs("%2f in the repository").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a backslash is refused`() {
        assertThat(refusal("/releases/com\\acme/lib-1.0.jar")).describedAs("backslash").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com%5Cacme/lib-1.0.jar")).describedAs("%5C").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a NUL or a control character is refused`() {
        assertThat(refusal("/releases/com/acme%00/lib-1.0.jar")).describedAs("%00").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com/acme%0A/lib-1.0.jar")).describedAs("%0A").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("/releases/com/acme\t/lib-1.0.jar")).describedAs("raw tab").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a path without a repository is refused`() {
        assertThat(refusal("/")).describedAs("root").isEqualTo(BAD_REQUEST.code)
        assertThat(refusal("")).describedAs("empty").isEqualTo(BAD_REQUEST.code)
    }

}
