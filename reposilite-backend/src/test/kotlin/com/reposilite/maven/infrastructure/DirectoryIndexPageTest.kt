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

package com.reposilite.maven.infrastructure

import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.SimpleDirectoryInfo
import io.javalin.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DirectoryIndexPageTest {

    @Test
    fun `should escape a file name that carries markup`() {
        // given: a file name that tries to leave the anchor
        val fileName = "\"><img src=x onerror=alert(1)>.jar"

        // when: the directory index is rendered
        val page = createDirectoryIndexPage(
            basePath = "/",
            uri = "/releases/com/example/",
            authenticatedFiles = listOf(DocumentInfo(name = fileName, contentType = ContentType.TEXT_PLAIN)),
        )

        // then: the name is encoded in the href and escaped as text
        assertThat(page)
            .`as`("the raw tag must not reach the markup")
            .doesNotContain("<img src=x")
            .`as`("href must carry the percent-encoded name")
            .contains("href=\"./%22%3E%3Cimg%20src=x%20onerror=alert(1)%3E.jar\"")
            .`as`("link text must carry the escaped name")
            .contains(">&quot;&gt;&lt;img src=x onerror=alert(1)&gt;.jar</a>")
    }

    @Test
    fun `should escape the request path in the title and the heading`() {
        // when: the requested directory contains quotes, ampersands and tags
        val page = createDirectoryIndexPage(
            basePath = "/",
            uri = "/releases/o'brien&co<x>/",
            authenticatedFiles = emptyList(),
        )

        // then: the title and the heading show the escaped path
        assertThat(page)
            .`as`("title must be escaped")
            .contains("<title>Index of /releases/o&#39;brien&amp;co&lt;x&gt;/</title>")
            .`as`("heading must be escaped")
            .contains("<h1>Index of /releases/o&#39;brien&amp;co&lt;x&gt;/</h1>")
    }

    @Test
    fun `should not let the request path break out of the base href`() {
        // when: the requested directory carries a markup injection
        val page = createDirectoryIndexPage(
            basePath = "/",
            uri = "/releases/\"><script>alert(1)</script>/",
            authenticatedFiles = emptyList(),
        )

        // then: the base href keeps the injection percent-encoded
        assertThat(page)
            .`as`("base href must be percent-encoded")
            .contains("<base href='/releases/%22%3E%3Cscript%3Ealert(1)%3C/script%3E/'>")
            .`as`("the raw injection must not reach the markup")
            .doesNotContain("\"><script>")
            .doesNotContain("<script>alert(1)")
    }

    @Test
    fun `should keep a regular directory listing readable`() {
        // when: a regular Maven directory is listed
        val page = createDirectoryIndexPage(
            basePath = "/",
            uri = "/releases/com/example/",
            authenticatedFiles = listOf(
                SimpleDirectoryInfo(name = "1.0"),
                DocumentInfo(name = "example-1.0.pom", contentType = ContentType.TEXT_XML),
            ),
        )

        // then: the base, the parent and the entries keep their plain form
        assertThat(page)
            .`as`("base href stays the directory")
            .contains("<base href='/releases/com/example/'>")
            .`as`("parent link stays the parent directory")
            .contains("<a href='/releases/com'>Parent Directory</a>")
            .`as`("directory entry keeps its trailing slash")
            .contains("<a href=\"./1.0/\">1.0/</a>")
            .`as`("file entry keeps its plain name")
            .contains("<a href=\"./example-1.0.pom\">example-1.0.pom</a>")
    }
}
