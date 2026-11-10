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
@file:OptIn(ExperimentalIngotFormatApi::class)

package com.reposilite.maven

import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.REPOSITORY_NAME_MAX_LENGTH
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import java.io.File
import java.io.InputStream
import java.nio.file.attribute.FileTime
import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import net.onelitefeather.ingot.format.RepositoryFormat
import net.onelitefeather.ingot.repository.RegisteredRepository
import panda.std.Result

@Suppress("DeprecatedCallableAddReplaceWith")
class Repository internal constructor(
    override val name: String,
    override val visibility: RepositoryVisibility,
    val description: String,
    val tags: List<String>,
    val redeployment: Boolean,
    val preserveSnapshots: Boolean,
    val mirrorHosts: List<MirrorHost>,
    override val storageProvider: StorageProvider,
    val storagePolicy: StoragePolicy,
    val metadataMaxAgeInSeconds: Long,
    resolutionCacheMaxEntries: Int,
    /** The declared type, null for an untyped repository, which keeps the behaviour it always had. */
    val declaredType: RepositoryType? = null,
    /** Group id prefixes this group serves from hosted members only, see [NamespaceProtection]. Empty disables the rule. */
    val protectedGroupPrefixes: List<String> = emptyList(),
    /** What a checksum file that does not match its stored artifact gets, see [UploadChecksumPolicy]. */
    val uploadChecksumPolicy: UploadChecksumPolicy = UploadChecksumPolicy.IGNORE,
    /** The version policy of deploys, [VersionPolicy.MIXED] unless the settings set one. */
    val versionPolicy: VersionPolicy = VersionPolicy.MIXED,
    /** Whether deploys and deletes are refused with 405. Reads are not affected. */
    val readOnly: Boolean = false,
    /** The format of this repository, see [net.onelitefeather.ingot.format.RepositoryFormats]. */
    internal val repositoryFormat: RepositoryFormat,
) : RegisteredRepository {

    init {
        check(name.length < REPOSITORY_NAME_MAX_LENGTH) { "Repository name cannot exceed $REPOSITORY_NAME_MAX_LENGTH characters" }
    }

    /** The id of the format of this repository, `maven2` unless the repository names another installed format. */
    override val format: String
        get() = repositoryFormat.id

    /** Whether the declared type is set. Untyped repositories report the type they are derived as. */
    val typeExplicit: Boolean
        get() = declaredType != null

    /** Whether a mirror of this repository stores the files it proxies. Reads of such files keep a local copy. */
    override val storesProxiedFiles: Boolean
        get() = mirrorHosts.any { it.configuration.store }

    /** The effective type, see [effectiveRepositoryType]: the declared type, or the one derived from the mirrored repositories. */
    override val effectiveType: EffectiveRepositoryType
        get() {
            val loopback = mirrorHosts.count { it.client is RepositoryLoopbackClient }
            return effectiveRepositoryType(declaredType, loopbackReferences = loopback, externalReferences = mirrorHosts.size - loopback)
        }

    /** Whether deployments and deletions are accepted. A typed proxy or group takes no uploads; see [acceptsUploads]. */
    val acceptsUploads: Boolean
        get() = declaredType.acceptsUploads()

    /** The write policy shown to operators, or null for a repository that takes no uploads at all. */
    val writePolicy: WritePolicy?
        get() = if (acceptsUploads) writePolicyOf(redeployment, readOnly) else null

    internal val resolutionCache: ResolutionCache? =
        if (resolutionCacheMaxEntries > 0) ResolutionCache(resolutionCacheMaxEntries) else null

    fun acceptsDeploymentOf(location: Location): Boolean =
        redeployment || repositoryFormat.pathRules.isGeneratedMetadata(location) || !storageProvider.exists(location)

    fun acceptsCachingOf(gav: Location): Boolean =
        !redeployment && !repositoryFormat.pathRules.isGeneratedMetadata(gav)

    fun writeFileChecksums(location: Location, bytes: ByteArray): Result<Unit, ErrorResponse> =
        writeFileChecksums(location, bytes.inputStream())

    fun writeFileChecksums(location: Location, bytes: InputStream): Result<Unit, ErrorResponse> {
        val temporaryFile = File.createTempFile("reposilite-", "-file-checksum")

        bytes.use { input ->
            temporaryFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return Checksum.entries
            .fold(Result.ok<Unit, ErrorResponse>(Unit)) { result, checksum -> result.flatMap { writeFileChecksum(location, temporaryFile, checksum) } }
            .peek { temporaryFile.delete() }
    }

    private fun writeFileChecksum(location: Location, file: File, checksum: Checksum): Result<Unit, ErrorResponse> =
        file.inputStream().use {
            val checksumFile = location.resolveSibling(location.getSimpleName() + "." + checksum.extension)
            storageProvider.putFile(checksumFile, checksum.generate(it).byteInputStream())
        }

    @Deprecated(message = "Use Repository#storageProvider")
    fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> =
        storageProvider.putFile(location, inputStream)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        storageProvider.getFile(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        storageProvider.getFileDetails(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun removeFile(location: Location): Result<Unit, ErrorResponse> =
        storageProvider.removeFile(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getFiles(directoryLocation: Location): Result<List<Location>, ErrorResponse> =
        storageProvider.getFiles(directoryLocation)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        storageProvider.getLastModifiedTime(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        storageProvider.getFileSize(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun exists(location: Location): Boolean =
        storageProvider.exists(location)

    @Deprecated(message = "Use Repository#storageProvider")
    fun getUsage(): Result<Long, ErrorResponse> =
        storageProvider.usage()

    @Deprecated(message = "Use Repository#storageProvider")
    fun canHold(contentLength: Long): Result<*, ErrorResponse> =
        storageProvider.canHold(contentLength)

    fun shutdown() =
        storageProvider.shutdown()

}
