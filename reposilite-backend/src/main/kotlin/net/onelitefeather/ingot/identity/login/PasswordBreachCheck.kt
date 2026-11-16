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

package net.onelitefeather.ingot.identity.login

import net.onelitefeather.ingot.identity.login.BreachCheckMode.OFF
import net.onelitefeather.ingot.identity.login.BreachCheckMode.OFFLINE
import net.onelitefeather.ingot.identity.login.BreachCheckMode.RANGE
import panda.std.reactive.Reference
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration

/** The time to connect to the range service and the time to answer, three seconds each. */
private val RANGE_TIMEOUT: Duration = Duration.ofSeconds(3)

/** A range prefix: the first five characters of an upper case SHA-1 in hexadecimal. */
private val PREFIX = Regex("[0-9A-F]{5}")

/** The answer of the breached-password check for one new password. */
internal sealed interface BreachVerdict {

    /** The password is not listed, or no check runs. */
    data object Clear : BreachVerdict

    /** The password is listed as breached. */
    data object Breached : BreachVerdict

    /** The check gave no answer. [failClosed] says whether the change is refused then. */
    data class Unavailable(val failClosed: Boolean) : BreachVerdict

}

/** Fetches the suffixes that a range service lists under one five character SHA-1 prefix. */
internal fun interface BreachRangeClient {

    /** The lines `SUFFIX` or `SUFFIX:COUNT` of [prefix]. Throws [BreachRangeUnavailable] when no answer comes. */
    fun suffixes(rangeUrl: String, prefix: String): List<String>

}

internal class BreachRangeUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The production range client: an HTTPS GET of the prefix with padding, three seconds to connect and three seconds to
 * answer. It sends no user name, address, cookie or authorization.
 */
internal class JdkBreachRangeClient(
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(RANGE_TIMEOUT).build(),
) : BreachRangeClient {

    override fun suffixes(rangeUrl: String, prefix: String): List<String> {
        val response = try {
            http.send(rangeRequest(rangeUrl, prefix), HttpResponse.BodyHandlers.ofString())
        } catch (failure: IOException) {
            throw BreachRangeUnavailable("The breached password service did not answer", failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BreachRangeUnavailable("The breached password service did not answer", failure)
        }
        return when (response.statusCode()) {
            200 -> response.body().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            // The service answers 404 for a prefix that no breached password has.
            404 -> emptyList()
            else -> throw BreachRangeUnavailable("The breached password service answered ${response.statusCode()}")
        }
    }

}

/** The request for one prefix. Its path is the prefix only, so that nothing else of the password leaves the instance. */
internal fun rangeRequest(rangeUrl: String, prefix: String): HttpRequest {
    require(prefix.matches(PREFIX)) { "A range prefix is five hexadecimal characters" }
    return HttpRequest.newBuilder(URI.create(rangeUrl.trimEnd('/') + "/" + prefix))
        .timeout(RANGE_TIMEOUT)
        .header("Add-Padding", "true")
        .GET()
        .build()
}

/**
 * The breached-password check of a new password, see [BreachCheckMode]. The settings are read on every check, so a mode
 * changed at runtime applies to the next password. The offline list is loaded by [preload] at start; a file that cannot
 * be read then stops the start, and a later failure is an unavailable check.
 */
internal class PasswordBreachCheck(
    private val settings: Reference<LoginSettings>,
    private val range: BreachRangeClient,
) {

    /** The offline list that is loaded, with the path it came from. Guarded by `this`. */
    private var offline: Pair<Path, OfflineBreachList>? = null

    /** Loads the offline list when that mode is set. Throws [IllegalStateException] naming the key when it cannot be loaded. */
    fun preload() {
        val policy = settings.get().passwordPolicy.breachCheck
        if (policy.mode == OFFLINE) {
            offlineList(policy.file)
        }
    }

    fun verdictOf(password: String): BreachVerdict {
        val policy = settings.get().passwordPolicy.breachCheck
        return when (policy.mode) {
            OFF -> BreachVerdict.Clear
            RANGE -> rangeVerdict(policy, password)
            OFFLINE -> offlineVerdict(policy, password)
        }
    }

    private fun rangeVerdict(policy: BreachCheckSettings, password: String): BreachVerdict {
        val hash = sha1Hex(password)
        val listed = try {
            range.suffixes(policy.rangeUrl, hash.take(5))
        } catch (failure: BreachRangeUnavailable) {
            return BreachVerdict.Unavailable(failClosed = policy.failClosed)
        }
        val suffix = hash.drop(5)
        return if (listed.any { it.substringBefore(':').equals(suffix, ignoreCase = true) }) BreachVerdict.Breached else BreachVerdict.Clear
    }

    private fun offlineVerdict(policy: BreachCheckSettings, password: String): BreachVerdict {
        val list = try {
            offlineList(policy.file)
        } catch (failure: IllegalStateException) {
            // The mode must not fall back to off: a list that cannot be read refuses the change.
            return BreachVerdict.Unavailable(failClosed = true)
        }
        return if (list.contains(sha1Hex(password))) BreachVerdict.Breached else BreachVerdict.Clear
    }

    @Synchronized
    private fun offlineList(file: String): OfflineBreachList {
        val path = Paths.get(file)
        offline?.takeIf { it.first == path }?.let { return it.second }
        val loaded = OfflineBreachList.load(file)
        offline = path to loaded
        return loaded
    }

    companion object {

        /** The SHA-1 of [password] as 40 upper case hexadecimal characters, the form the range service and the lists use. */
        fun sha1Hex(password: String): String =
            MessageDigest.getInstance("SHA-1")
                .digest(password.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02X".format(it) }

        /** The settings key of the offline file, named in every failure that concerns it. */
        internal const val FILE_KEY = "login.passwordPolicy.breachCheck.file"

    }

}

/** The SHA-1 hashes of an offline list, sorted in memory. The whole list is loaded, so it must fit in the heap. */
internal class OfflineBreachList private constructor(private val sortedHashes: Array<String>) {

    fun contains(sha1: String): Boolean =
        sortedHashes.binarySearch(sha1) >= 0

    companion object {

        /** Reads [file], one hash per line with an optional `:COUNT`. Throws [IllegalStateException] naming the key. */
        fun load(file: String): OfflineBreachList {
            val key = PasswordBreachCheck.FILE_KEY
            if (file.isBlank()) {
                throw IllegalStateException("$key is not set, and the breached password mode OFFLINE reads that file")
            }
            val lines = try {
                Files.readAllLines(Paths.get(file))
            } catch (failure: IOException) {
                throw IllegalStateException("$key: the file $file cannot be read", failure)
            }
            val hashes = lines.map { it.substringBefore(':').trim().uppercase() }.filter { it.isNotEmpty() }
            hashes.firstOrNull { !it.matches(SHA1) }?.let {
                throw IllegalStateException("$key: the file $file has a line that is not a SHA-1 hash in hexadecimal")
            }
            return OfflineBreachList(hashes.distinct().sorted().toTypedArray())
        }

        private val SHA1 = Regex("[0-9A-F]{40}")

    }

}
