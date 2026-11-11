---
description: Move a Nexus Repository 3 installation into Ingot with the guided Migration page of the dashboard, step by step, including what a reload does, where the password lives and how to trust a private certificate authority.
---

# Use the Nexus import wizard

The Migration page of the dashboard walks an instance administrator through an import from Nexus Repository 3 in seven steps: Connect, Discover, Mapping, Dry run, Sync, Verify and Cut-over. It calls the import API of the `nexus-import` plugin, so everything the wizard does can also be done with `curl`. The wizard only puts the steps in order and keeps you from skipping one.

The page is visible only when the plugin is enabled. Switch the plugin on as described in [Import from Nexus](import-from-nexus.md#switch-it-on). Without the switch the address `/settings/migration` shows the not-found page, and no entry appears in the navigation.

Nothing in the wizard writes to Nexus. Every step reads Nexus or writes to Ingot, and the freeze of Nexus writes at the end is a step you carry out in Nexus yourself.

## Before you begin

* A Nexus credential with read access to the repositories you want to copy. See the first list in [Import from Nexus](import-from-nexus.md).
* An Ingot account that is an instance administrator.
* A private certificate authority for Nexus, if Nexus uses one. Provide it to Ingot as described in [Trust a private certificate](#trust-a-private-certificate) before you start.

## The steps

1. **Connect.** Enter the Nexus URL, the user name and the password, or a secret reference such as `${env:NEXUS_PASSWORD}`. Test the connection. The page shows the Nexus version, and whether the credential may list repositories and read their settings. A secret reference is kept as text, and only the reference is sent.
2. **Discover.** Lists every repository in Nexus with its format, type, component count and size, and a table of the formats. Formats the plugin cannot import are marked as excluded, so you can decide before you map anything.
3. **Mapping.** Choose the Ingot name of each repository and whether it is included. Empty names and names that two included repositories share are flagged. Choose what happens when a repository already exists in Ingot. `Refuse` is the default and changes nothing that exists. `Abort` stops a repository at its first conflict. `Overwrite` replaces existing files, and the page asks you to confirm it first.
4. **Dry run.** Starts a dry run that reads Nexus and writes nothing. The loss report lists each object with its status (`MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED` or `EXCLUDED`), the reason, the consequence and the action. Filter it, or download it as JSON or CSV. A name that is taken comes with the name Ingot proposes, `<name>-nexus`. You can take the proposal, which changes the mapping and asks for a new dry run.
5. **Sync.** Starts the import once you have accepted the loss report for the current mapping. Sync stays closed until you do, and the page says why. The import copies the content and shows the progress of each repository.
6. **Verify.** Compares the copy with Nexus. Each repository ends `VERIFIED` or `MISMATCH`, and the paths that differ are listed.
7. **Cut-over.** The checklist for the day the clients are switched: freeze the writes in Nexus, check that every repository is verified, and switch the clients. The verify item is set by the verify job and cannot be ticked by hand. The page also shows Maven and Gradle settings for each repository, with placeholders for the token.

The address of each step is `/settings/migration/<step>`, for example `/settings/migration/dry-run`. A step you have not reached yet redirects to the step you have to finish first, and the page says which one that is.

## What a reload does

The import, the dry run and the verify run as jobs on the server, so a reload finds them again. After a reload the page opens the step the jobs call for: a running import opens Sync and shows its progress, a finished import opens Verify, and a finished verify opens Cut-over.

Your choices in the page are not stored on the server. A reload before the first job empties the connection, the discovery, the mapping and the acceptance of the loss report, and the page starts at Connect again. Once a job exists, the choices that the job already carries stay with the job.

## Where the password lives

A literal password stays in the memory of the browser tab while the wizard is open. It is not written to local storage, session storage, a cookie or the address. It is sent only in the body of a request to the Ingot API, and the server keeps it in memory only while a job needs it.

The password is forgotten when you leave the Migration page, sign out, or the session ends with a `401`. After a reload the page asks for the password again before the first call that needs it. A job that Nexus pauses because its credential was refused shows a form for the password, and that form sends the password only to the credentials route of the job.

A secret reference avoids a literal password in the browser altogether. The server resolves it, and that needs the secret reference setting `INGOT_SETTINGS_SECRET_REFERENCES` to be switched on.

## Trust a private certificate

The wizard has no setting for the trust of a certificate. Ingot checks Nexus against the trust store of the Java runtime. If the connection test reports that the certificate chain is not trusted, provide the certificate authority through `INGOT_EXTRA_CA_CERTS` and restart Ingot. The setting and its format are described in the [environment variables reference](../reference/environment-variables.md).

## After the wizard

* Keep the Nexus repositories read-only until the clients have switched, so no client writes to Nexus while it is being copied from.
* The import creates the repository definitions in Ingot. Check their access settings before you hand out tokens. See [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md).
* Remove the finished verify job from the Cut-over step once you are done. The repositories stay.
