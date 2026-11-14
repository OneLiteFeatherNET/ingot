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

package net.onelitefeather.ingot.cluster

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.plugin.api.StorageUsageFigure
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A usage snapshot of a replica that is newer than this one may carry fields this version does not read. The figures must still decode.
 */
internal class StorageUsageEventCompatibilityTest {

    private val computedAt = Instant.parse("2026-11-08T10:00:00Z")

    @Test
    fun `a usage event that carries an extra field in a figure still decodes its figures`() {
        // given: a snapshot of one repository with an extra field in its figure, as a newer replica sends it
        val payload = StorageUsageMessage.toPayload(
            StorageUsageRefreshedEvent(computedAt, listOf(StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L)))
        )
        (payload.get("repositories").single() as ObjectNode).put("storageType", "s3")

        // when: the payload is decoded
        val event = StorageUsageMessage.fromPayload(payload)

        // then: the figures are decoded as before the field was added
        assertThat(event).`as`("decoded snapshot").isNotNull
        val figure = event!!.repositories.single()
        assertThat(figure.id).`as`("id of the figure").isEqualTo("releases")
        assertThat(figure.usedBytes).`as`("used bytes of the figure").isEqualTo(900L)
        assertThat(figure.fileCount).`as`("file count of the figure").isEqualTo(7L)
    }

    @Test
    fun `a usage event without a storage type reads the figure as unknown`() {
        // given: a snapshot of a replica that predates the storage type of a figure
        val payload = StorageUsageMessage.toPayload(
            StorageUsageRefreshedEvent(computedAt, listOf(StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L)))
        )
        (payload.get("repositories").single() as ObjectNode).remove("storageType")

        // when: the payload is decoded
        val figure = StorageUsageMessage.fromPayload(payload)!!.repositories.single()

        // then: the storage type of the figure is unknown
        assertThat(figure.storageType).`as`("storage type of a figure without one").isEqualTo("unknown")
    }

    @Test
    fun `a usage event with a storage type keeps the storage type of its figure`() {
        // given: a snapshot whose figure carries the storage type it was measured with
        val payload = StorageUsageMessage.toPayload(
            StorageUsageRefreshedEvent(computedAt, listOf(StorageUsageFigure("archive", usedBytes = 1L, fileCount = 1L, storageType = "s3")))
        )

        // when: the payload is encoded and decoded again
        val figure = StorageUsageMessage.fromPayload(payload)!!.repositories.single()

        // then: the storage type survives the round trip
        assertThat(figure.storageType).`as`("storage type after the round trip").isEqualTo("s3")
    }

}
