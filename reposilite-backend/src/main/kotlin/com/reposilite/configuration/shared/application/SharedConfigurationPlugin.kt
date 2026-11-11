package com.reposilite.configuration.shared.application

import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SettingsSecretReferences
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedConfigurationReload
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.infrastructure.SettingsDecryptCommand
import com.reposilite.configuration.shared.infrastructure.SettingsEndpoints
import com.reposilite.configuration.shared.infrastructure.SettingsReencryptCommand
import com.reposilite.console.api.CommandsSetupEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.parameters
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.settings.encryption.KeyStartOutcome
import net.onelitefeather.ingot.settings.encryption.SettingsKeyConfiguration
import net.onelitefeather.ingot.settings.encryption.SettingsKeyGuard
import net.onelitefeather.ingot.settings.encryption.SettingsKeyRecords
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import java.util.concurrent.TimeUnit

@Plugin(name = "shared-configuration", dependencies = ["failure", "configuration", "local-configuration"])
class SharedConfigurationPlugin : ReposilitePlugin() {

    override fun initialize(): SharedConfigurationFacade {
        val localConfiguration = facade<LocalConfiguration>()
        val settingsEnvironment = parameters().settingsSecretEnvironment ?: System.getenv()
        val keyConfiguration = SettingsKeySources.read(settingsEnvironment)
        val secretCipher = SettingsSecretCipher.of(keyConfiguration)

        val sharedConfigurationFacade = SharedConfigurationComponents(
            journalist = this,
            workingDirectory = parameters().workingDirectory,
            extensions = extensions(),
            sharedConfigurationPath = parameters().sharedConfigurationPath,
            failureFacade = facade(),
            configurationFacade = facade(),
            sharedConfigurationReload = parameters().sharedConfigurationReload ?: SharedConfigurationReload.read(),
            secretReferences = SettingsSecretReferences.read(settingsEnvironment),
            secretCipher = secretCipher,
        ).sharedConfigurationFacade()

        logger.info("")
        logger.info("--- Shared settings")
        logger.info("Loading shared configuration from ${sharedConfigurationFacade.getProviderName()}")
        val keyRecords = SettingsKeyRecords(facade<ConfigurationFacade>())
        checkSettingsKey(sharedConfigurationFacade, keyRecords, keyConfiguration)

        val storedConfiguration = sharedConfigurationFacade.fetchConfiguration()
        val loadResult = sharedConfigurationFacade.loadSharedSettingsFromString(storedConfiguration)

        if (loadResult.isErr && !parameters().ignoreSharedConfigurationErrors) {
            logger.error("Failed to load shared configuration from '${sharedConfigurationFacade.getProviderName()}' provider.")
            logger.error("Please check your configuration and try again.")
            logger.error("If you want to ignore those errors and let Ingot start with default settings as a fallback values,")
            logger.error("please launch Ingot with --ignore-shared-configuration-errors' flag.")
            throw loadResult.error
        }

        // A database that still holds plain secrets is stored again with envelopes, once, when a key is configured. A managed
        // file is never rewritten at start, its values are sealed by the next save.
        if (loadResult.isOk && secretCipher.sealing && sharedConfigurationFacade.isMutable() && sharedConfigurationFacade.plaintextSecretCount() > 0) {
            val sealed = sharedConfigurationFacade.sealStoredSecrets(reason = "first-start")
            logger.info("Settings encryption | Sealed ${sealed ?: 0} stored secret values with key id ${secretCipher.activeKeyId}")
        }

        if (sharedConfigurationFacade.isMutable() || sharedConfigurationFacade.isExternallyManaged()) {
            val watcher = reposilite().scheduler.scheduleWithFixedDelay({
                sharedConfigurationFacade.reloadIfChanged()
            }, 10, 10, TimeUnit.SECONDS)

            event { _: ReposiliteDisposeEvent ->
                watcher.cancel(false)
            }
        }

        if (localConfiguration.debugEnabled.get()) {
            sharedConfigurationFacade.getDomainNames()
                .map { sharedConfigurationFacade.getSettingsReference<SharedSettings>(it) }
                .forEach {
                    logger.debug("Schema for ${it?.name}:")
                    logger.debug(it?.schema?.get()?.reader()?.readText())
                }
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(SettingsEndpoints(sharedConfigurationFacade, localConfiguration.hideSettingsSecrets, facade()))
        }

        event { event: CommandsSetupEvent ->
            event.registerCommand(SettingsReencryptCommand(sharedConfigurationFacade, keyRecords, secretCipher))
            event.registerCommand(SettingsDecryptCommand(sharedConfigurationFacade, keyRecords, secretCipher))
        }

        return sharedConfigurationFacade
    }

    /**
     * Compares the configured key with the key record of the database before anything is loaded. A new database adopts the key.
     *
     * @throws IllegalStateException when the key does not match the stored settings, see [SettingsKeyGuard]
     */
    private fun checkSettingsKey(
        sharedConfigurationFacade: SharedConfigurationFacade,
        keyRecords: SettingsKeyRecords,
        configuration: SettingsKeyConfiguration,
    ) {
        var outcome = SettingsKeyGuard.verify(configuration, keyRecords.read(), sharedConfigurationFacade.storedSealedSettings())
        if (outcome is KeyStartOutcome.Adopt) {
            // Another instance may adopt a key in the same moment: the record that was stored first is the one that counts.
            val stored = keyRecords.adopt(outcome.record)
            outcome = SettingsKeyGuard.verify(configuration, stored, sharedConfigurationFacade.storedSealedSettings())
        }

        when (outcome) {
            is KeyStartOutcome.AcceptedPreviousKey -> logger.warn(
                "Settings encryption | The stored settings are sealed with the previous key (key id ${outcome.stored.keyId}). " +
                    "A key rotation is open, run settings-reencrypt after every replica has the new key."
            )
            is KeyStartOutcome.Adopt, KeyStartOutcome.Accepted -> logger.info("Settings encryption | Settings key accepted")
            KeyStartOutcome.Unkeyed -> logger.info("Settings encryption | No settings key is configured, secrets are stored as before")
        }
    }

}
