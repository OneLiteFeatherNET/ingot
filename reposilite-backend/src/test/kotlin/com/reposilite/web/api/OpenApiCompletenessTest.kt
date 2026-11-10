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

package com.reposilite.web.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Every route the backend registers must have an `@OpenApi` entry, or an entry on [EXCLUDED_ROUTES] or
 * [EXCLUDED_SOURCE_FILES] that says why it is not part of the REST API.
 *
 * The route list is read from the `ReposiliteRoute` declarations in `src/main/kotlin`. A route is served only when it
 * is declared as one, because `ReposiliteRoutes.routes(...)` accepts nothing else. The documented list is read from
 * the `@OpenApi` annotations, which the served `/openapi` document is generated from. The test reads sources only,
 * so it needs no server, no clock and no network, and it runs in milliseconds.
 *
 * Path parameters are compared in one notation: `<gav>`, `{gav}` and `<*>` are normalised before the comparison.
 */
internal class OpenApiCompletenessTest {

    @Test
    fun `finds the REST routes declared in the backend sources`() {
        val routes = declaredRoutes()

        // The scanner must not pass silently after a layout change: the backend declares about 90 routes.
        assertThat(routes.size)
            .describedAs("route declarations found under $SOURCE_ROOT; a smaller number means the scanner no longer reads them")
            .isGreaterThanOrEqualTo(MINIMUM_ROUTE_DECLARATIONS)
    }

    @Test
    fun `documents every registered REST route or lists it as an exclusion with a reason`() {
        val declared = declaredRoutes()
        val documented = documentedRoutes().map { it.key }.toSet()
        val considered = declared.filterNot { it.file in EXCLUDED_SOURCE_FILES }

        assertThat(considered.filter { it.path == null }.map { it.location })
            .describedAs(
                "routes whose path is built at runtime cannot be checked; use a literal path, or exclude the file in " +
                    "EXCLUDED_SOURCE_FILES of OpenApiCompletenessTest.kt with the reason"
            )
            .isEmpty()

        val missing = considered
            .filter { it.path != null }
            .filterNot { it.key in EXCLUDED_ROUTES }
            .filterNot { it.key in documented }

        assertThat(missing.map { "${it.key} (${it.location})" })
            .describedAs(
                "routes registered without an @OpenApi entry. Add the annotation to the endpoint, or add the route to " +
                    "EXCLUDED_ROUTES in OpenApiCompletenessTest.kt with the reason it is not part of the REST API"
            )
            .isEmpty()
    }

    @Test
    fun `every exclusion names a route or a file that is registered`() {
        val declared = declaredRoutes()
        val declaredKeys = declared.mapNotNull { it.key }.toSet()
        val staleRoutes = EXCLUDED_ROUTES.keys.filterNot { it in declaredKeys }
        val staleFiles = EXCLUDED_SOURCE_FILES.keys.filterNot { file -> declared.any { it.file == file } }

        assertThat(staleRoutes)
            .describedAs("EXCLUDED_ROUTES entries that match no registered route; remove them")
            .isEmpty()
        assertThat(staleFiles)
            .describedAs("EXCLUDED_SOURCE_FILES entries that declare no route; remove them")
            .isEmpty()
    }

    @Test
    fun `every exclusion states a reason`() {
        val blank = EXCLUDED_ROUTES.filterValues { it.isBlank() }.keys + EXCLUDED_SOURCE_FILES.filterValues { it.isBlank() }.keys

        assertThat(blank)
            .describedAs("exclusions without a reason")
            .isEmpty()
    }

    /** A declared route. [method] and [path] are null when the path is built at runtime and cannot be read. */
    private data class DeclaredRoute(val method: String?, val path: String?, val file: String, val location: String) {
        val key: String? get() = if (method == null || path == null) null else "$method $path"
    }

    private data class DocumentedRoute(val method: String, val path: String) {
        val key: String get() = "$method $path"
    }

    private fun sourceFiles(): List<Path> {
        val root = Path.of(SOURCE_ROOT)
        assertThat(Files.isDirectory(root))
            .describedAs("$SOURCE_ROOT must exist; the test runs from the backend module directory")
            .isTrue()
        return Files.walk(root).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.sorted().toList()
        }
    }

    private fun declaredRoutes(): List<DeclaredRoute> = sourceFiles().flatMap { file ->
        val text = file.readText()
        val fileName = file.fileName.toString()
        routeCalls.findAll(text).flatMap { match ->
            val args = argumentsWithin(text, match.range.last)
            val line = text.substring(0, match.range.first).count { it == '\n' } + 1
            val location = "$fileName:$line"
            val literal = pathLiteral.find(args)?.takeUnless { '$' in it.groupValues[1] }
            if (literal == null) {
                listOf(DeclaredRoute(method = null, path = null, file = fileName, location = location))
            } else {
                val methods = routeMethods(args.substring(literal.range.last + 1))
                check(methods.isNotEmpty()) { "no HTTP method found for the route at $location; the scanner needs an explicit method" }
                methods.map { method ->
                    DeclaredRoute(method = method, path = normalise(literal.groupValues[1]), file = fileName, location = location)
                }
            }
        }.toList()
    }

    private fun documentedRoutes(): List<DocumentedRoute> = sourceFiles().flatMap { file ->
        val text = file.readText()
        openApiCalls.findAll(text).flatMap { match ->
            val args = argumentsWithin(text, match.range.last)
            val path = annotationPath.find(args)?.groupValues?.get(1)
                ?: error("@OpenApi without a path in ${file.fileName}; every entry must name the path it documents")
            val methods = annotationMethods.findAll(args).map { it.groupValues[1].uppercase() }.toList()
            check(methods.isNotEmpty()) { "@OpenApi for $path in ${file.fileName} names no method" }
            methods.map { DocumentedRoute(method = it, path = normalise(path)) }
        }.toList()
    }.toList()

    /** Returns the text between the parenthesis at [open] and its matching close, skipping string literals. */
    private fun argumentsWithin(text: String, open: Int): String {
        var depth = 0
        var index = open
        var inString = false
        while (index < text.length) {
            val char = text[index]
            when {
                inString && char == '\\' -> index++
                inString && char == '"' -> inString = false
                inString -> Unit
                char == '"' -> inString = true
                char == '(' -> depth++
                char == ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, index)
                }
            }
            index++
        }
        error("unbalanced parentheses after offset $open")
    }

    /**
     * The methods named before the first named argument (`bodyLimit =` or `handler =`), so that a body limit never
     * adds a method and the handler's own text is never read.
     */
    private fun routeMethods(afterPath: String): List<String> {
        val methodPart = afterPath.substringBefore("handler =").substringBefore("bodyLimit =")
        return routeMethodToken.findAll(methodPart).map { it.groupValues[1] }.distinct().toList()
    }

    private fun normalise(path: String): String = pathParameter.replace(path) { "{${it.groupValues[1]}}" }

    private companion object {
        const val SOURCE_ROOT = "src/main/kotlin"
        const val MINIMUM_ROUTE_DECLARATIONS = 80

        val routeCalls = Regex("""(?<!class )ReposiliteRoute(?:<[^>\n]*>)?\(""")
        val pathLiteral = Regex("""^\s*"([^"]+)"""")
        val routeMethodToken = Regex("""\b(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE|CONNECT|BEFORE|AFTER)\b""")
        val openApiCalls = Regex("""@OpenApi\(""")
        val annotationPath = Regex("""\bpath\s*=\s*"([^"]+)"""")
        val annotationMethods = Regex("""HttpMethod\.(\w+)""")
        val pathParameter = Regex("""[<{]([^>}]+)[>}]""")

        /**
         * Routes that are registered but are not part of the REST API, keyed by method and normalised path.
         * An entry that matches no registered route fails the test, so the list cannot go stale.
         */
        val EXCLUDED_ROUTES: Map<String, String> = mapOf(
            "GET /javadoc/{repository}/{gav}" to
                "the javadoc viewer page, rendered as HTML for a browser and not meant for a client",
            "GET /javadoc/{repository}/{gav}/raw/{resource}" to
                "the raw files behind the javadoc viewer, loaded by the browser pages and not part of the API",
            "HEAD /{repository}/{gav}" to
                "the HEAD form of the Maven file read; it has no body and the GET entry describes the same resource",
            "BEFORE /{*}" to
                "a before-handler that logs endpoint access; it answers no request itself",
            "BEFORE /api/*" to
                "a before-handler that bypasses external caches for API paths; it answers no request itself",
            "AFTER /{repository}/{*}" to
                "an after-handler that adds the WWW-Authenticate challenge to 401 responses; it answers no request itself",
        )

        /**
         * Source files whose routes are not REST routes of the backend, keyed by file name. The dashboard handlers
         * serve the web UI, its index page and its assets, and their paths are built at runtime from the dashboard directory.
         */
        val EXCLUDED_SOURCE_FILES: Map<String, String> = mapOf(
            "FrontendHandler.kt" to
                "dashboard handlers: the web UI, its index page, its assets and the files of the dashboard directory",
            "RedirectPlugin.kt" to
                "routes built at runtime for the repositories a redirect points at, which serve Maven file reads and are not part of the REST API",
        )
    }
}
