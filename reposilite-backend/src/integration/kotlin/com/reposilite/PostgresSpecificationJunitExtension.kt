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

package com.reposilite

import com.reposilite.storage.s3.S3StorageProviderSettings
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Integrations used in the PostgreSQL matrix leg:
 * - PostgreSQL, one schema per test
 * - AWS S3 through Floci, one bucket per test (the same storage as [RecommendedRemoteSpecificationJunitExtension])
 *
 * The containers are shared by the whole test JVM (see [SharedContainers]); this extension only
 * creates and drops what belongs to a single test method.
 */
internal class PostgresSpecificationJunitExtension : Extension, BeforeEachCallback, AfterEachCallback {

    private val namespace = ExtensionContext.Namespace.create(PostgresSpecificationJunitExtension::class.java)

    override fun beforeEach(context: ExtensionContext) {
        val postgres = SharedContainers.postgres
        val floci = SharedContainers.floci

        val schema = IsolatedResources.createPostgresSchema(postgres)
        val bucket = IsolatedResources.newBucketName("test-repository")
        context.getStore(namespace).put(SCHEMA_KEY, schema)

        context.also {
            val instance = it.requiredTestInstance
            val type = instance::class.java

            type.getField("_extensionInitialized").set(instance, true)
            type.getField("_database").set(instance, postgres.schemaToken(schema))
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
        val schema = context.getStore(namespace).remove(SCHEMA_KEY, String::class.java)
        if (schema != null) {
            IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
        }
    }

    private companion object {
        const val SCHEMA_KEY = "schema"
    }

}
