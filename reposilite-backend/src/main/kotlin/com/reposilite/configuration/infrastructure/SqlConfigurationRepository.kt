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

package com.reposilite.configuration.infrastructure

import com.reposilite.configuration.ConfigurationRepository
import com.reposilite.configuration.VersionedConfiguration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Suppress("RemoveRedundantQualifierName")
internal class SqlConfigurationRepository(private val database: Database) : ConfigurationRepository {

    internal object SettingsTable : Table("settings") {
        val name = varchar("name", 32).uniqueIndex()
        val updateDate = datetime("updateDate")
        val content = text("content")
        // Added after the first release: existing rows start at revision 0, so a writer that read them can still compare and set.
        val revision = long("revision").default(0L)
    }

    init {
        transaction(database) {
            SchemaUtils.create(SettingsTable)
            SchemaUtils.createMissingTablesAndColumns(SettingsTable)
        }
    }

    override fun saveConfiguration(name: String, configuration: String) {
        transaction(database) {
            val updated = SettingsTable.update(where = { SettingsTable.name eq name }, body = {
                it[SettingsTable.updateDate] = now()
                it[SettingsTable.content] = configuration
                it.update(SettingsTable.revision, SettingsTable.revision + 1L)
            })

            if (updated == 0) {
                insertDocument(name, configuration, revision = 1L)
            }
        }
    }

    override fun findConfiguration(name: String): String? =
        transaction(database) {
            findSharedConfigurationContent(name)
        }

    private fun findSharedConfigurationContent(name: String): String? =
        SettingsTable.selectAll()
            .where { SettingsTable.name eq name }
            .map { it[SettingsTable.content] }
            .firstOrNull()

    override fun findConfigurationUpdateDate(name: String): Instant? =
        transaction(database) {
            SettingsTable.selectAll()
                .where { SettingsTable.name eq name }
                .map { it[SettingsTable.updateDate] }
                .map { it.toInstant(ZoneOffset.UTC) }
                .firstOrNull()
        }

    override fun findVersionedConfiguration(name: String): VersionedConfiguration? =
        transaction(database) {
            SettingsTable.selectAll()
                .where { SettingsTable.name eq name }
                .map { VersionedConfiguration(content = it[SettingsTable.content], revision = it[SettingsTable.revision]) }
                .firstOrNull()
        }

    override fun compareAndSaveConfiguration(name: String, expectedRevision: Long?, configuration: String): Boolean {
        if (expectedRevision == null) {
            return saveConfigurationIfAbsent(name, configuration)
        }

        return transaction(database) {
            // A single conditional UPDATE: the revision is compared and advanced in the same statement, so two writers that
            // read the same revision cannot both win, whatever the instances and isolation levels in between.
            SettingsTable.update(
                where = { (SettingsTable.name eq name) and (SettingsTable.revision eq expectedRevision) },
                body = {
                    it[SettingsTable.updateDate] = now()
                    it[SettingsTable.content] = configuration
                    it.update(SettingsTable.revision, SettingsTable.revision + 1L)
                }
            ) == 1
        }
    }

    override fun saveConfigurationIfAbsent(name: String, configuration: String): Boolean =
        try {
            transaction(database) { insertDocument(name, configuration, revision = 1L) }
            true
        } catch (exception: ExposedSQLException) {
            // The unique index on the name rejected the insert: another writer stored the document first.
            false
        }

    private fun insertDocument(name: String, configuration: String, revision: Long) {
        SettingsTable.insert {
            it[SettingsTable.name] = name
            it[SettingsTable.updateDate] = now()
            it[SettingsTable.content] = configuration
            it[SettingsTable.revision] = revision
        }
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)

}
