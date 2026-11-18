# Proposal

## Open maintainer questions

Each question has a suggested default. The design and tasks follow the default unless a maintainer answers otherwise before
the code pull request opens. A "no" changes only the design decision named in brackets.

Maintainer decision of 2026-11-18, already applied below: the dashboard offers Google Cloud Storage and Azure Blob Storage only
while the respective plugin is loaded, and takes their fields from the plugin's settings schema.

1. **Opt-out for a plugin (design D1).** Default: every loaded storage plugin that registers a factory is offered, with no
   flag on the plugin side. Alternative: a plugin declares `dashboardVisible = false` on its factory, which costs one more
   member on `IngotStorageProviderFactory` under the experimental marker.
2. **Where the live registry lives (design D2).** Default: one `StorageProviderRegistry` per server, created with the plugin
   system and handed to the schema and the serdes. Today the serdes read a companion map that is built once from the
   application class path. Alternative: keep the companion and add a second, dashboard-only lookup, which keeps two sources
   of truth and is not recommended.
3. **Seam for the merged schema (design D3).** Default: the served schema of the repository settings is merged at serve time,
   and only the `storageProvider` property changes. Alternative: generate every settings schema at runtime instead of loading
   the build-time index. That touches every settings type and is a separate change.
4. **Wizard fields (design D5).** Default: the wizard offers every variant the served schema lists and shows the fields of a
   plugin variant from its schema, validating the `required` list. Alternative: the wizard offers the plugin variant and hands
   the fields over to the Storage tab, which is less code but leaves the first save incomplete.
5. **Field order (design D6).** Default: the built-in variants keep their order, plugin variants follow sorted by type id, and
   the fields inside a variant follow the settings class order. The display-only layout registry of the form ordering work is
   not on `main` yet (`reposilite-frontend/src/components/settings/nestedLayouts.ts` from PR #791 is missing), so this change
   uses it for the final order once it lands and does not wait for it. Alternative: wait for PR #791 before the frontend group
   starts.
6. **Secrets of a plugin variant (design D4).** Default: the `@Sensitive` fields of every offered variant join the secret
   shape of `storageProvider`, so they are redacted and sealed like the S3 keys. This is required, not optional, and the
   test of group 2 fails without it. There is no acceptable alternative; it is listed so a reviewer sees the dependency.
7. **A saved type that the served schema does not list (design D7).** Default: the value is kept as it was written, the Storage
   tab shows the type as unavailable, and nothing rewrites it to `fs`. This matches the `UNAVAILABLE` state of the registry.
   There is no acceptable alternative; it is listed for the same reason.
8. **Schema failure of one plugin (design D8).** Default: a plugin whose settings schema cannot be generated is left out with a
   warning that names the plugin, and every other variant is still served. Alternative: fail the settings schema, which blocks
   the whole repository form for one plugin.

## Why

Ingot stores repository files in the filesystem or in S3, and an operator picks one per repository in the dashboard. The
Google Cloud Storage and Azure Blob storage plugins are built to be installed beside the server, and they register their
provider type through the storage registration of `add-storage-provider-registration`. The dashboard does not know about
them. The storage choice in the repository Storage tab and in the new repository wizard lists only file system and S3, so
an operator who installs the Azure plugin still has to write the `storageProvider` block of the configuration file by hand.
That is the step the plugin is meant to remove.

Two places hard-code the list today. The repository settings declare the storage choice with `@OneOf` on `storageProvider`
in `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenSettings.kt`, which names the two built-in
settings classes. The wizard in `reposilite-frontend/src/components/settings/repositoryWizard.ts` branches on the kinds `fs`
and `s3` only. A plugin that registers a provider cannot change either list without a code change in the server.

## What Changes

- **A registry-backed list of storage variants.** The served schema of the repository settings lists, in the `oneOf` of
  `storageProvider`, the built-in variants as today and one further variant for each storage factory that a loaded plugin
  registered. A variant carries the plugin's settings schema, with `type` fixed to the factory's type id. Built-in variants
  are never removed or changed.
- **Type ids and the registry.** The `type` enum and the subtype resolution of the schema generator read the live registry
  instead of the companion map of `StorageProviderSerdes`, so a plugin type is a valid value in the schema. The parse path
  stays the one that `add-storage-provider-registration` already defined.
- **Secrets.** The secret shape of `storageProvider` (`SettingsSecrets.kt`, where `@OneOf` is read) includes the variants that
  are offered at the time it is computed.
- **Wizard.** `repositoryWizard.ts` takes its storage kinds from the served variants instead of a fixed `fs` and `s3` pair.
  For `fs` and `s3` the stored block is unchanged.
- **Tests and documentation.** Tests are written first for each group (see tasks). The how-to pages for Google Cloud Storage
  and Azure Blob Storage gain one sentence that the dashboard offers the provider once the plugin is installed. No environment
  variable and no configuration key is added.

### Out of scope

- Any change to the saved configuration format. A repository keeps `storageProvider` with `type` and the same keys.
- Changing the built-in `fs` and `s3` settings, their defaults, their labels or their order.
- Changing the plugin API: `IngotStorageProviderFactory`, `StorageProvider` and `StorageFacade` keep their signatures. The
  change reads what the registry already holds.
- Offering a provider whose plugin is not installed, or listing plugins from the plugin store. The store decides what is
  installed, this change only reflects what is loaded.
- Runtime generation of every settings schema (open question 3).
- Migrating an existing repository from one provider to another. The dashboard edits a repository's choice, it does not copy data.
- The display-only layout registry itself (PR #791). This change uses it when it lands and does not implement it.

## Capabilities

### New Capabilities

- `storage-provider-dashboard`: the storage variants the dashboard and the wizard offer, the rule that a variant is offered only
  while its plugin is loaded, the secret shape of a variant, and the handling of a saved type that is not offered.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)` and `feat(storage)`, and the
  documentation sentence is part of the code pull request as a `docs(storage)` commit. The pull request title is:

  `feat(storage): offer plugin storage providers in the dashboard`

- **Breaking marker.** No breaking change. An installation without the Google Cloud Storage or Azure plugin sees the same
  schema, the same wizard and the same saved files. An operator who installs a plugin sees one more storage choice in the
  dashboard and no other change. No configuration key, environment variable, volume, manifest, route or database schema changes.
- **Dependencies.** `add-storage-provider-registration` and `add-storage-capability-hooks` (both archived) provide the registry
  and the capabilities. The variant tests use the settings shape of `add-gcs-storage-plugin` (not yet archived) and
  `add-azure-blob-storage-plugin` (archived) as reference only. The frontend group uses the form ordering of PR #791, which is
  not on `main` at the time of writing.
- **Order.** Depends on no open change in this repository, except PR #791 for the final field order.

## Verification

- Unit tests of the variant list with a fake registry, with no plugin jar and no network.
- A byte comparison of the served repository schema with no plugin loaded against the schema of `main`.
- A frontend test that the wizard offers exactly the served variants.
- `npx openspec validate offer-plugin-storage-providers-in-dashboard --strict` passes, and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/offer-plugin-storage-providers-in-dashboard` prints nothing.
