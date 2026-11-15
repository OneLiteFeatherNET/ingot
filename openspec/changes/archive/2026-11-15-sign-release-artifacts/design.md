# Design

## Decisions

1. **Keyless signing instead of a stored key.** cosign signs with a short-lived certificate issued for the workflow's OIDC
   token, and the signature is recorded in the public Rekor transparency log. The trust anchor is the workflow identity
   `OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main`, not a key that a maintainer could lose or
   leak. The cost is a dependency on the public Sigstore services during a release. A release that cannot reach them fails
   and is retried, which is the behaviour wanted. The log entries carry digests and identities only, no secret.

2. **Sign digests, never tags.** Tags are mutable, and the server image has a retry path. The digest for signing is taken
   from whichever push succeeded: `steps.server.outputs.digest` when the first push worked, otherwise
   `steps.server-retry.outputs.digest`. The same expression is used for the dashboard image. The retry steps get ids for
   this; they have none today.

3. **Verify in the same job, before it can succeed.** Each signing job runs `cosign verify` (and the jar job runs
   `gh attestation verify`) with the identity above as the only accepted issuer and subject. A signature that does not verify
   fails the job, which keeps the release visibly failed rather than quietly unsigned. This is the in-workflow check required
   by roadmap P8.

4. **Dispatch rebuilds sign too, and must run from main.** `release-please.yml:281` sets `released=true` for a
   `workflow_dispatch` with `container_version`, so such a rebuild already pushes the release tags. Signing it keeps the
   rebuilt digest verifiable. A dispatch from any other branch produces an identity that the verify step refuses, so it fails
   after the push. That is accepted, and documented in `.github/RELEASE.md`: a rebuild of a release is dispatched from main.
   Refusing the dispatch earlier would also block the legitimate rebuild and was not chosen.

5. **Jar: attestation, not a detached signature.** `actions/attest-build-provenance` stores the provenance in GitHub's
   attestation store and is checked with `gh attestation verify`. It needs no key and no extra asset on the release. A
   detached `cosign sign-blob` would add a `.sig` and a `.pem` asset to every release page, which the release page does not
   need for an operator who already downloads the jar from the release.

6. **GHCR throttling.** A signature is one more manifest push per release digest. The sign steps reuse the house pattern of
   one retry after the same wait the image push uses, so a refused manifest PUT does not fail a release that already pushed
   its image.

## Permissions

| Job | Today | After |
|---|---|---|
| `container` | `contents: read`, `packages: write` | plus `id-token: write`, `attestations: write` |
| `chart` | `contents: read`, `packages: write` | plus `id-token: write` |
| `jar` | `contents: write` | plus `id-token: write`, `attestations: write` |
| `changes`, `maven`, `release-please` | unchanged | unchanged |

The top-level `contents: read` stays. `attestations: write` is needed only where the attest step runs, and
`id-token: write` only where cosign runs keyless.

## Risks

- A workflow change cannot be fully exercised before a release. The policy test catches permission and ordering mistakes;
  the first release proves the signing itself. If that release fails, the fix is a `fix(release)` change, and the release
  already on GHCR is unaffected because signing never changes a tag.
- `helm push` output format is the only source of the chart digest. The task checks for the `Digest:` line and fails when it
  is missing, instead of guessing a reference.
