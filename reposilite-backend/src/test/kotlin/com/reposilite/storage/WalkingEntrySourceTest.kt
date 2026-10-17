package com.reposilite.storage

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemEntrySource
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.ResultAssertions.assertOk
import panda.std.asSuccess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes

// A fixed instant rather than the clock, so the expectation holds on every machine at any time of day.
private val MODIFIED_AT: Instant = Instant.parse("2026-10-15T12:00:00.123Z")

internal class WalkingEntrySourceTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `should list the files of a provider without a native listing in file system order`() {
        // given: nested files and a top-level file whose name starts with a directory name
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")

        // when: the tree is listed through the walking fallback
        val paths = listAll(WalkingEntrySource(plainStorage())).map { it.path.toString() }

        // then: the order matches the file system source, so the two sources can be swapped
        assertThat(paths).describedAs("paths in listing order")
            .containsExactly("a/x.jar", "a/y/k.pom", "a.txt", "b/z.jar")
    }

    @Test
    fun `should page through the walked files with a cursor`() {
        // given: four files
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")
        val source = WalkingEntrySource(plainStorage())

        // when: the first page of two entries is requested
        val first = assertOk(source.listEntries(Location.empty(), after = null, limit = 2))

        // then: the cursor names the last entry of the full page
        assertThat(first.entries.map { it.path.toString() }).containsExactly("a/x.jar", "a/y/k.pom")
        assertThat(first.next).describedAs("cursor after a full page").isEqualTo("a/y/k.pom")

        // when: the next page is requested with that cursor
        val second = assertOk(source.listEntries(Location.empty(), after = first.next, limit = 2))

        // then: the remaining entries follow and the cursor is null at the end
        assertThat(second.entries.map { it.path.toString() }).containsExactly("a.txt", "b/z.jar")
        assertThat(second.next).describedAs("cursor after the last entry").isNull()
    }

    @Test
    fun `should report the size and the modification time of walked files`() {
        // given: a file of five bytes with a known modification time
        val file = file("a/x.jar", size = 5)
        Files.setLastModifiedTime(file, FileTime.from(MODIFIED_AT))

        // when: the tree is listed through the walking fallback
        val entry = assertOk(WalkingEntrySource(plainStorage()).listEntries(Location.empty(), after = null, limit = 10))
            .entries.single()

        // then: the entry carries the size and the modification time
        assertThat(entry.path.toString()).isEqualTo("a/x.jar")
        assertThat(entry.size).describedAs("size in bytes").isEqualTo(5L)
        assertThat(entry.lastModified).describedAs("last modified").isEqualTo(MODIFIED_AT)
    }

    @Test
    fun `should report the epoch as the modification time when the provider reports none`() {
        // given: a provider whose directory listing carries a file without a modification time
        val source = WalkingEntrySource(storageWithoutModificationTimes())

        // when: the tree is listed
        val entry = assertOk(source.listEntries(Location.empty(), after = null, limit = 10)).entries.single()

        // then: the entry uses the epoch, which is the documented stand-in for an unknown time
        assertThat(entry.lastModified).describedAs("last modified").isEqualTo(Instant.EPOCH)
    }

    @Test
    fun `should use the native listing when the provider implements it`() {
        // given: a file system provider, which implements the listing itself
        val storage: StorageProvider = FileSystemStorageProviderFactory.of(InMemoryLogger(), root, "100%")

        // when: the source for the provider is chosen
        val source = storage.entrySource()

        // then: the provider is used as its own source
        assertThat(source).describedAs("source of a file system provider").isSameAs(storage)
    }

    @Test
    fun `should use the walking fallback when the provider does not implement the listing`() {
        // given: a provider without a native listing
        val storage = plainStorage()

        // when: the source for the provider is chosen
        val source = storage.entrySource()

        // then: the walking fallback is used
        assertThat(source).describedAs("source of a provider without a native listing").isInstanceOf(WalkingEntrySource::class.java)
    }

    private fun file(path: String, size: Int = 1): Path =
        root.resolve(path).createParentDirectories().also { it.writeBytes(ByteArray(size)) }

    private fun plainStorage(): StorageProvider =
        PlainStorage(FileSystemStorageProviderFactory.of(InMemoryLogger(), root, "100%"))

    private fun storageWithoutModificationTimes(): StorageProvider =
        object : StorageProvider by plainStorage() {
            override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
                if (location.toString().isEmpty()) {
                    DirectoryInfo(
                        name = "",
                        files = listOf(
                            DocumentInfo(
                                name = "app.jar",
                                contentType = APPLICATION_OCTET_STREAM,
                                contentLength = 1,
                                lastModifiedTime = null,
                            )
                        ),
                    ).asSuccess()
                } else {
                    notFoundError("File not found: $location")
                }
        }

    private fun listAll(source: StorageEntrySource): List<StorageEntry> {
        val entries = mutableListOf<StorageEntry>()
        var cursor: String? = null
        do {
            val page = assertOk(source.listEntries(Location.empty(), cursor, limit = 100))
            entries += page.entries
            cursor = page.next
        } while (cursor != null)
        return entries
    }

}

/**
 * A provider that only offers the [StorageProvider] contract, as a third-party provider does.
 * Hiding the native listing behind a delegate is what makes the walking fallback apply.
 */
private class PlainStorage(storage: StorageProvider) : StorageProvider by storage
