@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.support

import net.onelitefeather.ingot.storage.capability.SupportDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The support details of one repository storage: the settings entries and the provider entries, with the settings winning. */
internal class StorageDetailsTest {

    private class Details(private val values: Map<String, String>) : SupportDetails {
        override fun describe(): Map<String, String> = values
    }

    @Test
    fun `the entries of the provider are added to the entries of the settings`() {
        // when: the settings describe the delegate and the provider adds its counters
        val details = storageDetails(Details(mapOf("bucket" to "releases")), Details(mapOf("hits" to "3")))

        // then: both sets of entries are reported
        assertThat(details).containsEntry("bucket", "releases").containsEntry("hits", "3")
    }

    @Test
    fun `a settings entry wins over a provider entry with the same key`() {
        // when: the settings and the provider both report the same key
        val details = storageDetails(Details(mapOf("prefix" to "configured")), Details(mapOf("prefix" to "from-provider")))

        // then: the value of the settings is reported
        assertThat(details).containsEntry("prefix", "configured")
    }

    @Test
    fun `a storage without support details reports no entries`() {
        // when: neither the settings nor the provider implements the interface
        val details = storageDetails(settings = "plain", provider = "plain")

        // then: the details are empty
        assertThat(details).isEmpty()
    }

}
