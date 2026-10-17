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

package net.onelitefeather.ingot.search.api

import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Points the first replica of a test at an H2 file database that lives in a directory of its own, so that a second replica
 * can open the same database. The directory is removed after the test.
 */
internal class SharedH2DatabaseJunitExtension : Extension, BeforeEachCallback, AfterEachCallback {

    private val namespace = ExtensionContext.Namespace.create(SharedH2DatabaseJunitExtension::class.java)

    override fun beforeEach(context: ExtensionContext) {
        val directory = Files.createTempDirectory("ingot-replicas")
        context.getStore(namespace).put(DIRECTORY, directory)

        val instance = context.requiredTestInstance
        val type = instance::class.java
        type.getField("_database").set(instance, "h2 ${directory.resolve("search").toAbsolutePath()}")
        type.getField("_storageProvider").set(instance, FileSystemStorageProviderSettings())
        type.getField("_extensionInitialized").set(instance, true)
    }

    override fun afterEach(context: ExtensionContext) {
        context.getStore(namespace).get(DIRECTORY, Path::class.java)?.toFile()?.deleteRecursively()
    }

    private companion object {
        const val DIRECTORY = "directory"
    }

}
