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

@file:OptIn(ExperimentalIngotStorageApi::class)

package com.reposilite.storage

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import net.onelitefeather.ingot.storage.StorageProviderNotInstalledException

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class NestedStorageProviderTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    private val failureFacade = FailureFacade(journalist)

    /** The provider that the leaf factory returns. A dynamic proxy keeps the test free of the full storage interface. */
    private val leaf: StorageProvider = java.lang.reflect.Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(StorageProvider::class.java),
    ) { _, _, _ -> null } as StorageProvider

    private fun facade(vararg factories: StorageProviderFactory<*, *>): StorageFacade {
        val registry = StorageProviderRegistry()
        factories.forEach { registry.register(it, source = "test", builtIn = false) }
        return StorageFacade(registry)
    }

    private fun build(facade: StorageFacade, settings: StorageProviderSettings): StorageProvider? =
        facade.createStorageProvider(journalist, failureFacade, Path.of("work"), "releases", settings)

    @Test
    fun `a factory builds the provider of another registered type from its settings`() {
        val storage = facade(LeafFactory(leaf), WrapperFactory())

        val provider = build(storage, WrapperSettings(delegate = LeafSettings()))

        assertThat(provider).describedAs("the wrapper must return the provider that the nested leaf built").isSameAs(leaf)
    }

    @Test
    fun `a nested type that is not installed is refused and the message names it`() {
        val storage = facade(WrapperFactory())

        assertThatThrownBy { build(storage, WrapperSettings(delegate = LeafSettings())) }
            .isInstanceOf(StorageProviderNotInstalledException::class.java)
            .hasMessageContaining("'leaf'")
            .hasMessageContaining("not installed")
    }

    @Test
    fun `a nested type that is already being built is refused and the message names the chain`() {
        val storage = facade(WrapperFactory())

        assertThatThrownBy { build(storage, WrapperSettings(delegate = WrapperSettings(delegate = LeafSettings()))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("wrapper -> wrapper")
    }

    @Test
    fun `a nested provider is shut down when the outer factory fails after building it`() {
        val delegate = RecordingProvider()
        val storage = facade(RecordingLeafFactory(delegate.provider), FailingWrapperFactory())

        assertThatThrownBy { build(storage, WrapperSettings(delegate = LeafSettings())) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("outer factory failed")

        assertThat(delegate.shutdowns)
            .describedAs("the delegate built before the failure must not leak")
            .isEqualTo(1)
    }

    @Test
    fun `an outer type that is not registered still gives no provider`() {
        val storage = facade(LeafFactory(leaf))

        assertThat(build(storage, WrapperSettings(delegate = LeafSettings())))
            .describedAs("an unregistered outer type keeps the existing answer of null")
            .isNull()
    }

}

private data class LeafSettings(override val type: String = "leaf") : StorageProviderSettings

private data class WrapperSettings(
    override val type: String = "wrapper",
    val delegate: StorageProviderSettings,
) : StorageProviderSettings

private class LeafFactory(private val provider: StorageProvider) : IngotStorageProviderFactory<StorageProvider, LeafSettings> {

    override val type: String = "leaf"
    override val settingsType: Class<LeafSettings> = LeafSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: LeafSettings): StorageProvider = provider

}

private class WrapperFactory : IngotStorageProviderFactory<StorageProvider, WrapperSettings> {

    override val type: String = "wrapper"
    override val settingsType: Class<WrapperSettings> = WrapperSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: WrapperSettings): StorageProvider =
        context.createProvider(settings.delegate)

}

/** A leaf that hands out [provider] and counts the calls of [StorageProvider.shutdown] on it. */
private class RecordingLeafFactory(private val provider: StorageProvider) : IngotStorageProviderFactory<StorageProvider, LeafSettings> {

    override val type: String = "leaf"
    override val settingsType: Class<LeafSettings> = LeafSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: LeafSettings): StorageProvider = provider

}

/** Builds its delegate and then fails, as a plugin does when a later check of its settings rejects them. */
private class FailingWrapperFactory : IngotStorageProviderFactory<StorageProvider, WrapperSettings> {

    override val type: String = "wrapper"
    override val settingsType: Class<WrapperSettings> = WrapperSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: WrapperSettings): StorageProvider {
        context.createProvider(settings.delegate)
        throw IllegalStateException("outer factory failed")
    }

}

/** A provider whose only observable behaviour is the number of [shutdown] calls. */
private class RecordingProvider {

    var shutdowns = 0
        private set

    val provider: StorageProvider = java.lang.reflect.Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(StorageProvider::class.java),
    ) { _, method, _ ->
        if (method.name == "shutdown") shutdowns++
        null
    } as StorageProvider

}
