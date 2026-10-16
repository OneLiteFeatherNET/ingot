---
description: Return an upgraded installation to the previous Reposilite or Ingot release on the same data directory, or restore the backup.
---

# Roll back an upgrade

## Prerequisites

* The image reference of the release you ran before the upgrade.
* The backup from [Back up before upgrading](back-up-before-upgrading.md), if you want to restore it.

{% hint style="warning" %}
Changes made in Ingot after the upgrade are not visible to the old version. Recreate them there, as described in step 4.
{% endhint %}

## Steps

1. Stop the server.

2. Change the image back to the previous release. Leave the rest of the file as it is.

3. Start the server on the same data directory. The old version reads the state it had before the first start of Ingot.

4. Recreate the changes you made in Ingot:
   * Tokens you created in Ingot do not authenticate on the old version. Create them again there.
   * Tokens you revoked in Ingot are active again on the old version. Revoke them again there.
   * Rights you changed in Ingot are back to their earlier values. Change them again there.

5. Check that the tokens and artifacts you had before the upgrade work.

To be certain, stop the server, restore the backup you took before the upgrade, and start the old image on the restored data.

## Result

The old version starts on the same data directory, and the tokens and artifacts you had before the upgrade work again.

A token you create on the old version is imported again the next time you upgrade.
