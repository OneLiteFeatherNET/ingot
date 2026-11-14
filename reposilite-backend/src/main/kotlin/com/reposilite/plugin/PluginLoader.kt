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

import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import net.onelitefeather.ingot.plugin.store.PluginStore
import net.onelitefeather.ingot.plugin.store.StoreEntry
import net.onelitefeather.ingot.plugin.store.StoreState
import kotlin.reflect.full.findAnnotation
import panda.std.Result.supplyThrowing
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.jar.JarFile
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.useDirectoryEntries

class PluginLoader(
    val pluginsDirectory: Path,
    val extensions: Extensions,
    /** The jars shipped with the image. Read only when the metrics switch is on. */
    private val bundledPluginsDirectory: Path = Path.of(System.getProperty("ingot.bundledPlugins") ?: DEFAULT_BUNDLED_PLUGINS_DIRECTORY),
    /** The environment the switch is read from. Injected so that tests do not change the process environment. */
    private val environment: Map<String, String> = System.getenv(),
    /** The plugin store. Its plugins load after the plugins directory and before the bundled jars. Null when no store is configured. */
    private val pluginStore: PluginStore? = null,
) {

    private val storeStates: MutableList<StoreEntry> = mutableListOf()
    private val storePluginIds: MutableSet<String> = mutableSetOf()

    /** The outcome of every plugin that the store selected, in selection order, as the status of the store shows it. */
    val storeEntries: List<StoreEntry>
        get() = storeStates.toList()

    fun initialize() {
        loadPlugins()

        val plugins = sortPlugins()

        plugins.chunked(5).forEach {
            extensions.logger.info(it.joinToString(", ", transform = { (metadata, _) -> metadata.name }))
        }

        initializePlugins(plugins)
    }

    /**
     * Initialises the given plugins in order. A store plugin whose initialise throws is marked as failed and the start goes on,
     * while any other failure stops the start as before.
     */
    internal fun initializePlugins(plugins: List<PluginEntry>) {
        plugins.forEach { (metadata, plugin) ->
            try {
                plugin.initialize()?.apply { extensions.registerFacade(this) }
            } catch (exception: Exception) {
                if (metadata.name !in storePluginIds) {
                    throw exception
                }
                markStoreFailure(metadata.name, exception)
            }
            System.gc() // startup is heavy & reflective operation, so we'd like to tell jvm to take a look on that
        }
    }

    /**
     * Calls [ReposilitePlugin.load] on every plugin, then registers the storage providers of the plugin jars. Nothing is initialised here,
     * so repositories built during initialisation see every provider.
     */
    internal fun loadPlugins() {
        extensions.getPlugins()
            .values
            .forEach { (_, plugin) -> plugin.load(this) }

        pluginClassLoaders.forEach { extensions.storageProviders.discover(it, builtIn = false) }
    }

    /** The class loaders of the plugin jars, in the order they were opened. Their storage factories are discovered in [loadPlugins]. */
    private val pluginClassLoaders: MutableList<ClassLoader> = mutableListOf()

    private fun sortPlugins(): List<PluginEntry> =
        with (extensions.getPlugins()) {
            extensions.getPlugins().asSequence()
                .map { it.value.metadata }
                .sortedBy { it.name }
                .associateBy({ it.name }, { it.dependencies.toList() })
                .let { toFlattenedDependencyGraph(it) }
                .mapNotNull { this[it] }
        }

    internal fun loadPluginsByServiceFiles() {
        if (pluginsDirectory.notExists()) {
            pluginsDirectory.createDirectories()
        }

        check(pluginsDirectory.isDirectory()) { "The path is not a directory" }
        extensions.logger.debug("Plugins directory: ${pluginsDirectory.absolute()}")

        pluginsDirectory.useDirectoryEntries { pluginDirectoryStream ->
            pluginDirectoryStream
                .filter { it.extension == "jar" }
                .filter { isValidJarFile(it.toFile()) }
                .map { it.toUri().toURL() }
                .toList()
                .onEach { extensions.logger.debug("Plugin file: $it") }
                .let { URLClassLoader(it.toTypedArray()).also(pluginClassLoaders::add) }
                .let { ServiceLoader.load(ReposilitePlugin::class.java, it) }
                .onEach { extensions.logger.debug("Plugin class: $it") }
                .forEach { extensions.registerPlugin(it) }
        }

        loadStorePlugins()
        loadBundledPlugins()
    }

    /**
     * Loads the verified jars of the plugin store, after the plugins directory and before the bundled jars. A plugin id that a
     * jar of the plugins directory already holds shadows the store plugin. A store plugin that the bundled jars would replace
     * wins over them, because the bundled jars are skipped when their id is registered. Nothing here stops the start: an
     * entry that cannot be loaded is reported in [storeEntries] and the start goes on.
     */
    private fun loadStorePlugins() {
        val store = pluginStore ?: return
        val resolved = try {
            store.resolve()
        } catch (exception: Exception) {
            extensions.logger.error("Plugin store could not be resolved, the server starts without store plugins: ${exception.message}")
            return
        }

        resolved.forEach { entry ->
            val outcome = if (entry.loadable) loadStoreJar(entry) else entry
            storeStates += outcome
            logStoreEntry(outcome)
        }
    }

    private fun loadStoreJar(entry: StoreEntry): StoreEntry {
        val jar = requireNotNull(entry.jar) { "A loadable store entry has a jar" }
        val declared = try {
            declaredStorePlugins(jar)
        } catch (exception: Exception) {
            return entry.copy(state = StoreState.REFUSED, reason = "the jar cannot be loaded: ${exception.message}")
        } catch (linkage: LinkageError) {
            return entry.copy(state = StoreState.REFUSED, reason = "the jar cannot be loaded: ${linkage.message}")
        }

        if (declared.size != 1) {
            return entry.copy(state = StoreState.REFUSED, reason = "the jar must declare exactly one plugin, it declares ${declared.size}")
        }
        val plugin = declared.single()
        val name = plugin::class.findAnnotation<Plugin>()?.name
            ?: return entry.copy(state = StoreState.REFUSED, reason = "the plugin class has no @Plugin annotation")

        val expected = entry.pluginId
        if (name != expected) {
            return entry.copy(
                state = StoreState.REFUSED,
                reason = "the jar declares the plugin id $name but its catalogue entry names $expected, so the jar is refused",
            )
        }
        if (extensions.getPlugins().containsKey(name)) {
            return entry.copy(
                state = StoreState.SHADOWED,
                reason = "a jar in the plugins directory has the same plugin id, so the store plugin is not loaded",
            )
        }

        extensions.registerPlugin(plugin)
        storePluginIds += name
        return entry
    }

    /**
     * The plugins that the jar declares in its own service file. A service lookup through the loader would also return the
     * built-in plugins of the parent loader, so the names are read from the jar and each class is instantiated from it.
     */
    private fun declaredStorePlugins(jar: Path): List<ReposilitePlugin> {
        val names = JarFile(jar.toFile()).use { jarFile ->
            val service = jarFile.getEntry("META-INF/services/${ReposilitePlugin::class.java.name}") ?: return emptyList()
            jarFile.getInputStream(service).bufferedReader().use { reader ->
                reader.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
            }
        }
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), ReposilitePlugin::class.java.classLoader)
        return names.map { name ->
            Class.forName(name, true, loader).getDeclaredConstructor().newInstance() as ReposilitePlugin
        }
    }

    private fun markStoreFailure(name: String, exception: Exception) {
        val index = storeStates.indexOfFirst { it.pluginId == name && it.loadable }
        if (index < 0) {
            return
        }
        val failed = storeStates[index].copy(state = StoreState.FAILED, reason = "initialize failed: ${exception.message}")
        storeStates[index] = failed
        extensions.logger.warn("Store plugin $name failed in initialize and is marked as failed. Routes it registered before the failure stay until the next start.")
        logStoreEntry(failed)
    }

    private fun logStoreEntry(entry: StoreEntry) {
        val id = entry.pluginId ?: entry.coordinate.toString()
        when (entry.state) {
            StoreState.LOADED, StoreState.CACHED -> extensions.logger.info("Plugin store: $id ${entry.state} from ${entry.source}")
            StoreState.UNVERIFIED -> extensions.logger.warn("Plugin store: $id is UNVERIFIED, its signature is not checked because unsigned content is allowed")
            else -> extensions.logger.warn("Plugin store: $id ${entry.state}, ${entry.reason}")
        }
    }

    /**
     * Loads the jars of [bundledPluginsDirectory] after the plugins directory. Each jar follows the switch of its own entry in
     * [BUNDLED_SWITCHES]; a jar the table does not know follows the metrics switch, as every bundled jar did before the table
     * existed. A plugin whose id is already registered, by a jar of the plugins directory or by a built-in plugin, is skipped,
     * so the operator's own copy keeps working and start-up does not fail on the duplicate id.
     */
    private fun loadBundledPlugins() {
        if (bundledPluginsDirectory.notExists() || !bundledPluginsDirectory.isDirectory()) {
            extensions.logger.debug("Bundled plugins directory $bundledPluginsDirectory does not exist, nothing to load")
            return
        }

        val jars = bundledPluginsDirectory.useDirectoryEntries { entries ->
            entries
                .filter { it.extension == "jar" }
                .filter { isBundledJarSwitchedOn(it.fileName.toString()) }
                .filter { isValidJarFile(it.toFile()) }
                .map { it.toUri().toURL() }
                .toList()
        }

        if (jars.isEmpty()) {
            return
        }

        val bundledLoader = URLClassLoader(jars.toTypedArray(), ReposilitePlugin::class.java.classLoader)
        pluginClassLoaders.add(bundledLoader)
        ServiceLoader.load(ReposilitePlugin::class.java, bundledLoader)
            .forEach { plugin ->
                val name = plugin::class.findAnnotation<Plugin>()?.name
                    ?: error("Bundled plugin ${plugin::class} does not have @Plugin annotation")

                if (extensions.getPlugins().containsKey(name)) {
                    extensions.logger.debug("Bundled plugin $name is not loaded, a plugin with the same id is already registered")
                } else {
                    extensions.registerPlugin(plugin)
                }
            }
    }

    private fun isBundledJarSwitchedOn(fileName: String): Boolean =
        (BUNDLED_SWITCHES[fileName] ?: METRICS_SWITCH_OF_JAR).isOn(environment)

    private fun isValidJarFile(file: File): Boolean =
        supplyThrowing { JarFile(file) }
            .map { it.close() }
            .onError { extensions.logger.warn("Invalid JAR file: ${file.absolutePath}") }
            .isOk

    /**
     * The switch of one bundled jar. The `INGOT_` name wins whenever it is set, even to `false`; the `REPOSILITE_` name is the
     * fallback for installations that predate the rename.
     */
    class BundledSwitch(private val ingotName: String, private val legacyName: String) {
        fun isOn(environment: Map<String, String>): Boolean {
            val current = environment[ingotName]?.trim()
            if (current != null) {
                return current.equals("true", ignoreCase = true)
            }
            return environment[legacyName]?.trim().equals("true", ignoreCase = true)
        }
    }

    companion object {
        const val DEFAULT_BUNDLED_PLUGINS_DIRECTORY: String = "/app/bundled-plugins"
        const val METRICS_SWITCH: String = "INGOT_METRICS_ENABLED"
        const val LEGACY_METRICS_SWITCH: String = "REPOSILITE_METRICS_ENABLED"
        const val NEXUS_IMPORT_SWITCH: String = "INGOT_NEXUS_IMPORT_ENABLED"
        const val LEGACY_NEXUS_IMPORT_SWITCH: String = "REPOSILITE_NEXUS_IMPORT_ENABLED"
        const val OPENSEARCH_SWITCH: String = "INGOT_OPENSEARCH_ENABLED"
        const val LEGACY_OPENSEARCH_SWITCH: String = "REPOSILITE_OPENSEARCH_ENABLED"

        private val METRICS_SWITCH_OF_JAR = BundledSwitch(METRICS_SWITCH, LEGACY_METRICS_SWITCH)

        /** The bundled jars by file name and the switch each one follows. */
        val BUNDLED_SWITCHES: Map<String, BundledSwitch> = mapOf(
            "prometheus-plugin.jar" to METRICS_SWITCH_OF_JAR,
            "nexus-import-plugin.jar" to BundledSwitch(NEXUS_IMPORT_SWITCH, LEGACY_NEXUS_IMPORT_SWITCH),
            "opensearch-plugin.jar" to BundledSwitch(OPENSEARCH_SWITCH, LEGACY_OPENSEARCH_SWITCH),
        )
    }

}
