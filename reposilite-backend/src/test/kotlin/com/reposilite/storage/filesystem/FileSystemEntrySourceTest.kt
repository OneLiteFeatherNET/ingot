package com.reposilite.storage.filesystem

import com.reposilite.storage.StorageEntry
import com.reposilite.storage.api.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes

// A fixed instant rather than the clock, so the expectation holds on every machine at any time of day.
private val MODIFIED_AT: Instant = Instant.parse("2026-10-15T12:00:00.123Z")

internal class FileSystemEntrySourceTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `should list files depth first with sibling names in order`() {
        // given: nested files and a top-level file whose name starts with a directory name
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")

        // when: the whole tree is listed
        val paths = listAll().map { it.path.toString() }

        // then: directory "a" is entered where its name sorts, so its files come before "a.txt"
        assertThat(paths).describedAs("paths in listing order")
            .containsExactly("a/x.jar", "a/y/k.pom", "a.txt", "b/z.jar")
    }

    @Test
    fun `should end a page at its limit and point the cursor at the last entry of the page`() {
        // given: four files
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")

        // when: the first page of two entries is requested
        val first = assertOk(source().listEntries(Location.empty(), after = null, limit = 2))

        // then: the page is full and the cursor names its last entry because more entries follow
        assertThat(first.entries.map { it.path.toString() }).containsExactly("a/x.jar", "a/y/k.pom")
        assertThat(first.next).describedAs("cursor after a full page").isEqualTo("a/y/k.pom")

        // when: the next page is requested with that cursor
        val second = assertOk(source().listEntries(Location.empty(), after = first.next, limit = 2))

        // then: the remaining entries follow and the cursor is null at the end of the tree
        assertThat(second.entries.map { it.path.toString() }).containsExactly("a.txt", "b/z.jar")
        assertThat(second.next).describedAs("cursor after the last entry").isNull()
    }

    @Test
    fun `should resume after an existing file without repeating it`() {
        // given: the same four files
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")

        // when: the listing resumes after a file inside directory "a"
        val paths = listAll(after = "a/x.jar").map { it.path.toString() }

        // then: the file is not repeated and everything after it follows in order
        assertThat(paths).containsExactly("a/y/k.pom", "a.txt", "b/z.jar")
    }

    @Test
    fun `should resume after a cursor that no longer exists and skip the siblings that sort before it`() {
        // given: the same four files
        file("b/z.jar")
        file("a/y/k.pom")
        file("a/x.jar")
        file("a.txt")

        // when: the listing resumes after "a/z", a path that is gone, inside directory "a"
        val paths = listAll(after = "a/z").map { it.path.toString() }

        // then: "x.jar" and "y" sort before "z" and are skipped, while the top-level entries after it are kept
        assertThat(paths).containsExactly("a.txt", "b/z.jar")
    }

    @Test
    fun `should report the size and the modification time of each file`() {
        // given: a file of five bytes with a known modification time
        val file = file("a/x.jar", size = 5)
        Files.setLastModifiedTime(file, FileTime.from(MODIFIED_AT))

        // when: the tree is listed
        val entry = listAll().single()

        // then: the entry carries the path, the size in bytes and the modification time
        assertThat(entry.path.toString()).isEqualTo("a/x.jar")
        assertThat(entry.size).describedAs("size in bytes").isEqualTo(5L)
        assertThat(entry.lastModified).describedAs("last modified").isEqualTo(MODIFIED_AT)
    }

    @Test
    fun `should list only the files below the prefix`() {
        // given: a file next to a directory whose name it starts with
        file("a/x.jar")
        file("a/y/k.pom")
        file("a.txt")
        file("b/z.jar")

        // when: the tree below "a" is listed
        val paths = listAll(prefix = "a").map { it.path.toString() }

        // then: "a.txt" is not below "a" and is left out
        assertThat(paths).containsExactly("a/x.jar", "a/y/k.pom")
    }

    @Test
    fun `should return an empty page without a cursor when the prefix does not exist`() {
        // given: a repository without the requested directory
        file("a/x.jar")

        // when: the missing directory is listed
        val page = assertOk(source().listEntries(Location.of("missing"), after = null, limit = 10))

        // then: the page is empty and there is nothing to resume
        assertThat(page.entries).describedAs("entries").isEmpty()
        assertThat(page.next).describedAs("cursor").isNull()
    }

    private fun source(): FileSystemEntrySource =
        FileSystemEntrySource(root)

    private fun file(path: String, size: Int = 1): Path =
        root.resolve(path).createParentDirectories().also { it.writeBytes(ByteArray(size)) }

    private fun listAll(prefix: String = "", after: String? = null): List<StorageEntry> {
        val entries = mutableListOf<StorageEntry>()
        var cursor = after
        do {
            val page = assertOk(source().listEntries(Location.of(prefix), cursor, limit = 100))
            entries += page.entries
            cursor = page.next
        } while (cursor != null)
        return entries
    }

}
