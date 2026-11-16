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

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin

/** The Java release that Apache Lucene 10 is compiled for. The server itself stays on Java 17. */
internal const val LUCENE_MIN_JAVA_FEATURE: Int = 21

/**
 * Returns the message that the plugin logs when the server runs on a Java release older than [LUCENE_MIN_JAVA_FEATURE], or null
 * when the plugin may start. It takes the feature version as a parameter so that both branches can be tested without a
 * second JVM. This file references no Lucene class, so the entry class loads on Java 17.
 */
internal fun luceneRuntimeProblem(feature: Int): String? {
    if (feature >= LUCENE_MIN_JAVA_FEATURE) {
        return null
    }
    return "The Lucene search plugin needs Java $LUCENE_MIN_JAVA_FEATURE or newer, this server runs Java $feature; " +
        "the plugin is not active"
}

/**
 * The entry point of the Lucene search plugin. On a server older than Java 21 it logs one error line and registers nothing, so
 * the rest of the server keeps running. The check runs before anything else in the plugin, so no Lucene class is loaded on
 * Java 17.
 */
@Plugin(name = "lucene-search", dependencies = ["search"])
class LucenePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        luceneRuntimeProblem(Runtime.version().feature())?.let { problem -> logger.error(problem) }
        return null
    }

}
