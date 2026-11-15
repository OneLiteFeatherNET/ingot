# Proposal

## Why

A build that deploys to Ingot sends an artifact and then its checksum files. If the connection drops, a proxy rewrites the
body or a client is buggy, the stored checksum can disagree with the stored artifact. Ingot accepts both without a word, and the
damage shows up later in a consumer's build, far from the deploy that caused it. An operator who needs to trust what is in the
repository, for a release or for an audit, has no way to make the server refuse such an upload or even to see that it happened.

The fix is an opt-in check. When it is on, a checksum file that does not match the artifact it belongs to is refused with
`400`, the artifact stays untouched, and the refusal is written to the audit log. With the check off, which is the default,
nothing changes.

## What Changes

- **Policy.** New repository setting `uploadChecksumPolicy` with the values `ignore`, `warn` and `reject`. Left out, the
  repository takes the instance default. The instance default is set by the new variable `INGOT_UPLOAD_CHECKSUM_POLICY`
  (`REPOSILITE_UPLOAD_CHECKSUM_POLICY` as fallback, `-Dingot.uploadChecksumPolicy` as system property) and is `ignore` when
  unset. It applies to hosted repositories that take uploads.
- **What is checked.** A deploy of a file whose name ends in `.md5`, `.sha1`, `.sha256` or `.sha512` is a checksum file. When the
  file it describes (same path without the suffix) is already stored, Ingot digests the stored file with that algorithm and
  compares it with the digest in the upload. The upload may be the bare digest or the `sha1sum` form `digest  name`, in any
  letter case.
- **Order.** Only this order is checked: artifact first, checksum second. That is how Maven and Gradle deploy. A checksum file
  that arrives when its artifact is not stored is accepted and not checked, in every policy. See design.md D1.
- **`reject`.** A mismatch, or a body that is not a digest of the right length, answers `400` with a message that names the
  algorithm. Nothing is stored for the checksum file, so no partial or wrong checksum is ever visible. The artifact stays.
- **`warn`.** The mismatch is logged and audited, and the checksum file is stored as today.
- **Audit.** New audit action `UPLOAD_CHECKSUM_MISMATCH` with the repository, the path, the actor and the details `algorithm`
  and `outcome` (`rejected` or `accepted`). It is written by the existing audit plugin from a new event, so no write path of
  the audit log changes.
- **Metric.** The existing deploy outcome counter gains the label value `checksum_mismatch` only when the policy is not
  `ignore`. Nothing is emitted under the default.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` (the variable), `docs/gitbook/reference/repository-settings.md`
  (the key), `docs/gitbook/reference/audit-events.md` (the action), `docs/gitbook/reference/api-errors.md` (the `400`), and a
  new how-to `docs/gitbook/how-to/verify-uploaded-checksums.md` listed in `docs/gitbook/SUMMARY.md`.

### Out of scope

- A checksum file that arrives before its artifact. Verifying it needs the artifact commit to check the stored checksums, which
  means a hook in the storage providers. It is a follow-up, and the gap is stated in the how-to.
- Requiring that a checksum file exists at all, and rejecting an artifact that has none.
- Server-side generation of checksums. `X-Generate-Checksums` exists and is not changed.
- A scan or backfill of files stored before the policy is turned on. That is the integrity scan task of the roadmap.
- Checking mirrored files (`add-mirror-integrity`), signatures (`.asc`) and metadata content.
- Verification against `Content-MD5`, `Digest` or `X-Checksum-*` request headers.
- Any change to the format of the stored files or to the S3 layout.

## Capabilities

### New Capabilities

- `upload-checksum-verification`: the policy, the check of a checksum file against its stored artifact, the refusal, the audit
  action and the order rule.

## Impact

- **Type and scope.** The change ships under `feat(maven)`. The pull request title, squash-merged onto main, is:

  `feat(maven): verify uploaded checksum files against the stored artifact`

- **Breaking marker.** No breaking change. The policy is `ignore` unless an operator sets it, so a Reposilite installation
  pointed at Ingot behaves as before. The new key is optional, the new variable is optional, and the new audit action and metric
  label only appear when the policy is on. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None.
- **Opt-in.** `uploadChecksumPolicy` per repository, or `INGOT_UPLOAD_CHECKSUM_POLICY` for the instance. A stored value of
  `uploadChecksumPolicy` is a new key in the shared maven settings; an upstream Reposilite that reads those settings after a
  rollback ignores an unknown key only if its loader is tolerant, which the rollback task checks (tasks 6.1).
- **Dependencies on other changes.**
  - `fix-atomic-filesystem-upload` (implemented on main): readers of the stored artifact see old or new bytes, never a partial
    file, so the digest is always of a committed file.
  - `add-problem-details` (merged): the `400` uses the problem body.
  - `add-mirror-integrity` (merged, #118): uses the same policy vocabulary (`ignore`, `warn`, and a refusing value) and adds audit
    actions too. The two changes do not share code. Where both add enum entries to `AuditAction`, this change rebases onto
    main and reuses the vocabulary of `add-mirror-integrity`.
- **Code.** `reposilite-backend` (maven settings, `RepositoryService`, audit plugin and action), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate verify-upload-checksums --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/verify-upload-checksums` prints nothing.
