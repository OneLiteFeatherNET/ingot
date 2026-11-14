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

package com.reposilite.storage

import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import java.io.File
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * The storage providers known to this server, by type. The built-in factories of the application class path are registered first,
 * then the factories of the plugin class loaders, so a plugin can never replace a built-in provider. A refused factory is reported
 * through [warn] and is not used; the other factories keep working.
 */
@OptIn(ExperimentalIngotStorageApi::class)
internal class StorageProviderRegistry(private val warn: (String) -> Unit = {}) {

    private class Registration(val factory: StorageProviderFactory<*, *>, val source: String, val className: String)

    private val registrations = LinkedHashMap<String, Registration>()
    private val discoveredClasses = mutableSetOf<Class<*>>()

    /**
     * Registers the factories that [loader] lists as services. A factory class that was already seen is skipped silently, because a plugin
     * class loader lists the built-in factories of its parent too.
     */
    fun discover(loader: ClassLoader, builtIn: Boolean) {
        val factories = ServiceLoader.load(StorageProviderFactory::class.java, loader).iterator()
        while (true) {
            val factory = try {
                if (!factories.hasNext()) break
                factories.next()
            } catch (error: ServiceConfigurationError) {
                warn("Storage provider factories of a plugin are not loaded: ${error.message}")
                break
            }
            if (discoveredClasses.add(factory.javaClass)) {
                register(factory, sourceOf(factory, builtIn), builtIn)
            }
        }
    }

    internal fun register(factory: StorageProviderFactory<*, *>, source: String, builtIn: Boolean): Boolean {
        val className = factory.javaClass.name
        val type = factory.type

        if (!builtIn && factory !is IngotStorageProviderFactory<*, *>) {
            warn("Storage provider factory $className of $source is refused, it does not implement IngotStorageProviderFactory and declares no API version")
            return false
        }
        if (factory is IngotStorageProviderFactory<*, *> && factory.apiVersion != STORAGE_PROVIDER_API_VERSION) {
            warn("Storage provider factory $className of $source is refused, it declares API version ${factory.apiVersion} and this server supports $STORAGE_PROVIDER_API_VERSION")
            return false
        }
        if (!ID_PATTERN.matches(type)) {
            warn("Storage provider factory $className of $source is refused, its type '$type' is not lower-case kebab-case")
            return false
        }
        registrations[type]?.let { existing ->
            warn("Storage provider factory $className of $source is refused, '$type' is already registered by ${existing.source} (${existing.className})")
            return false
        }

        registrations[type] = Registration(factory, source, className)
        return true
    }

    fun resolve(type: String): StorageProviderFactory<*, *>? =
        registrations[type]?.factory

    fun settingsType(type: String): Class<out StorageProviderSettings>? =
        registrations[type]?.factory?.settingsType

    fun types(): List<String> =
        registrations.keys.toList()

    private fun sourceOf(factory: Any, builtIn: Boolean): String =
        if (builtIn) {
            "application class path"
        } else {
            runCatching { File(factory.javaClass.protectionDomain.codeSource.location.toURI()).name }
                .getOrDefault("plugin class path")
        }

    companion object {

        private val ID_PATTERN = Regex("^[a-z][a-z0-9-]*$")

        /** A registry that holds the built-in factories of the application class path and nothing else. */
        fun applicationClassPath(warn: (String) -> Unit = {}): StorageProviderRegistry =
            StorageProviderRegistry(warn).also { it.discover(StorageProviderFactory::class.java.classLoader, builtIn = true) }

    }

}
