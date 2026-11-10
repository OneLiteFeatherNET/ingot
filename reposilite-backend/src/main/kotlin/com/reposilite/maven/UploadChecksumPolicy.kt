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

import com.fasterxml.jackson.annotation.JsonProperty
import com.reposilite.maven.api.Checksum
import panda.std.Result

/**
 * What a repository does with an uploaded checksum file that does not match the artifact stored before it.
 * - [IGNORE] stores the checksum file without a check, as uploads always did.
 * - [WARN] stores the checksum file and records the mismatch in the audit log.
 * - [REJECT] refuses the checksum file with `400`, stores nothing and records the refusal.
 */
enum class UploadChecksumPolicy {
    @JsonProperty("ignore") IGNORE,
    @JsonProperty("warn") WARN,
    @JsonProperty("reject") REJECT,
}

/** The largest body of a checksum file that is read as a digest. A longer body is never a digest. */
internal const val UPLOAD_CHECKSUM_MAX_BYTES = 1024

/** The instance default, read by `INGOT_UPLOAD_CHECKSUM_POLICY` first and `REPOSILITE_UPLOAD_CHECKSUM_POLICY` second. */
internal const val UPLOAD_CHECKSUM_POLICY_VARIABLE = "INGOT_UPLOAD_CHECKSUM_POLICY"
internal const val UPLOAD_CHECKSUM_POLICY_FALLBACK_VARIABLE = "REPOSILITE_UPLOAD_CHECKSUM_POLICY"
internal const val UPLOAD_CHECKSUM_POLICY_PROPERTY = "ingot.uploadChecksumPolicy"

/**
 * The policy that applies to a repository. A [repositoryValue] set on the repository wins. Otherwise the first of the
 * variable [UPLOAD_CHECKSUM_POLICY_VARIABLE], the fallback variable and the system property that is set decides, and
 * [UploadChecksumPolicy.IGNORE] applies when none is set. A value that is not a policy is refused with a message that
 * names where it came from.
 */
internal fun resolveUploadChecksumPolicy(
    repositoryValue: UploadChecksumPolicy?,
    environment: Map<String, String>,
    systemProperty: String?,
): Result<UploadChecksumPolicy, String> {
    if (repositoryValue != null) {
        return Result.ok(repositoryValue)
    }

    val sources = listOf(
        UPLOAD_CHECKSUM_POLICY_VARIABLE to environment[UPLOAD_CHECKSUM_POLICY_VARIABLE],
        UPLOAD_CHECKSUM_POLICY_FALLBACK_VARIABLE to environment[UPLOAD_CHECKSUM_POLICY_FALLBACK_VARIABLE],
        UPLOAD_CHECKSUM_POLICY_PROPERTY to systemProperty,
    )
    val (source, value) = sources.firstOrNull { !it.second.isNullOrBlank() }
        ?: return Result.ok(UploadChecksumPolicy.IGNORE)

    val raw = value.orEmpty()
    val policy = parseUploadChecksumPolicy(raw.trim())
        ?: return Result.error("$source must be one of ignore, warn or reject, but is '$raw'")

    return Result.ok(policy)
}

/**
 * The instance default of this process, read from the environment and the system property. Called once at startup, so a
 * value that is not a policy stops the start with a message that names the setting.
 */
internal fun readInstanceUploadChecksumPolicy(
    environment: Map<String, String> = System.getenv(),
    systemProperty: String? = System.getProperty(UPLOAD_CHECKSUM_POLICY_PROPERTY),
): UploadChecksumPolicy =
    resolveUploadChecksumPolicy(repositoryValue = null, environment = environment, systemProperty = systemProperty).let { result ->
        if (result.isErr) throw IllegalStateException(result.error) else result.get()
    }

private fun parseUploadChecksumPolicy(value: String): UploadChecksumPolicy? =
    UploadChecksumPolicy.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

/** The checksum algorithm of a file whose name ends in its suffix, such as `lib-1.0.0.jar.sha1`, or null for any other file. */
internal fun checksumOfFileName(fileName: String): Checksum? =
    Checksum.entries.firstOrNull { fileName.endsWith("." + it.extension) }

/** The number of hex characters of a digest of this algorithm. */
internal val Checksum.hexLength: Int
    get() = when (this) {
        Checksum.MD5 -> 32
        Checksum.SHA1 -> 40
        Checksum.SHA256 -> 64
        Checksum.SHA512 -> 128
    }

private val WHITESPACE = Regex("\\s+")

/**
 * The digest in the body of a checksum file: the first word of its content, in lower case. The content may be the bare
 * digest or a digest followed by a file name, as the `sha1sum` tool writes it. Null when the body is longer than
 * [UPLOAD_CHECKSUM_MAX_BYTES] or its first word is not a hex digest of the length of [checksum].
 */
internal fun parseUploadedDigest(body: ByteArray, checksum: Checksum): String? {
    if (body.size > UPLOAD_CHECKSUM_MAX_BYTES) {
        return null
    }

    val word = String(body, Charsets.UTF_8).trim().split(WHITESPACE).firstOrNull()?.lowercase() ?: return null
    val isHex = word.all { character -> character in '0'..'9' || character in 'a'..'f' }
    return word.takeIf { it.length == checksum.hexLength && isHex }
}
