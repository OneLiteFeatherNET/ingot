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

package com.reposilite.plugin

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.EventListener
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposilitePlugin.ReposilitePluginAccessor
import com.reposilite.storage.StorageProviderRegistry
import net.onelitefeather.ingot.plugin.description.PluginDescriptionNormaliser
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import net.onelitefeather.ingot.plugin.tier.PluginTierResolver
import kotlin.reflect.full.findAnnotation

data class PluginEntry(
    val metadata: Plugin,
    val plugin: ReposilitePlugin
)

class Extensions(private val journalist: Journalist) : Journalist {

    /** The storage providers of the built-in factories, extended by the plugin class loaders before any plugin initialises. */
    internal val storageProviders: StorageProviderRegistry = StorageProviderRegistry.applicationClassPath { journalist.logger.warn(it) }

    private val plugins: MutableMap<String, PluginEntry> = mutableMapOf()
    /** The origin of each registered plugin by plugin id. It is kept here, not in [PluginEntry], which is public API. */
    private val origins: MutableMap<String, PluginOrigin> = mutableMapOf()
    private val facades: MutableList<Facade> = mutableListOf()
    private val events: MutableMap<Class<*>, MutableList<EventListener<Event>>> = mutableMapOf()

    /** Registers a plugin without an origin. It reads as coming from the plugins directory, which never grants Core. */
    fun registerPlugin(plugin: ReposilitePlugin) {
        registerPlugin(plugin, PluginOrigin.PLUGINS_DIRECTORY)
    }

    fun registerPlugin(plugin: ReposilitePlugin, origin: PluginOrigin) {
        val pluginEntry = plugin::class.findAnnotation<Plugin>()
            ?.let { PluginEntry(it, plugin) }
            ?.also { ReposilitePluginAccessor.injectExtension(it.plugin, this) }
            ?: error("Plugin ${plugin::class} does not have @Plugin annotation")

        val name = pluginEntry.metadata.name
        check(!plugins.containsKey(name)) { "Plugin $name is already registered" }
        plugins[name] = pluginEntry
        origins[name] = origin

        if (PluginDescriptionNormaliser.normalise(pluginEntry.metadata.description).cut) {
            val limit = PluginDescriptionNormaliser.LIMIT
            logger.warn("Description of plugin $name is longer than $limit characters and is cut to $limit")
        }
    }

    /**
     * The tier that the start log and the support information show for the plugin with this id. A name that is not registered
     * reads as the plugins directory with the default tier, so this never fails and never grants Core without an origin.
     */
    /** The origin of the plugin with this id. A name that is not registered reads as the plugins directory, which never grants Core. */
    fun originOf(name: String): PluginOrigin =
        origins[name] ?: PluginOrigin.PLUGINS_DIRECTORY

    fun effectiveTierOf(name: String): PluginTier =
        PluginTierResolver.resolve(
            origins[name] ?: PluginOrigin.PLUGINS_DIRECTORY,
            plugins[name]?.metadata?.tier ?: PluginTier.COMMUNITY,
        )

    /**
     * The description of the plugin with this id, as the stored plain text. A name that is not registered, and a plugin without a
     * description, read as the empty text, so this never fails.
     */
    fun descriptionOf(name: String): String =
        plugins[name]
            ?.let { PluginDescriptionNormaliser.normalise(it.metadata.description).text }
            ?: ""

    fun registerFacade(facade: Facade) {
        facades.add(facade)
    }

    inline fun <reified EVENT : Event> registerEvent(listener: EventListener<EVENT>) =
        registerEvent(EVENT::class.java, listener)

    fun <EVENT : Event> registerEvent(eventClass: Class<in EVENT>, listener: EventListener<EVENT>) {
        val listeners = events.computeIfAbsent(eventClass) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        listeners.add(listener as EventListener<Event>)
        listeners.sortBy { it.priority() }
    }

    fun <E : Event> emitEvent(event: E): E {
        events[event.javaClass]?.forEach { it.onCall(event) }
        return event
    }

    inline fun <reified F : Facade> facade(): F =
        facade(F::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <F : Facade> facade(type: Class<F>): F =
        getFacades()
            .find { type.isInstance(it) } as? F
            ?: throw IllegalStateException("Facade ${type.name} not found")

    fun getFacades(): Collection<Facade> =
        facades.toList()

    fun getPlugins(): Map<String, PluginEntry> =
        plugins.toMap()

    override fun getLogger(): Logger =
        journalist.logger

}
