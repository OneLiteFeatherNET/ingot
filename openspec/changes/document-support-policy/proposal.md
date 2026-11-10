# Proposal

## Why

Releases `v1.0.0`, `v1.1.0` and `v1.1.1` exist, yet `.github/SECURITY.md` still says Ingot has published no release, and no
file tells a user where to ask for help or how long a release line receives fixes. An operator choosing a version, or an
auditor asking what "supported" means, has nothing to read in the repository.

## What Changes

- **Support file.** A new `SUPPORT.md` at the repository root says where to get help, which release lines receive fixes and
  for how long, and that support never gates a feature.
- **Security policy table.** The `Supported Versions` section of `.github/SECURITY.md` is updated to the same lines and links
  `SUPPORT.md`. No other section of that file changes.

### Maintainer decision needed before implementation

The support window is a maintainer decision and this change does not invent one. The draft options stay as options until the
maintainer chooses:

- Option A: the latest minor line receives every fix; the previous minor line receives security fixes only, for 90 days after
  the next minor line ships; older lines receive nothing. This needs a release branch for the previous line, which the
  repository does not have yet.
- Option B: only the latest line is supported, and the table has one row.

The maintainer also names the channel for questions. Implementation does not start before these answers exist.

### Out of scope

- A commercial support offering, support tiers or an SLA. `SUPPORT.md` describes community support only.
- Contacts in `.github/SECURITY.md`, `.github/CODE_OF_CONDUCT.md`, `MAINTAINERS.md`, `GOVERNANCE.md`, `ADOPTERS.md` and
  `SECURITY-INSIGHTS.yml`: they belong to `add-repository-trust-files`.
- `security.txt`: it waits for the contact address in `add-repository-trust-files`.
- Creating the release branch itself. If the maintainer picks option A, that is a separate step.

## Capabilities

### New Capabilities

- `support-policy`: where help is found and which release lines receive fixes.

## Impact

- **Type and scope.** The change ships under `docs(support)`. The pull request title, squash-merged onto main, is:

  `docs(support): add a support policy and support window`

- **Breaking marker.** None. Documentation only; the title carries no `!`.
- **Behaviour that changes by default.** None.
- **Dependencies on other changes.**
  - `add-repository-trust-files`: overlaps with this change only in `.github/SECURITY.md`. That change edits the contact
    section, this one edits only `Supported Versions`. The two touch different hunks, but whichever is applied second rebases
    onto the first. Neither blocks the other.
  - `add-version-check-switch`: split out of it. It no longer carries any support text.
- **Code.** None. `SUPPORT.md` and `.github/SECURITY.md`.

## Verification

- `openspec validate document-support-policy --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/document-support-policy` prints nothing.
- Both files name the same release lines, and the diff of `.github/SECURITY.md` is limited to the `Supported Versions` section.
