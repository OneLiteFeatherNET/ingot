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

package com.reposilite

import com.reposilite.storage.s3.S3StorageProviderSettings
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Integrations used in remote stack:
 * - MariaDB, one database per test
 * - AWS S3 through Floci, one bucket per test
 *
 * The containers are shared by the whole test JVM (see [SharedContainers]); this extension only
 * creates and drops what belongs to a single test method.
 */
internal class RecommendedRemoteSpecificationJunitExtension : Extension, BeforeEachCallback, AfterEachCallback {

    private val namespace = ExtensionContext.Namespace.create(RecommendedRemoteSpecificationJunitExtension::class.java)

    override fun beforeEach(context: ExtensionContext) {
        val mariaDb = SharedContainers.mariaDb
        val floci = SharedContainers.floci

        val database = IsolatedResources.createMariaDbDatabase(mariaDb)
        val bucket = IsolatedResources.newBucketName("test-repository")
        context.getStore(namespace).put(DATABASE_KEY, database)

        context.also {
            val instance = it.requiredTestInstance
            val type = instance::class.java

            type.getField("_extensionInitialized").set(instance, true)
            type.getField("_database").set(instance, mariaDb.databaseToken(database))
            type.getField("_storageProvider").set(
                instance,
                S3StorageProviderSettings(
                    bucketName = bucket,
                    endpoint = floci.s3Endpoint(),
                    accessKey = "test",
                    secretKey = "test",
                    region = "us-east-1",
                    sharedBucket = true
                )
            )
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val database = context.getStore(namespace).remove(DATABASE_KEY, String::class.java)
        if (database != null) {
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, database)
        }
    }

    private companion object {
        const val DATABASE_KEY = "database"
    }

}
