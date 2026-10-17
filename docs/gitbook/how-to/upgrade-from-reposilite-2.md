---
description: Import the access tokens of a Reposilite 2.x installation into Ingot 1.2.0 with the migration plugin and the token-import command.
---

# Upgrade from Reposilite 2.x

Reposilite 2.x keeps its tokens in `tokens.dat`, which Ingot cannot read directly. The `migration-plugin` converts that file, and the `token-import` command loads the result. The plugin reads only `tokens.dat`, so this procedure imports tokens and nothing else from 2.x.

{% hint style="warning" %}
The container upgrade tests do not cover this path. The steps below come from the source of the migration plugin and the `token-import` command.
{% endhint %}

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the 2.x data directory, which holds `tokens.dat`.
* The `migration-plugin` jar. The Ingot image does not contain it. The jar is built from the source in the Ingot repository, in the project `:reposilite-plugins:migration-plugin`, with the Gradle task `shadowJar`. The build writes it to `reposilite-test/workspace/plugins/migration-plugin.jar`. No released copy of the jar was found for Ingot, so check where your copy comes from before you use it.
* The image `ghcr.io/onelitefeathernet/ingot:1.2.0` as the server you start in step 2.

## Steps

1. Copy the `migration-plugin` jar into the `plugins` directory of the data directory.

2. Start the server on the data directory that holds `tokens.dat`. The plugin writes `tokens.json` next to `tokens.dat` and logs the next step.

3. In the console of the running server, import the tokens.

   ```
   token-import tokens.json
   ```

4. Check the tokens as described in [Verify an upgrade](verify-an-upgrade.md).

5. Stop the server, remove the `migration-plugin` jar from the `plugins` directory, and start the server again.

## Result

`token-import` prints one line per token, `Access token '<name>' has been imported.`. Each token is created as a machine principal. The principal list from [Verify an upgrade](verify-an-upgrade.md) shows them, and the `migration-plugin` jar is gone from the `plugins` directory.
