# Proposal

## Why

Ingot publishes two container images, the repository server and the dashboard, each as a multi-architecture image on
`ghcr.io/onelitefeathernet`. An operator who inspects a tag sees the labels that the image config carries: title, description,
vendor, licence and version. The labels live inside the image, though. The index that the tag points to, and the per-platform
manifests under it, carry nothing. Registry tooling reads from those places. A registry UI, a vulnerability scanner that reads
only the manifest, or an operator who runs `docker buildx imagetools inspect` sees an index without a title, without a licence
and without any record of which base image the run stage was built on.

The release workflow sets the metadata action's labels and never its annotations. Its default level is `manifest`, so even the
annotations the action can generate would not reach the index. The base image is pinned in each Dockerfile by tag and digest,
and that pin is the one fact a reviewer needs to answer "what is this built on", yet nothing publishes it.

## What Changes

- **Index and manifest annotations.** The server and the dashboard images get the same five OCI annotations that their labels
  already carry (`title`, `description`, `vendor`, `licenses`, `version`), on the image index and on each platform manifest.
- **Base image annotations.** Both images get `org.opencontainers.image.base.name` and `org.opencontainers.image.base.digest`.
  The values are read from the run stage `FROM` line of the image's own Dockerfile at build time, so a digest that Renovate
  raises in the Dockerfile reaches the annotation without a second edit.
- **A check on the pushed images.** A small script reads the raw index of each pushed reference and fails the release job when
  an expected annotation is missing or empty. It is tested first against fixture files, so the check itself is verified without
  a registry.
- **Scope of the workflow.** Only the build steps of `.github/workflows/release-please.yml` change: the metadata steps that
  compute annotations, the four build-and-push steps (server, server retry, dashboard, dashboard retry) that pass them on, and
  the check after the pushes.

Current state, verified against `origin/main`: the roadmap names `release-please.yml:298,336`. Those lines are a comment about
QEMU and the first server label block. The label inputs of the build steps are at `release-please.yml:339`, `:377`, `:414` and
`:437`. The metadata steps are at `:310` (server) and `:385` (dashboard). Nothing in the repository sets `annotations`.

### Out of scope

- The "Container images" job in `ci.yml`. It builds unpushed images for the host architecture and never publishes an index, so
  it has no annotations to check. The release job is where the check belongs.
- Attestations (SBOM and provenance). They stay releases-only and are not changed.
- Changing any label value, tag, entrypoint, path, port, user or environment variable of either image.
- Annotations on the `ingot-dashboard` base image choice or the base images themselves.
- Annotations for `source`, `revision` or `created`. They can be added later as a separate, additive change.

## Capabilities

### New Capabilities

- `image-annotations`: the OCI annotations on the image index and manifests of both published images, including the base image
  references, and the check that the release job runs against them.

## Impact

- **Type and scope.** The change ships under `ci(docker)`. Its commits are `test(docker)` (the check script with fixtures) and
  `ci(docker)` (the workflow). The pull request title, squash-merged onto main, is:

  `ci(docker): add oci annotations to the image index and manifests`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, plugin or data
  directory changes, and no operator edits anything. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes.** The index and manifests gain annotations. Because annotations are part of the index bytes, the
  digest of each release tag changes once, on the first release after this lands. Clients that resolve by tag see no difference.
  An operator who pins a release by digest keeps the old digest, which still resolves. This is noted for the release notes and
  is not a drop-in break.
- **Nightlies.** Nightlies carry the same annotations. The `version` annotation is `nightly`, the same value as the label.
- **Code.** `.github/workflows/release-please.yml`, and one new script under `.github/scripts/` with its fixtures.
- **Docs.** Not applicable. No configuration key, environment variable or manifest changes, so no page under `docs/gitbook/` is
  touched by this change.

## Verification

- The check script's tests fail before the script exists and pass after it, using fixture files in a temporary directory.
- The release job prints the annotations of both pushed index references after each release or dispatch run.
- `openspec validate annotate-image-index --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/annotate-image-index` prints nothing.
