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

package com.reposilite.shared.extensions

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.reposilite.shared.ErrorResponse
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HandlerType.HEAD
import io.javalin.http.HandlerType.OPTIONS
import io.javalin.http.Header
import io.javalin.http.Header.CACHE_CONTROL
import io.javalin.http.Header.LAST_MODIFIED
import io.javalin.http.HttpStatus
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import net.onelitefeather.ingot.web.SecurityHeaders
import com.reposilite.shared.http.CacheScope
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.hours
import org.eclipse.jetty.ee10.servlet.HttpOutput
import panda.std.Result
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class ContentTypeSerializer : StdSerializer<ContentType> {

    constructor() : this(null)
    constructor(type: Class<ContentType>?) : super(type)

    override fun serialize(value: ContentType, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.mimeType)
    }

}

object EmptyBody

data class HtmlResponse(val content: String)

fun Context.response(result: Any): Context =
    also {
        when (result) {
            is EmptyBody, Unit -> return@also
            is Context -> return@also
            is Result<*, *> -> {
                result.consume(
                    { value -> response(value) },
                    { error -> response(error) }
                )
                return@also
            }
        }

        if (!acceptsBody() || !outputStream().isProbablyOpen()) {
            if (result is InputStream) {
                result.silentClose()
            }

            return@also
        }

        clearContentLength()

        when (result) {
            is ErrorResponse -> error(result)
            is HtmlResponse -> html(result.content)
            is InputStream -> result(result)
            is String -> result(result)
            else -> json(result)
        }
    }

internal val maxAge = System.getProperty("reposilite.maven.maxAge", 1.hours.inWholeSeconds.toString()).toLong()

internal fun Context.resultAttachment(
    name: String,
    contentType: ContentType,
    contentLength: Long,
    lastTimeModified: Instant?,
    compressionStrategy: String,
    cache: Boolean,
    cacheScope: CacheScope = CacheScope.PUBLIC,
    data: InputStream
) {
    attachmentHeaders(
        name = name,
        contentType = contentType,
        contentLength = contentLength,
        lastTimeModified = lastTimeModified,
        compressionStrategy = compressionStrategy,
        cache = cache,
        cacheScope = cacheScope,
    )

    when {
        acceptsBody() -> result(data)
        else -> data.silentClose()
    }
}

/** The Cache-Control value of an answer that no cache may store. */
internal const val NO_STORE_CACHE_CONTROL = "no-cache, no-store, max-age=0"

internal fun Context.attachmentHeaders(
    name: String,
    contentType: ContentType,
    contentLength: Long,
    lastTimeModified: Instant?,
    compressionStrategy: String,
    cache: Boolean,
    cacheScope: CacheScope = CacheScope.PUBLIC,
) {
    SecurityHeaders.markRepositoryContent(this)

    if (!contentType.isHumanReadable) {
        contentDisposition("""attachment; filename="$name"; filename*=utf-8''${URLEncoder.encode(name, "utf-8")}""")
    }

    if (compressionStrategy == "none" && contentLength > 0) {
        contentLength(contentLength) // Using this with GZIP ends up with "Premature end of Content-Length delimited message body".
    }

    if (lastTimeModified != null) {
        lastModified(lastTimeModified)
    }

    when {
        !cache -> header(CACHE_CONTROL, NO_STORE_CACHE_CONTROL)
        cacheScope == CacheScope.PRIVATE -> header(CACHE_CONTROL, "private, max-age=$maxAge")
        else -> header(CACHE_CONTROL, "public, max-age=$maxAge")
    }

    contentType(contentType)
}

fun Context.acceptsBody(): Boolean =
    method() != HEAD && method() != OPTIONS

fun Context.clearContentLength(): Context =
    also { contentLength(-1) }

fun Context.contentLength(length: Long): Context =
    also { res().setContentLengthLong(length) }

fun Context.encoding(encoding: Charset): Context =
    encoding(encoding.name())

fun Context.encoding(encoding: String): Context =
    also { res().characterEncoding = encoding }

fun Context.contentDisposition(disposition: String): Context =
    header("Content-Disposition", disposition)

private val gmtZone = ZoneId.of("GMT")

fun Context.lastModified(time: Instant): Context =
    header(LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(time.atZone(gmtZone)))

fun Context.uri(): String =
    path()

const val PROBLEM_JSON = "application/problem+json"

fun Context.error(error: ErrorResponse): Context =
    error(error.status, error)

/**
 * True when the client names `application/problem+json` and ranks it at least as high as `application/json`.
 * Wildcards and a missing header keep the answer as `application/json`, as it has always been.
 */
internal fun prefersProblemJson(accept: String?): Boolean {
    if (accept.isNullOrBlank()) {
        return false
    }
    val quality = accept.split(',')
        .map { it.split(';').map(String::trim) }
        .associate { parts ->
            val q = parts.drop(1).firstOrNull { it.startsWith("q=", ignoreCase = true) }
                ?.substring(2)?.toDoubleOrNull() ?: 1.0
            parts[0].lowercase() to q
        }
    val problem = quality[PROBLEM_JSON] ?: return false
    return problem > 0.0 && problem >= (quality["application/json"] ?: 0.0)
}

fun Context.error(status: HttpStatus, error: Any): Context =
    error(status.code, error)

/**
 * Builds a Vary value from the earlier values and the names that the answer depends on. The earlier names keep their
 * order, and each name is appended unless it is already listed in any letter case. A wildcard already covers every
 * request header, so it is returned unchanged.
 */
internal fun mergeVary(existing: Collection<String>, names: Collection<String>): String {
    val merged = existing.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    if ("*" in merged) return "*"

    names
        .filterNot { name -> merged.any { it.equals(name, ignoreCase = true) } }
        .forEach { merged += it }
    return merged.joinToString(separator = ", ")
}

fun Context.error(status: Int, error: Any): Context =
    when (error) {
        is ErrorResponse -> {
            error.instance = error.instance ?: path()
            status(status).json(error)
            if (prefersProblemJson(header(Header.ACCEPT))) {
                contentType(PROBLEM_JSON)
            }
            // The content type depends on Accept, with or without problem details, so every negotiated answer varies on it
            header(Header.VARY, mergeVary(res().getHeaders(Header.VARY), listOf(Header.ACCEPT)))
            this
        }
        else -> status(status).json(error)
    }

fun OutputStream.isProbablyOpen(): Boolean =
    when (this) {
        is HttpOutput -> !isClosed
        else -> true
    }

fun Closeable?.silentClose() =
    runCatching {
        this?.close()
    }
