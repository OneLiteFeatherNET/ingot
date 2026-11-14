@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.rebuild

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.database.DatabaseSearchBackend
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import net.onelitefeather.ingot.search.runs.SimulatedCrash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/**
 * The index identity of a backend decides whether a rebuild cursor may be continued (search backend seam, D6 of
 * `add-lucene-search-plugin`). A physical index that was replaced must not inherit the position of the old one.
 */
internal class RebuildIndexIdentityTest {

    @TempDir
    lateinit var directory: Path

    /** Delegates every member to [delegate] and reports [identity] as the index identity. */
    private class IdentifiedBackend(
        private val delegate: ArtifactSearchBackend,
        var identity: String?,
    ) : ArtifactSearchBackend by delegate {

        override fun indexIdentity(): String? = identity
    }

    @Test
    fun `a resumed rebuild starts from the first page when the index identity changed`() {
        withHarness(directory, "replaced") {
            // given: a run that dies on the third page while the index reports identity index-a
            (1..6).forEach { entries.put("releases", jar(it)) }
            entries.crashOnRequest = 3
            val original = IdentifiedBackend(DatabaseSearchBackend(database, clock), "index-a")
            assertThatThrownBy { service("instance-a", original).rebuild(RunTrigger.MANUAL) }
                .isInstanceOf(SimulatedCrash::class.java)
            assertThat(runs.recent(1).single().cursorPath)
                .`as`("cursor of the crashed run before the replacement")
                .isEqualTo(jar(4))

            // when: the index is replaced (identity index-b) and another instance resumes after the lease ran out
            entries.crashOnRequest = null
            entries.requests.clear()
            clock.advance(Duration.ofMinutes(3))
            val replaced = IdentifiedBackend(DatabaseSearchBackend(database, clock), "index-b")
            val resumed = service("instance-b", replaced).resume()

            // then: the rebuild asks for the first page, completes, and stores the new identity with the run
            assertThat(entries.requests.first().after)
                .`as`("first page asked after the replacement")
                .isNull()
            assertThat(resumed?.status).`as`("status of the resumed run").isEqualTo(RunStatus.COMPLETED)
            assertThat(state.get(SearchStateKey.INDEX_IDENTITY))
                .`as`("identity stored by the rebuild")
                .isEqualTo("index-b")
        }
    }

    @Test
    fun `a resumed rebuild keeps its cursor when the backend reports no index identity`() {
        withHarness(directory, "nullIdentity") {
            (1..6).forEach { entries.put("releases", jar(it)) }
            entries.crashOnRequest = 3
            val original = IdentifiedBackend(DatabaseSearchBackend(database, clock), "index-a")
            assertThatThrownBy { service("instance-a", original).rebuild(RunTrigger.MANUAL) }
                .isInstanceOf(SimulatedCrash::class.java)

            entries.crashOnRequest = null
            entries.requests.clear()
            clock.advance(Duration.ofMinutes(3))
            val withoutIdentity = IdentifiedBackend(DatabaseSearchBackend(database, clock), null)
            service("instance-b", withoutIdentity).resume()

            assertThat(entries.requests.first().after)
                .`as`("first page asked by a backend without an identity")
                .isEqualTo(jar(4))
        }
    }

    @Test
    fun `an index identity is stored when the first rebuild runs`() {
        withHarness(directory, "firstIdentity") {
            (1..2).forEach { entries.put("releases", jar(it)) }
            val identified = IdentifiedBackend(DatabaseSearchBackend(database, clock), "index-a")

            service("instance-a", identified).rebuild(RunTrigger.MANUAL)

            assertThat(state.get(SearchStateKey.INDEX_IDENTITY))
                .`as`("identity stored after the first rebuild")
                .isEqualTo("index-a")
        }
    }

    @Test
    fun `a backend that does not override the index identity reports none`() {
        assertThat(FakeArtifactSearchBackend().indexIdentity())
            .`as`("identity of a backend that keeps the default")
            .isNull()
    }

    @Test
    fun `the database backend reports no index identity`() {
        withHarness(directory, "databaseIdentity") {
            assertThat(DatabaseSearchBackend(database, clock).indexIdentity())
                .`as`("identity of the database backend")
                .isNull()
        }
    }

}
