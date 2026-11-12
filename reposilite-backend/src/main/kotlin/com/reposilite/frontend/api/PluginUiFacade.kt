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
package com.reposilite.frontend.api

import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.settingsName
import com.reposilite.journalist.Logger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import java.nio.charset.StandardCharsets.UTF_8

/**
 * The extension point for dashboard pages. A plugin obtains a registrar for itself with [forPlugin] and registers
 * pages and settings sections through it. Invalid registrations are dropped with a warning that names the plugin,
 * so a mistake in one page never stops the server or the other pages.
 *
 * Plugins that never call this facade see no change.
 */
class PluginUiFacade internal constructor(
    private val logger: Logger,
    private val enabled: () -> Boolean,
    /** The switch `INGOT_LOCAL_PLUGINSTYLES`. It is read on every request, so a change applies without a restart. */
    private val stylesheetsEnabled: () -> Boolean = { true },
) : Facade {

    private val lock = Any()
    private val pagesByPlugin = linkedMapOf<String, MutableList<PluginUiPage>>()
    private val sectionsByPlugin = linkedMapOf<String, MutableList<PluginUiManifestSection>>()
    private val stylesheetsByPlugin = linkedMapOf<String, PluginStylesheet>()
    private val stylesheetCallers = mutableSetOf<String>()

    /**
     * The registrar of [plugin]. The plugin name and its own settings domain are read from its `@Plugin` annotation,
     * so a plugin registers under its own name only. Its stylesheets are read from its own class loader.
     */
    fun forPlugin(plugin: ReposilitePlugin): PluginUiRegistrar =
        forPlugin(plugin) { name -> plugin.javaClass.classLoader.getResourceAsStream(name)?.use { it.readBytes() } }

    internal fun forPlugin(plugin: ReposilitePlugin, readResource: (String) -> ByteArray?): PluginUiRegistrar {
        val annotation = plugin.javaClass.getAnnotation(Plugin::class.java)
            ?: error("${plugin.javaClass.name} has no @Plugin annotation")
        val ownDomain = annotation.settings.java.takeUnless { it == SharedSettings::class.java }?.let { settingsName(it) }
        if (ownDomain == PluginUiModel.RESERVED_DOMAIN) {
            logger.warn("Plugin UI | plugin '${annotation.name}' has a settings domain named '$ownDomain', which collides with the dashboard address /settings/$ownDomain/...")
        }
        return PluginUiRegistrar(this, annotation.name, ownDomain, readResource)
    }

    internal fun addPage(plugin: String, page: PluginUiPage): Boolean = synchronized(lock) {
        val existing = pagesByPlugin[plugin].orEmpty()
        val problems = buildList {
            addAll(PluginUiValidator.pageProblems(plugin, page))
            if (existing.size >= PluginUiModel.MAX_PAGES) {
                add("plugin '$plugin' already has ${existing.size} pages, a plugin takes at most ${PluginUiModel.MAX_PAGES} pages")
            }
            if (existing.any { it.id == page.id }) add("plugin '$plugin' already registered a page with id '${page.id}'")
            PluginUiValidator.manifestProblem(plugin, existing + page)?.let(::add)
        }
        if (problems.isNotEmpty()) {
            logger.warn("Plugin UI | page '${page.id}' of plugin '$plugin' is dropped: ${problems.joinToString("; ")}")
            return false
        }
        pagesByPlugin.getOrPut(plugin) { mutableListOf() }.add(page)
        true
    }

    internal fun addSection(plugin: String, ownDomain: String?, section: PluginUiSection): Boolean = synchronized(lock) {
        val problems = buildList {
            if (section.domain != ownDomain) {
                add("the settings domain '${section.domain}' is not a domain of plugin '$plugin', which owns ${ownDomain?.let { "'$it'" } ?: "no domain"}")
            }
            addAll(PluginUiValidator.sectionProblems(plugin, section))
            if (sectionsByPlugin[plugin].orEmpty().any { it.domain == section.domain }) {
                add("plugin '$plugin' already placed the settings domain '${section.domain}'")
            }
        }
        if (problems.isNotEmpty()) {
            logger.warn("Plugin UI | settings section of plugin '$plugin' is dropped: ${problems.joinToString("; ")}")
            return false
        }
        sectionsByPlugin.getOrPut(plugin) { mutableListOf() }
            .add(PluginUiManifestSection(plugin, section.domain, section.label, section.group))
        true
    }

    /**
     * The manifest for one caller. [canOpen] answers for an admin area (`*` for the full instance administrator).
     * Pages and sections the caller may not open are left out, so an unauthorised caller does not learn they exist.
     * Empty while the switch is off, but [enabledPlugins] is always reported.
     */
    internal fun manifest(canOpen: (String) -> Boolean, enabledPlugins: List<String>): PluginUiManifest {
        if (!enabled()) return PluginUiManifest(pages = emptyList(), sections = emptyList(), enabledPlugins = enabledPlugins)
        val (pages, sections, stylesheets) = synchronized(lock) {
            Triple(
                pagesByPlugin.flatMap { (plugin, list) -> list.map { PluginUiManifestPage.of(plugin, it) } },
                sectionsByPlugin.values.flatten(),
                stylesheetsByPlugin.values.toList(),
            )
        }
        return PluginUiManifest(
            pages = pages.filter { canOpen(it.area) },
            sections = if (canOpen("settings")) sections else emptyList(),
            enabledPlugins = enabledPlugins,
            stylesheets = if (stylesheetsEnabled()) {
                stylesheets.map { PluginUiManifestStylesheet(plugin = it.plugin, path = it.path, sha256 = it.sha256) }
            } else {
                emptyList()
            },
        )
    }

    /** Adds the stylesheet [resource] of [plugin], read with [readResource]. Returns false when it was dropped. */
    internal fun addStylesheet(plugin: String, resource: String, readResource: (String) -> ByteArray?): Boolean = synchronized(lock) {
        if (!stylesheetCallers.add(plugin)) {
            logger.warn("Plugin UI | stylesheet '$resource' of plugin '$plugin' is dropped: a plugin takes one stylesheet, and it already registered one")
            return false
        }
        val bytes = runCatching { readResource(resource) }.getOrNull()
        if (bytes == null) {
            logger.warn("Plugin UI | stylesheet '$resource' of plugin '$plugin' is dropped: the plugin has no such resource")
            return false
        }
        val problems = PluginStylesheetValidator.problems(plugin, bytes)
        if (problems.isNotEmpty()) {
            logger.warn("Plugin UI | stylesheet '$resource' of plugin '$plugin' is dropped: ${problems.joinToString("; ")}")
            return false
        }
        stylesheetsByPlugin[plugin] = PluginStylesheet(plugin, PluginStylesheetValidator.wrap(String(bytes, UTF_8)).toByteArray(UTF_8))
        true
    }

    /** The stylesheet of [plugin] whose hash is [hash], or null. Null while either switch is off. */
    internal fun stylesheet(plugin: String, hash: String): PluginStylesheet? {
        if (!enabled() || !stylesheetsEnabled()) return null
        return synchronized(lock) { stylesheetsByPlugin[plugin]?.takeIf { it.hash == hash } }
    }

    /** The areas that guard the data routes of [plugin]. Empty for a plugin without a page, or while the switch is off. */
    internal fun areasGuarding(plugin: String): Set<String> {
        if (!enabled()) return emptySet()
        return synchronized(lock) { pagesByPlugin[plugin].orEmpty().map { it.area }.toSet() }
    }

    /** Whether the switch is on. The route guard is installed only when it is. */
    internal fun isEnabled(): Boolean = enabled()
}

/**
 * Registers the pages and settings sections of one plugin. Created by [PluginUiFacade.forPlugin].
 */
class PluginUiRegistrar internal constructor(
    private val facade: PluginUiFacade,
    /** The name of the registering plugin, the first segment below `/api/plugins/`. */
    val plugin: String,
    private val ownDomain: String?,
    private val readResource: (String) -> ByteArray?,
) {

    /** Adds a page. Returns false when the page was dropped; the reason is in the log. */
    fun registerPage(page: PluginUiPage): Boolean =
        facade.addPage(plugin, page)

    /** Gives one of the plugin's own settings domains a label and a menu group. Returns false when it was dropped. */
    fun registerSettingsSection(section: PluginUiSection): Boolean =
        facade.addSection(plugin, ownDomain, section)

    /**
     * Registers the stylesheet [resource] of the plugin, a CSS file in the plugin's jar. The sheet is checked now, and a
     * sheet that fails is dropped with a warning. A plugin takes one call: a second one is dropped, and so is a first
     * one that failed. Returns false when the sheet was dropped.
     */
    fun registerStylesheet(resource: String): Boolean =
        facade.addStylesheet(plugin, resource, readResource)
}
