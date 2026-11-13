# Proposal

## Open maintainer questions

Each question carries the default the change is written against. A "no" from the maintainer changes the
design section named in brackets and nothing else.

1. **Build despite working interoperability? (default: yes).** Google Cloud Storage can already be reached
   through the `s3` provider and the XML interoperability API with HMAC keys. The plugin is still built, because
   HMAC keys cannot use GKE workload identity or Application Default Credentials. If the step 0 result shows
   that interoperability is fully sufficient for the operators who ask, the maintainer may cut this change to
   the how-to page alone. [design D1]
   Nexus Repository and Artifactory are said below to offer native GCS blob stores; task 0.5 verifies this per
   edition.
2. **Transport (default: JSON over HTTP).** The plugin uses the JSON/HTTP transport of the client and never
   selects gRPC. This keeps the dependency tree small. [design D1]
3. **Minimal IAM role (default: `roles/storage.objectUser` at bucket scope).** The predefined role table
   (reported as checked on the Google Cloud documentation; the link and date are recorded by task 0.5) lists `storage.objects.create`, `storage.objects.delete`,
   `storage.objects.get` and `storage.objects.list` in `roles/storage.objectUser`. The same table lists managed
   folders as the lowest resource of that role, so step 0 must confirm that the role works when bound at bucket
   scope. Until it does, the documentation names `roles/storage.objectAdmin` as the fallback. [design D2, D8]
4. **Native paging (default: yes).** The JSON `objects.list` method accepts `startOffset` and `pageToken`
   (verified). Native paging is used, and the cursor is the root-relative path. Step 0 must confirm that the
   emulator used in tests honours `startOffset`; if it does not, the affected tests are skipped with a reason.
   [design D6]
5. **Client library home (default: the `google-cloud-storage` artifact from the `libraries-bom` BOM, pinned to
   the current release at implementation time).** The `googleapis/java-storage` repository is licensed under
   Apache 2.0 but states that it is deprecated and that its content has moved to `googleapis/google-cloud-java`.
   The maintainer confirms the coordinates and licence at implementation time. [design D1]
6. **Meaning of "empty prefix" (default: a directory prefix without objects is `NOT_FOUND`).** The brief says an
   empty prefix is `NOT_FOUND`, matching `s3`. This change reads that as: a directory prefix with no object below
   it is `NOT_FOUND`, and the repository root is always a directory, as in the `s3` provider. [design D5]
7. **Emulator endpoint guard (default: refused unless `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT=true`).** The
   `endpoint` key exists for the emulator only. Without the process-wide switch, a repository that sets
   `endpoint` does not start, so that a copied test setting cannot send production data to a foreign host.
   [design D2]
8. **Shaded size budget (default: report only, no limit).** The shaded jar size is measured and written into
   the how-to page. No hard limit is set until the first measurement is seen. [design D1, risks]

## Why

Operators who keep artifacts in Google Cloud Storage have two options in Ingot today. They can use the `s3`
provider with HMAC keys through the XML interoperability API at `storage.googleapis.com`, or they can run
their own filesystem mount. Ingot's own documentation marks multipart uploads over the interoperability API as
not checked (`docs/gitbook/how-to/use-s3-storage.md`, the note after the temporary file section), and the
interoperability API cannot use GKE workload identity or Application Default Credentials. Nexus Repository and
Artifactory both offer native Google Cloud Storage blob stores, so an operator who compares the products expects
the same.

A native provider keeps the upload path, the consistency model and the credential model of the platform. This
change adds it as a plugin, because the storage registration of `add-storage-provider-registration` makes that
possible without touching the core.

## Step 0 result (interoperability versus native)

Not yet run. Before the proposal pull request is opened, the implementer runs the existing `s3` integration
tests against a real Google Cloud Storage bucket through the interoperability API with HMAC keys, and records
here, per test: passed, failed with the error, or not applicable. The run also records whether an upload above
8 MiB succeeds, since the `s3` how-to marks that case as unverified. Only the recorded result decides whether
open question 1 is answered "yes" or "cut to docs".

Recorded result: pending.

## What Changes

- **A plugin module `reposilite-plugins/gcs-storage-plugin`** (package `net.onelitefeather.ingot.storage.gcs`,
  plugin id `gcs-storage`). It registers the storage type `gcs` through `IngotStorageProviderFactory` with
  `apiVersion = STORAGE_PROVIDER_API_VERSION` (1), opted in with `@OptIn(ExperimentalIngotStorageApi::class)`, and listed in the jar's
  `META-INF/services/com.reposilite.storage.StorageProviderFactory`, so the factory is discovered from the plugin jar. Without the jar, a
  repository of type `gcs` is `UNAVAILABLE` with the reason `storage provider 'gcs' is not installed`, as
  `add-storage-provider-registration` specifies. The provider implements the storage SPI of `com.reposilite.storage`
  and the capability interfaces of `add-storage-capability-hooks`.
- **Distribution.** The Maven artifact `net.onelitefeather.ingot:gcs-storage-plugin` is the release unit, and the jar is not
  bundled in the image. The plugin store of `add-plugin-store` lists it as a signed catalogue entry, selectable with
  `INGOT_PLUGINS` or in the dashboard and published by the release pipeline of `publish-plugin-catalogue`. The jar also
  stays installable by hand into `plugins/`, and a jar there wins over a store entry with the same id. No switch and no `BUNDLED_SWITCHES`
  entry are added.
- **Authentication.** `authentication: "adc"` (the default) uses Application Default Credentials, which covers
  GKE workload identity, the metadata server and `GOOGLE_APPLICATION_CREDENTIALS`. `authentication:
  "service-account-key"` uses a service account key given in `credentialsJson`, which is a sensitive value and
  may also be a secret reference or a file through `add-settings-secret-references` and `add-file-secrets`.
- **Settings per repository** in the existing `storageProvider` block of `configuration.shared.json`: `type`, `projectId`, `bucket`, `prefix`, `endpoint`
  (emulator only), `authentication`, `credentialsJson` and `chunkSize`. The type is always written.
- **Process-wide variable** `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT` (fallback `REPOSILITE_GCS_ALLOW_EMULATOR_ENDPOINT`),
  default `false`.
- **Behaviour.** Uploads stream in chunks of `chunkSize` and become visible only when finalised. Ranged reads use a
  ranged channel, and a range that starts at or past the end is the empty answer of the storage contract kit. Listing pages natively. Errors map to `NOT_FOUND` for 404 and to a reported failure for 401 and
  403. Retries of 429 and 5xx use the client defaults.
- **Tests.** Unit tests behind a thin adapter with a fake. Integration tests run the storage contract kit against
  `fsouza/fake-gcs-server` (BSD-2-Clause) in a container pinned by tag and digest.
- **Documentation.** A new how-to `docs/gitbook/how-to/use-google-cloud-storage.md` with the install steps, a GKE
  workload identity example, a settings example, the step 0 result, and the limits. The interoperability note of
  `use-s3-storage.md` links to it. The new keys and the process-wide variable are added to the configuration and
  environment variable reference pages.

### Out of scope

- Archive and Coldline restore handling. Objects in those classes are read as they are, and a read of an archived
  object fails with the error the client returns.
- Autoclass and lifecycle policy management. The operator configures them in Google Cloud.
- Object retention lock and holds. Writes to a locked object fail with the error the client returns.
- Signed URL redirects. Downloads are streamed through Ingot as for every other provider.
- A metadata cache. Version one reads object metadata on every request.
- Conditional creation with `ifGenerationMatch=0`. Coordination of concurrent writers stays in the database.
- Cross-bucket or cross-project replication, and migration from the `s3` provider to this provider.
- A Helm chart option or a Workload Identity manifest generator. The how-to shows the manifest; a chart change is a
  follow-up.
- A bundled image copy of the plugin. The plugin is installed by the operator.

## Capabilities

### New Capabilities

- `gcs-storage`: the plugin, its settings and process-wide variable, its authentication modes, the SPI mapping
  of each method, the write and read paths, the errors, the capabilities, and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `feat(storage)`,
  `build(storage)`, `ci(storage)` and `docs(storage)`. The pull request title is:

  `feat(storage): add a google cloud storage provider plugin`

- **Breaking marker.** No breaking change. Nothing that an existing installation depends on changes. The `fs`
  and `s3` providers, their keys, their defaults and their interoperability behaviour are untouched. The plugin is
  not in the image, so an installation that does not install it sees no difference. The `s3` how-to gains a link
  and no behaviour changes. No operator must change a configuration file, environment variable, volume, manifest or
  plugin to keep running.
- **Build.** A new Gradle subproject `:reposilite-plugins:gcs-storage-plugin`, with a shaded jar that relocates
  Guava, protobuf and the Google API extensions (gax) under `net.onelitefeather.ingot.storage.gcs.shaded`, and a
  Maven publication `net.onelitefeather.ingot:gcs-storage-plugin`.
- **Order.** Depends on `add-storage-provider-registration` (the factory discovery from plugin jars) and on
  `add-storage-capability-hooks` (the capability interfaces that the provider implements). It uses
  `add-settings-secret-references` and `add-file-secrets` for the credential key, and does not wait for them: without
  them the key is read from the setting itself. The distribution tasks depend on `add-plugin-store` and `publish-plugin-catalogue`; no other task does.

## Verification

- Step 0 result recorded in this file before the proposal pull request is opened.
- Unit tests of the settings rules, the adapter mapping and the SPI methods pass with no network.
- Integration tests of the contract kit against `fake-gcs-server` pass, and every skipped case names its reason.
- A manual run against a real bucket with `authentication: "service-account-key"` and with the GKE workload identity
  example is recorded in the pull request, including the shaded jar size.
- `openspec validate add-gcs-storage-plugin --strict` passes and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-gcs-storage-plugin`
  prints nothing.
