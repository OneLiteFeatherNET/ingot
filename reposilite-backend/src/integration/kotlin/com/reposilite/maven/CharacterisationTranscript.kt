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

package com.reposilite.maven

import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import java.io.File

/**
 * The transcript of a characterisation test: requests are sent, every response is rendered with its status, its headers and
 * its body, and the rendered text is compared with a fixture recorded on main. The fixtures are only re-recorded on purpose:
 * when the environment variable [RECORD_ENVIRONMENT] names a directory, the transcript is written there instead.
 *
 * Volatile values are normalised: the Date and Last-Modified headers, the value of the ETag (derived from the modification
 * time of the stored file, so it changes on every run) and the port of the instance. The ETag keeps its presence and quotes.
 */
internal const val CHARACTERISATION_RECORD_ENVIRONMENT = "INGOT_CHARACTERISATION_RECORD"

/**
 * Date and Last-Modified change on every run. Connection: close is sent by Jetty when it closes the socket after answering a
 * request whose body it has not read, such as an upload refused with 401. Whether that happens depends on the timing of the
 * client and the server, not on the route: the same request was answered with and without the header on the same code, so the
 * header is left out instead of failing the comparison. It appeared once in a CI run and in none of six back-to-back runs, on the
 * branch and on main alike, so it is not a route difference.
 */
private val VOLATILE_HEADERS = setOf("date", "last-modified", "connection")

/**
 * The file name of a frontend bundle. The frontend build names its bundles after their chunks and adds a content hash, and
 * both change whenever the frontend changes, so the whole file name is replaced by a placeholder. The path and the extension
 * stay, so the transcript still shows which kind of asset is served from where.
 */
private val BUNDLE_NAME = Regex("(/assets/)[^\\s\"'<>]+?\\.(js|css)(?=[\\s\"'<>])")

/**
 * One request of a transcript. Authenticated probes send the default management token. A probe with an [anchor] is a page
 * whose body is too volatile to record: the transcript keeps only whether the body contains the anchor, and the
 * content-length header, which follows the size of that body, is left out.
 */
internal class CharacterisationProbe(
    val title: String,
    val method: String,
    val path: String,
    val body: String? = null,
    val auth: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val anchor: String? = null,
) {

    fun send(base: String, name: String, secret: String): CharacterisationExchange {
        val url = "$base/$path"
        val request = when (method) {
            "GET" -> Unirest.get(url)
            "HEAD" -> Unirest.head(url)
            "DELETE" -> Unirest.delete(url)
            "PUT" -> Unirest.put(url).body(body.orEmpty())
            "POST" -> Unirest.post(url).body(body.orEmpty())
            else -> error("Unsupported method $method")
        }
        headers.forEach { (key, value) -> request.header(key, value) }
        if (auth) request.basicAuth(name, secret)
        return CharacterisationExchange(this, request.asString())
    }

}

internal class CharacterisationExchange(val probe: CharacterisationProbe, val response: HttpResponse<String>) {

    fun render(): String = buildString {
        appendLine("== ${probe.title}")
        appendLine("${probe.method} ${probe.path}${if (probe.auth) " (authenticated)" else ""}")
        appendLine("status: ${response.status}")
        response.headers.all()
            .filterNot { it.name.lowercase() in VOLATILE_HEADERS }
            .filterNot { probe.anchor != null && it.name.equals("content-length", ignoreCase = true) }
            .map { "header: ${it.name.lowercase()}: ${normalise(it.name, it.value)}" }
            .sorted()
            .forEach { appendLine(it) }
        appendLine("body:")
        val anchor = probe.anchor
        if (anchor != null) {
            appendLine("contains ${anchor}: ${response.body.orEmpty().contains(anchor)}")
        } else {
            appendLine(response.body.orEmpty().replace(Regex("localhost:\\d+"), "localhost:<port>").replace(BUNDLE_NAME, "$1<asset>.$2"))
        }
    }

}

private fun normalise(name: String, value: String): String =
    if (name.equals("ETag", ignoreCase = true)) "\"<etag>\"" else value

/** Renders the exchanges of [probes] in order, one block per request. */
internal fun renderTranscript(base: String, name: String, secret: String, probes: List<CharacterisationProbe>): String =
    probes.joinToString("\n") { probe -> probe.send(base, name, secret).render() }

/**
 * Compares [actual] with the fixture resource [fixture], or writes [actual] into the record directory when one is named by
 * [CHARACTERISATION_RECORD_ENVIRONMENT].
 */
internal fun compareWithFixture(fixture: String, actual: String) {
    val recordDirectory = System.getenv(CHARACTERISATION_RECORD_ENVIRONMENT)
    if (!recordDirectory.isNullOrBlank()) {
        File(recordDirectory).mkdirs()
        File(recordDirectory, fixture.substringAfterLast('/')).writeText(actual)
        return
    }
    val expected = CharacterisationExchange::class.java.getResourceAsStream(fixture)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: error("The fixture $fixture is missing from the test resources")
    assertThat(actual)
        .describedAs("the responses must stay byte for byte the same as recorded on main, fixture $fixture")
        .isEqualTo(expected)
}
