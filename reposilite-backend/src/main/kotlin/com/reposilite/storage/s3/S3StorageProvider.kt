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

@file:OptIn(ExperimentalIngotStorageApi::class)

package com.reposilite.storage.s3

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.internalServerError
import com.reposilite.shared.notFoundError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import com.reposilite.storage.api.UNKNOWN_LENGTH
import com.reposilite.status.probeListing
import io.javalin.http.ContentType
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import io.javalin.http.ContentType.Companion.OCTET_STREAM
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.ClusterBus
import net.onelitefeather.ingot.storage.capability.ClusterCacheAware
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import net.onelitefeather.ingot.storage.capability.ReadinessProbe
import net.onelitefeather.ingot.storage.capability.RelatedKeyAware
import net.onelitefeather.ingot.storage.capability.UsageCapability
import panda.std.Result
import panda.std.Result.ok
import panda.std.asSuccess
import panda.std.letIf
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CommonPrefix
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

private val skipBucketCreation = System.getProperty("reposilite.s3.skip-bucket-creation", "false") == "true"

/** The size of every part of a multipart upload but the last. S3 accepts parts from 5 MiB, so this is the size used for the probe too. */
private const val UPLOAD_PART_SIZE = 8 * 1024 * 1024

class S3StorageProvider(
    private val failureFacade: FailureFacade,
    private val s3: S3Client,
    private val bucket: String,
    private val keyPrefix: String = "",
    /** Restores the temporary file path for every upload, see `INGOT_S3_BUFFER_TO_DISK`. */
    private val bufferToDisk: Boolean = false,
) : StorageProvider, StorageEntrySource by S3EntrySource(s3, bucket, keyPrefix, failureFacade), Journalist,
    RelatedKeyAware, ClusterCacheAware, ReadinessProbe, UsageCapability {

    init {
        if (!skipBucketCreation) {
            createBucketIfNotExists()
        }
    }

    private fun createBucketIfNotExists() {
        try {
            val headBucketRequest = HeadBucketRequest.builder()
                .bucket(bucket)
                .build()

            s3.headBucket(headBucketRequest)
        } catch (noSuchBucket: NoSuchBucketException) {
            try {
                s3.createBucket { it.bucket(bucket) }
            } catch (bucketExists: BucketAlreadyExistsException) {
                // ignored
            } catch (buckedOwned: BucketAlreadyOwnedByYouException) {
                // ignored
            }
        }
    }

    override fun shutdown() {
        s3.close()
    }

    /** Bounds the HEAD answers of this storage to the cluster, when the storage caches them. */
    internal fun bindMetadataCacheToClusterBus() {
        (s3 as? CachableS3Client)?.bindToClusterBus()
    }

    override fun useRelatedKeys(rule: RelatedKeyRule) {
        (s3 as? CachableS3Client)?.useRelatedKeys(rule)
    }

    /** Bounds the head cache by [maxAge], once the cluster bus is open. */
    override fun bind(clusterBus: ClusterBus, maxAge: Duration) {
        (s3 as? CachableS3Client)?.bindToClusterBus(maxAge)
    }

    override fun invalidate(keys: Collection<String>) {
        (s3 as? CachableS3Client)?.invalidateKeys(keys)
    }

    /** The listing of the root, as every storage without its own probe is read. */
    override fun probe(): ReadinessOutcome =
        probeListing()

    /** S3 answers every write made through this instance, so the bucket is never a fixed size and no quota is reported. */
    override val reportsUsage: Boolean = false

    override val supportsQuota: Boolean = false

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> =
        inputStream.use { data ->
            if (bufferToDisk) {
                putThroughTemporaryFile(location, data)
            } else {
                putStreamed(location, data)
            }
        }

    /**
     * The escape hatch of `INGOT_S3_BUFFER_TO_DISK`: the whole stream is copied to a temporary file first, and that file is sent in one put.
     */
    private fun putThroughTemporaryFile(location: Location, data: InputStream): Result<Unit, ErrorResponse> {
        val temporary = File.createTempFile("reposilite-", "-s3-put")

        try {
            temporary.outputStream().use { destination ->
                data.copyTo(destination)
            }
        } catch (exception: IOException) {
            // A copy that fails while it is read is never sent, so its temporary file goes with it.
            temporary.delete()
            throw exception
        }

        try {
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(location.toBucketKey())
                .contentType(contentTypeOf(location))
                .contentLength(temporary.length())
                .build()
            s3.putObject(request, RequestBody.fromFile(temporary))
            return ok(Unit)
        } catch (exception: Exception) {
            return failed("write $location", exception)
        } finally {
            temporary.delete()
        }
    }

    private fun putStreamed(location: Location, data: InputStream): Result<Unit, ErrorResponse> {
        // S3 needs the content length of a single put, and the length of this stream is not known here. The first part
        // decides the request: a stream that ends within it is one put with its exact length, and a longer stream is a
        // multipart upload. Only one part is held at a time, so the bytes never pass through the temporary directory.
        val input = PushbackInputStream(data, 1)
        val first = input.readNBytes(UPLOAD_PART_SIZE)
        // A full first part is followed by one more byte only when the stream continues past it
        val next = if (first.size == UPLOAD_PART_SIZE) input.read() else -1

        return if (next == -1) {
            putSingle(location, first)
        } else {
            input.unread(next)
            putMultipart(location, first, input)
        }
    }

    private fun putSingle(location: Location, bytes: ByteArray): Result<Unit, ErrorResponse> =
        try {
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(location.toBucketKey())
                .contentType(contentTypeOf(location))
                .contentLength(bytes.size.toLong())
                .build()
            s3.putObject(request, RequestBody.fromBytes(bytes))
            ok(Unit)
        } catch (exception: Exception) {
            failed("write $location", exception)
        }

    /**
     * Sends a stream longer than one part as a multipart upload. The first part is already read, and [input] yields the rest.
     * A failed part or completion aborts the upload, so that no incomplete upload is left behind by a failure seen here.
     */
    private fun putMultipart(location: Location, first: ByteArray, input: InputStream): Result<Unit, ErrorResponse> {
        val key = location.toBucketKey()
        var uploadId: String? = null
        return try {
            val upload = s3.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentTypeOf(location))
                    .build()
            )
            val id = upload.uploadId()
            uploadId = id

            val parts = uploadParts(key, id, first, input)
            s3.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(id)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build()
            )
            ok(Unit)
        } catch (exception: Exception) {
            uploadId?.let { abortMultipartUpload(key, it) }
            failed("write $location", exception)
        }
    }

    /** Sends the parts in order. A part shorter than [UPLOAD_PART_SIZE] is the last one, and an empty read ends the upload. */
    private fun uploadParts(key: String, uploadId: String, first: ByteArray, input: InputStream): List<CompletedPart> {
        val parts = mutableListOf<CompletedPart>()
        var part = first
        while (part.isNotEmpty()) {
            val partNumber = parts.size + 1
            val request = UploadPartRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength(part.size.toLong())
                .build()
            val response = s3.uploadPart(request, RequestBody.fromBytes(part))
            parts += CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build()

            if (part.size < UPLOAD_PART_SIZE) {
                break
            }
            part = input.readNBytes(UPLOAD_PART_SIZE)
        }
        return parts
    }

    /** An abort that fails is logged with its exception. The upload then stays until the bucket lifecycle removes it. */
    private fun abortMultipartUpload(key: String, uploadId: String) {
        try {
            s3.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build()
            )
        } catch (exception: Exception) {
            failureFacade.throwException("S3 Storage Provider failed to abort the multipart upload of $key", exception)
        }
    }

    private fun contentTypeOf(location: Location): String =
        ContentType.mimeTypeByExtension(location.getExtension()) ?: OCTET_STREAM

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        try {
            val request = GetObjectRequest.builder()
            request.bucket(bucket)
            request.key(location.toBucketKey())
            s3.getObject(request.build()).asSuccess()
            // val bytes = ByteArray(Math.toIntExact(response.response().contentLength()))
            // response.read(bytes)
        } catch (_: NoSuchKeyException) {
            notFoundError("File not found: $location")
        } catch (exception: S3Exception) {
            // A bare 404 carries no NoSuchKey body, so it is only recognised by its status code
            s3Error("read $location", location, exception)
        } catch (exception: Exception) {
            failed("read $location", exception)
        }

    override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> =
        try {
            val request = GetObjectRequest.builder()
            request.bucket(bucket)
            request.key(location.toBucketKey())
            request.range("bytes=$offset-${offset + length - 1}")
            s3.getObject(request.build()).asSuccess()
        } catch (_: NoSuchKeyException) {
            notFoundError("File not found: $location")
        } catch (exception: S3Exception) {
            s3Error("read $location", location, exception)
        } catch (exception: Exception) {
            failed("read $location", exception)
        }

    override fun getFileDetails(location: Location): Result<FileDetails, ErrorResponse> =
        when {
            // The repository root is always a directory. Its key is the prefix, and a folder object created at
            // that prefix would make a HEAD succeed and mistake the whole repository for a single empty file.
            location.toString().isEmpty() -> listDirectoryDetails(location)
            else -> head(location)
                .map { toDocumentInfo(location, it) }
                .flatMapErr { headError ->
                    // Only a missing key may be a directory; a failed HEAD must not turn into an empty listing
                    when (headError.status) {
                        NOT_FOUND.code -> listDirectoryDetails(location)
                        else -> Result.error(headError)
                    }
                }
        }

    private fun listDirectoryDetails(location: Location): Result<FileDetails, ErrorResponse> =
        listDirectory(
            location = location,
            onDirectory = {
                Location.ofRequest(it.prefix().withoutKeyPrefix())
                    .orNull()
                    ?.let { prefix -> SimpleDirectoryInfo(name = prefix.getSimpleName()) }
            },
            onFile = {
                Location.ofRequest(it.key().withoutKeyPrefix())
                    .orNull()
                    ?.toDocumentInfo(
                        contentLength = it.size(),
                        lastModifiedTime = it.lastModified(),
                    )
            },
        ).map {
            location.toDirectoryInfo(files = it)
        }

    private fun Location.toDirectoryPrefix(): String =
        toString().replace('\\', '/')
            .let { "$it/" }
            .letIf({ it == "/" }, { "" })

    private fun Location.toBucketKey(): String =
        keyPrefix + toString().replace('\\', '/')

    private fun Location.toBucketDirectoryPrefix(): String =
        keyPrefix + toDirectoryPrefix()

    private fun String.withoutKeyPrefix(): String =
        removePrefix(keyPrefix)

    private fun toDocumentInfo(location: Location, head: HeadObjectResponse): FileDetails =
        location.toDocumentInfo(
            contentLength = head.contentLength(),
            lastModifiedTime = head.lastModified(),
        )

    private fun Location.toDocumentInfo(contentLength: Long?, lastModifiedTime: Instant?): DocumentInfo =
        DocumentInfo(
            name = getSimpleName(),
            contentType = ContentType.contentTypeByExtension(getExtension()) ?: APPLICATION_OCTET_STREAM,
            contentLength = contentLength ?: UNKNOWN_LENGTH,
            lastModifiedTime = lastModifiedTime,
        )

    private fun Location.toDirectoryInfo(files: List<FileDetails>): FileDetails =
        DirectoryInfo(
            name = getSimpleName(),
            files = files,
        )

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> {
        val prefix = location.toString().replace('\\', '/')
        // Inputs that collapse to "" or "." would prefix-match the entire bucket and wipe every repository
        // sharing it; mirrors the FileSystemStorageProvider guard against DELETE on the repository root.
        if (prefix.isBlank() || prefix == ".") {
            return badRequestError("Cannot remove repository root")
        }

        return try {
            s3.deleteObject(createDeleteRequest(keyPrefix + prefix))

            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("$keyPrefix$prefix/")
                .build()

            s3.listObjectsV2Paginator(request)
                .contents()
                .forEach { s3.deleteObject(createDeleteRequest(it.key())) }

            ok(Unit)
        } catch (exception: Exception) {
            failed("remove $location", exception)
        }
    }

    private fun createDeleteRequest(key: String): DeleteObjectRequest =
        DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        listDirectory(
            location = location,
            onDirectory = { Location.ofRequest(it.prefix().withoutKeyPrefix()).orNull() },
            onFile = { Location.ofRequest(it.key().withoutKeyPrefix()).orNull() },
        )

    private fun <T> listDirectory(
        location: Location,
        onDirectory: (CommonPrefix) -> T?,
        onFile: (S3Object) -> T?,
    ): Result<List<T>, ErrorResponse> =
        try {
            val request =
                ListObjectsV2Request
                    .builder()
                    .bucket(bucket)
                    .prefix(location.toBucketDirectoryPrefix())
                    .delimiter("/")
                    .build()

            val pages = s3.listObjectsV2Paginator(request).toList()
            val directories = pages.flatMap { it.commonPrefixes() }.mapNotNull(onDirectory)
            // Keys ending in '/' are folder objects written by consoles and tools, not entries of the repository.
            val files = pages.flatMap { it.contents() }.filterNot { it.key().endsWith("/") }.mapNotNull(onFile)
            val entries = directories + files

            when {
                entries.isEmpty() -> notFoundError("Directory not found or is empty")
                else -> entries.asSuccess()
            }
        } catch (exception: S3Exception) {
            s3Error("list $location", location, exception)
        } catch (exception: Exception) {
            failed("list $location", exception)
        }

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        head(location)
            .map { FileTime.from(it.lastModified()) }

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        head(location)
            .map { it.contentLength() }

    private fun head(location: Location): Result<HeadObjectResponse, ErrorResponse> =
        try {
            val request = HeadObjectRequest.builder()
            request.bucket(bucket)
            request.key(location.toBucketKey())
            s3.headObject(request.build()).asSuccess()
        } catch (ignored: NoSuchKeyException) {
            notFoundError("File not found: $location")
        } catch (exception: S3Exception) {
            // HEAD has no error body, so a missing key arrives as a bare 404 rather than NoSuchKey
            s3Error("HEAD ${location.toBucketKey()}", location, exception)
        } catch (exception: Exception) {
            failed("HEAD ${location.toBucketKey()}", exception)
        }

    /**
     * Answers a failed S3 read. A missing key is a 404, and so is a 403: S3 answers a missing key with 403 when the
     * credentials lack ListBucket, and a deployment with such a policy must keep getting 404 for misses. Any other
     * S3 failure is a server error, so that a transient problem is never reported as a missing file.
     */
    private fun <T> s3Error(operation: String, location: Location, exception: S3Exception): Result<T, ErrorResponse> =
        when (exception.statusCode()) {
            NOT_FOUND.code -> notFoundError("File not found: $location")
            FORBIDDEN.code -> {
                logger.debug("S3 denied $operation, answering as not found (the credentials may lack ListBucket)")
                notFoundError("File not found: $location")
            }
            else -> failed(operation, exception)
        }

    /**
     * Logs an unexpected S3 failure (server error, network, credentials) and answers it as a server error.
     */
    private fun <T> failed(operation: String, exception: Exception): Result<T, ErrorResponse> {
        failureFacade.throwException("S3 Storage Provider failed to $operation", exception)
        return internalServerError("Failed to $operation")
    }

    override fun exists(location: Location): Boolean =
        head(location)
            .fold(
                { true },
                { getFiles(location).isOk }
            )

    override fun usage(): Result<Long, ErrorResponse> =
        ok(-1)

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> =
        ok(Long.MAX_VALUE)

    override fun getLogger(): Logger =
        failureFacade.logger

}
