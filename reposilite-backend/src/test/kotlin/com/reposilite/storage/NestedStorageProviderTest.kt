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
