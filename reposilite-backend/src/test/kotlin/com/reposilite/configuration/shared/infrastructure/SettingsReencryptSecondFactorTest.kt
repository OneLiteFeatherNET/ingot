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

package com.reposilite.configuration.shared.infrastructure

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteJournalist
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedConfigurationProvider
import com.reposilite.configuration.shared.SharedSettingsProvider
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.console.CommandContext
import com.reposilite.console.CommandStatus
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import java.nio.file.Path
import java.time.Instant
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorSeedResealer
import net.onelitefeather.ingot.identity.login.mfa.SettingsSeedCipher
import net.onelitefeather.ingot.identity.login.storage.FactorRow
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEncryptionMode
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsKeyRecords
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.mutableReference

/**
 * The settings-reencrypt command against the second factor seeds of a database. The document holds no secret, so every count in the
 * output belongs to the seeds. Each test builds its own database, keys and facade.
 */
internal class SettingsReencryptSecondFactorTest {

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(@get:Sensitive val secret: String = "default") : SharedSettings

    private class MemoryProvider(var content: String) : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String = content

        override fun isUpdateRequired(): Boolean = false

        override fun isMutable(): Boolean = true

        override fun name(): String = "memory"
    }

    private val oldKey = ByteArray(32) { (it * 5 + 2).toByte() }
    private val newKey = ByteArray(32) { (it * 11 + 4).toByte() }
    private val unknownKey = ByteArray(32) { (it * 13 + 6).toByte() }
    private val seed = ByteArray(32) { (it * 3 + 1).toByte() }
    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val journalist = InMemoryLogger()
    private val reporter = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false)

    @TempDir
    lateinit var workingDirectory: Path

    private fun codec(key: ByteArray) = SettingsEnvelopeCodec(key = key)

    private fun cipher(active: ByteArray?, previous: ByteArray?) = SettingsSecretCipher(
        active = active?.let { codec(it) },
        previous = previous?.let { codec(it) },
        mode = SettingsEncryptionMode.OPTIONAL,
    )

    private fun facade(provider: MemoryProvider, cipher: SettingsSecretCipher) =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretCipher = cipher,
        )

    private fun records() = SettingsKeyRecords(ConfigurationFacade(InMemoryConfigurationRepository()))

    private fun context() = CommandContext(reporter)

    private class Stores(val database: Database, val identities: IdentityStore, val factors: SecondFactorStore)

    private fun withStores(block: (Stores) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(Stores(connection.database, IdentityStore(connection.database, clock), SecondFactorStore(connection.database, clock)))
        }
    }

    /** Stores a confirmed authenticator whose seed is sealed with [key], as an instance that had [key] active would have. */
    private fun storeConfirmedSeed(stores: Stores, key: ByteArray): FactorRow {
        val principalId = stores.identities.createPrincipal(PrincipalKind.USER, "alice", "alice", false, clock.instant())!!.id
        val envelope = SettingsSeedCipher(codecs = { listOf(codec(key)) }).seal("factor-alice", seed)
        stores.factors.insertPendingTotp(principalId, "factor-alice", "Authenticator", envelope, clock.instant())
        val pending = checkNotNull(stores.factors.findPendingTotp(principalId))
        stores.factors.confirm(pending.id, clock.instant(), 0)
        return stores.factors.findFactors(principalId).single()
    }

    private fun SettingsSecretCipher.seeds() = SettingsSeedCipher(codecs = { codecs() })

    @Test
    fun `settings-reencrypt re-seals a second factor seed of the previous key and reports the count`() {
        withStores { stores ->
            // given: a seed sealed with the previous key, and the new key active
            val row = storeConfirmedSeed(stores, oldKey)
            val cipher = cipher(active = newKey, previous = oldKey)
            val facade = facade(MemoryProvider("{}"), cipher)
            facade.registerSealedSecretStore(SecondFactorSeedResealer(stores.factors, cipher.seeds()))

            // when
            val context = context()
            SettingsReencryptCommand(facade, records(), cipher).execute(context)

            // then: the command succeeds, reports the count, and the seed opens with the new key alone
            assertThat(context.status).describedAs("status").isEqualTo(CommandStatus.SUCCEEDED)
            assertThat(context.output().joinToString("\n")).describedAs("output").contains("second factor seeds: 1 re-sealed, 0 already sealed")
            val stored = stores.factors.findFactors(row.principalId).single()
            val alone = cipher(active = newKey, previous = null).seeds()
            assertThat(alone.open(stored.factorKey, checkNotNull(stored.secret))).describedAs("opened seed").isEqualTo(seed)
        }
    }

    @Test
    fun `settings-reencrypt run again reports the seeds as already sealed and writes nothing`() {
        withStores { stores ->
            // given: a seed already re-sealed by a first run
            val row = storeConfirmedSeed(stores, oldKey)
            val cipher = cipher(active = newKey, previous = oldKey)
            val facade = facade(MemoryProvider("{}"), cipher)
            facade.registerSealedSecretStore(SecondFactorSeedResealer(stores.factors, cipher.seeds()))
            SettingsReencryptCommand(facade, records(), cipher).execute(context())
            val afterFirstRun = checkNotNull(stores.factors.findFactors(row.principalId).single().secret)

            // when
            val context = context()
            SettingsReencryptCommand(facade, records(), cipher).execute(context)

            // then
            assertThat(context.output().joinToString("\n")).describedAs("output").contains("second factor seeds: 0 re-sealed, 1 already sealed")
            assertThat(stores.factors.findFactors(row.principalId).single().secret).describedAs("stored seed").isEqualTo(afterFirstRun)
        }
    }

    @Test
    fun `a seed that no configured key opens is named in the output and left unchanged`() {
        withStores { stores ->
            // given: a seed sealed with a key that is not configured any more
            val row = storeConfirmedSeed(stores, unknownKey)
            val cipher = cipher(active = newKey, previous = oldKey)
            val facade = facade(MemoryProvider("{}"), cipher)
            facade.registerSealedSecretStore(SecondFactorSeedResealer(stores.factors, cipher.seeds()))

            // when
            val context = context()
            SettingsReencryptCommand(facade, records(), cipher).execute(context)

            // then: the output names the seed by its factor id, and the row keeps its envelope
            val output = context.output().joinToString("\n")
            assertThat(output).describedAs("output").contains("second factor ${row.id}").contains("left unchanged")
            val stored = stores.factors.findFactors(row.principalId)
            assertThat(stored).describedAs("stored factors").hasSize(1)
            assertThat(stored.single().secret).describedAs("stored seed").isEqualTo(row.secret)
        }
    }

}
