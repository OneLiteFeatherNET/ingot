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

package net.onelitefeather.ingot.search

import com.reposilite.maven.isMetadataOrChecksumFileName

/** The outcome of parsing one stored path (artifact search spec, section 2.2). */
sealed interface ArtifactPathResult {

    /** A searchable file with its coordinates. */
    data class Row(val file: ArtifactFileRecord) : ArtifactPathResult

    /** Not part of the index by design. Not counted. */
    data class Ignored(val reason: IgnoreReason) : ArtifactPathResult

    /** Looks like an artifact path, but the file name does not match the coordinates. Counted as unparsed. */
    data class Unparsed(val reason: UnparsedReason) : ArtifactPathResult

    /** A value exceeds the limit of [column]. The file is skipped and counted, never truncated. */
    data class TooLong(val column: SearchColumn) : ArtifactPathResult
}

enum class IgnoreReason {
    /** Fewer than four segments: no group, artifact, version and file. */
    TOO_SHORT,

    /** A `maven-metadata*` file or a checksum sidecar. */
    METADATA_OR_CHECKSUM,

    /** The file name has no extension after the coordinates. */
    NO_EXTENSION,
}

enum class UnparsedReason {
    /** The file name does not start with `<artifactId>-<version>`. */
    NAME_PREFIX,

    /** The remainder after the coordinates has an empty classifier or cannot be split. */
    MALFORMED_NAME,

    /** The path has an empty segment, for example a leading slash. */
    MALFORMED_PATH,
}

/**
 * Derives Maven coordinates from a path inside a repository. The directory structure is
 * authoritative; the file name is only checked against it. Pure: no IO, no clock.
 */
object ArtifactPathParser {

    private const val MIN_SEGMENTS = 4
    private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
    private const val ASC = ".asc"
    private val COMPOUND_EXTENSIONS = listOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")
    private val TIMESTAMP_AT_START = Regex("""^\d{8}\.\d{6}-\d+""")

    /**
     * @param repository the repository name, without any path
     * @param path the path inside the repository, without a leading slash
     */
    fun parse(repository: String, path: String): ArtifactPathResult {
        if (repository.length > SearchColumn.REPOSITORY.limit) {
            return ArtifactPathResult.TooLong(SearchColumn.REPOSITORY)
        }

        val segments = path.split('/')
        if (segments.size < MIN_SEGMENTS) return ArtifactPathResult.Ignored(IgnoreReason.TOO_SHORT)
        if (segments.any { it.isEmpty() }) return ArtifactPathResult.Unparsed(UnparsedReason.MALFORMED_PATH)

        val fileName = segments.last()
        if (fileName.isMetadataOrChecksumFileName()) {
            return ArtifactPathResult.Ignored(IgnoreReason.METADATA_OR_CHECKSUM)
        }

        val version = segments[segments.size - 2]
        val artifactId = segments[segments.size - 3]
        val groupId = segments.subList(0, segments.size - 3).joinToString(".")
        val snapshot = version.endsWith(SNAPSHOT_SUFFIX)

        val remainder = remainderAfterPrefix(fileName, artifactId, version, snapshot)
            ?: return ArtifactPathResult.Unparsed(UnparsedReason.NAME_PREFIX)

        val (classifier, extension) = when {
            remainder.startsWith('.') -> null to remainder.substring(1)
            remainder.startsWith('-') -> splitClassifier(remainder.substring(1))
                ?: return ArtifactPathResult.Ignored(IgnoreReason.NO_EXTENSION)
            else -> return ArtifactPathResult.Ignored(IgnoreReason.NO_EXTENSION)
        }
        if (classifier != null && classifier.isEmpty()) {
            return ArtifactPathResult.Unparsed(UnparsedReason.MALFORMED_NAME)
        }
        if (extension.isEmpty()) return ArtifactPathResult.Ignored(IgnoreReason.NO_EXTENSION)

        val record = ArtifactFileRecord(
            repository = repository,
            path = path,
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
            extension = extension,
            snapshot = snapshot,
        )
        return oversizedColumn(record)?.let { ArtifactPathResult.TooLong(it) } ?: ArtifactPathResult.Row(record)
    }

    /**
     * The part of [fileName] after `<artifactId>-<fileVersion>`, or null when the name does not
     * start with that prefix. A snapshot file is named either with the literal `-SNAPSHOT` or with
     * a deployment timestamp in its place.
     */
    private fun remainderAfterPrefix(fileName: String, artifactId: String, version: String, snapshot: Boolean): String? {
        if (!snapshot) {
            val prefix = "$artifactId-$version"
            return if (fileName.startsWith(prefix)) fileName.substring(prefix.length) else null
        }

        val releaseLine = "$artifactId-${version.removeSuffix(SNAPSHOT_SUFFIX)}"
        val literal = releaseLine + SNAPSHOT_SUFFIX
        if (fileName.startsWith(literal)) return fileName.substring(literal.length)

        if (!fileName.startsWith("$releaseLine-")) return null
        val afterLine = fileName.substring(releaseLine.length + 1)
        val timestamp = TIMESTAMP_AT_START.find(afterLine) ?: return null
        return afterLine.substring(timestamp.value.length)
    }

    /**
     * Splits the text after the dash of a classifier into classifier and extension. Returns null
     * when there is no dot at all. The classifier may be empty; the caller decides.
     */
    private fun splitClassifier(text: String): Pair<String, String>? {
        val asc = text.endsWith(ASC)
        val body = if (asc) text.dropLast(ASC.length) else text

        val compound = COMPOUND_EXTENSIONS.firstOrNull { body.endsWith(".$it") }
        val classifier: String
        val extension: String
        if (compound != null) {
            classifier = body.dropLast(compound.length + 1)
            extension = compound
        } else {
            val dot = body.lastIndexOf('.')
            if (dot < 0) return null
            classifier = body.substring(0, dot)
            extension = body.substring(dot + 1)
        }
        return classifier to if (asc) extension + ASC else extension
    }

    private fun oversizedColumn(record: ArtifactFileRecord): SearchColumn? = listOf(
        SearchColumn.PATH to record.path,
        SearchColumn.GROUP_ID to record.groupId,
        SearchColumn.ARTIFACT_ID to record.artifactId,
        SearchColumn.VERSION to record.version,
        SearchColumn.CLASSIFIER to (record.classifier ?: ""),
        SearchColumn.EXTENSION to record.extension,
    ).firstOrNull { (column, value) -> value.length > column.limit }?.first
}
