---
description: Import the access tokens of a Reposilite 2.x installation into Ingot with the migration plugin and the token-import command.
---

# Upgrade from Reposilite 2.x

Reposilite 2.x keeps its tokens in `tokens.dat`, which Ingot cannot read directly. The `migration-plugin` converts that file, and the `token-import` command loads the result. Statistics from 2.x are not imported.

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the 2.x data directory, which holds `tokens.dat`.
* The `migration-plugin` jar.
* Ingot, the first release that includes the identity model, as the server you start in step 2.

## Steps

1. Copy the `migration-plugin` jar into the `plugins` directory of the data directory.

2. Start the server on the data directory that holds `tokens.dat`. The plugin writes `tokens.json` next to `tokens.dat` and logs the next step.

3. In the console of the running server, import the tokens.

   ```
   token-import tokens.json
   ```

4. Check the tokens as described in [Verify an upgrade](verify-an-upgrade.md).

5. Remove the `migration-plugin` jar.

## Result

`token-import` prints one line per token, `Access token '<name>' has been imported.`. The principal list from [Verify an upgrade](verify-an-upgrade.md) shows each token as a machine principal, and the `migration-plugin` jar is gone from the `plugins` directory.
