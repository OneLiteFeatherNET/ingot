# Proposal

## Why

An operator who runs Ingot with its artifacts on a shared volume, such as CephFS, and more than one replica, has no
guarantee that a reader sees a finished file. A Maven client that downloads an artifact while another replica is
still storing it can receive a half-written jar, and the checksum or the archive then fails in the build.

The cause is in the upload path. The request body is written to a temporary file in `java.io.tmpdir`, and the file is
then moved to its final place with a plain replace. When the temporary directory is on another filesystem than the
repository, which is the normal case in a container, that move is a copy. The final file appears under its real name
and grows while it is copied. Nothing in the move asks for atomicity.

The same temporary file also lives outside the data volume. A large upload fills the container's own storage instead
of the volume the artifact is meant for.

## What Changes

- The upload is written to a temporary file next to its target, in the same directory, named `.ingot-<random>.part`.
- The file is moved into place with one atomic move that replaces the old file. The move falls back to a plain replace
  only when the filesystem refuses atomic moves, and logs one warning when it does.
- A file named `.ingot-*.part` is an upload in progress. It is left out of listings, directory views, search entries,
  usage figures and direct reads. An upload to such a name is refused with `400`, because no Maven artifact has a name
  with a leading dot.
- When the provider starts, partial files whose last modification is older than 24 hours are deleted. Newer partial files
  stay, because another replica on the same volume may still be writing them.
- No setting is added. Apart from the reserved name, a client cannot tell the difference.

### Out of scope

- S3 storage. An object is committed as a whole by the object store, and streaming multipart upload is change N4.
- The checksum temporary file in `Repository.kt`. It is small, never moved into place, and stays in the temporary directory.
- Locks across replicas. This change makes readers see either the old or the new bytes; it does not serialise writers
  on different replicas. That is F4, `fix-cluster-metadata-lock`.
- Removing the sized `/tmp` volume from the Helm chart. It becomes unnecessary for uploads, but the change is N6.
- Resumable uploads and the staged-upload service provider interface (S1).

## Capabilities

### New Capabilities

- `filesystem-upload-atomicity`: how a filesystem upload is written, committed, hidden while in progress and cleaned up
  after a crash.

## Impact

- **Type and scope.** The change ships under `fix(storage)`. The branches use `test`, `fix` and `docs` commits with the
  scope `storage`, one type per commit. The pull request title, squash-merged onto main, is:

  `fix(storage): move uploads into place atomically`

- **Breaking marker.** No breaking change for operators. No configuration key, environment variable, manifest or plugin
  has to change, and a Reposilite data directory is adopted as before. The title carries no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Behaviour that differs.** Three points need a note in the upgrade changes, and none needs action:
  - the temporary file now lives on the repository volume, so an upload needs free space there while it is written;
  - a name of the form `.ingot-*.part` is refused on upload with `400`;
  - a partial file left by a crash is removed after 24 hours or at the next start once it is that old.
- **Clients.** Status codes and response bodies of uploads and downloads do not change. A Maven, Gradle or npm client sees
  no difference.
- **Dependencies.** None on other changes. No other wave A change touches the files named here. The staged-upload
  SPI (S1) builds on the commit path introduced here, so S1 depends on this change.
- **Code.** `reposilite-backend` (storage filesystem package and its tests), `reposilite-backend/src/containerTest`
  for the rollback check, and `docs/gitbook/reference/upgrade-changes.md` and `docs/gitbook/reference/storage-providers.md`.

## Verification

- Targeted tests per commit, run through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`.
- The new unit tests fail before the implementation commit and pass after it.
- The filesystem integration test passes unedited.
- The container rollback test starts the upstream image on a volume that holds an upload in progress, and passes.
- `openspec validate fix-atomic-filesystem-upload --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-atomic-filesystem-upload` prints nothing.
