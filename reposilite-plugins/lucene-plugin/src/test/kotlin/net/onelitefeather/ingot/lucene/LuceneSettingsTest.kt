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

package net.onelitefeather.ingot.lucene

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class LuceneSettingsTest {

    private val defaultDirectory: Path = Path.of("data", "search", "lucene")

    @Test
    fun `the ingot spelling of the directory wins over the reposilite spelling`() {
        val settings = LuceneSettings.read(
            mapOf(
                "INGOT_LUCENE_DIRECTORY" to "/var/lib/ingot/lucene",
                "REPOSILITE_LUCENE_DIRECTORY" to "/var/lib/reposilite/lucene",
            ),
            defaultDirectory,
        )

        assertThat(settings.directory)
            .`as`("directory when both spellings are set")
            .isEqualTo(Path.of("/var/lib/ingot/lucene"))
    }

    @Test
    fun `the reposilite spelling of the directory is read when the ingot spelling is unset`() {
        val settings = LuceneSettings.read(mapOf("REPOSILITE_LUCENE_DIRECTORY" to "/srv/lucene"), defaultDirectory)

        assertThat(settings.directory).`as`("directory from the fallback spelling").isEqualTo(Path.of("/srv/lucene"))
    }

    @Test
    fun `the default directory is the search directory below the data directory when no variable is set`() {
        val settings = LuceneSettings.read(emptyMap(), defaultDirectory)

        assertThat(settings.directory).`as`("directory without any variable").isEqualTo(defaultDirectory)
    }

    @Test
    fun `a blank directory variable counts as unset`() {
        val settings = LuceneSettings.read(mapOf("INGOT_LUCENE_DIRECTORY" to "   "), defaultDirectory)

        assertThat(settings.directory).`as`("directory from a blank variable").isEqualTo(defaultDirectory)
    }

    @Test
    fun `the minimum free space defaults to one GiB`() {
        val settings = LuceneSettings.read(emptyMap(), defaultDirectory)

        assertThat(settings.minFreeBytes).`as`("minimum free bytes by default").isEqualTo(1L shl 30)
    }

    @Test
    fun `the minimum free space is read from its variable in bytes`() {
        val settings = LuceneSettings.read(mapOf("INGOT_LUCENE_MIN_FREE_BYTES" to "5368709120"), defaultDirectory)

        assertThat(settings.minFreeBytes).`as`("minimum free bytes from the variable").isEqualTo(5_368_709_120L)
    }

    @Test
    fun `a negative minimum free space is refused and the message names the variable`() {
        assertThatThrownBy { LuceneSettings.read(mapOf("INGOT_LUCENE_MIN_FREE_BYTES" to "-1"), defaultDirectory) }
            .`as`("negative minimum free space")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_LUCENE_MIN_FREE_BYTES")
    }

    @Test
    fun `a minimum free space that is not a number is refused and the message names the variable`() {
        assertThatThrownBy { LuceneSettings.read(mapOf("INGOT_LUCENE_MIN_FREE_BYTES" to "one gibibyte"), defaultDirectory) }
            .`as`("minimum free space that is not a number")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_LUCENE_MIN_FREE_BYTES")
    }

}
