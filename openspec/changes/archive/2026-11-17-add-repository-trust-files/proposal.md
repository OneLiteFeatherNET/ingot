# Proposal

## Why

Someone who finds a flaw in Ingot and wants to tell the maintainers finds a policy that stops mid-sentence. `.github/SECURITY.md`
points to GitHub's private advisory form, which works, but the next paragraph reads `TODO(onelitefeather): security contact
address`. The same unfinished marker sits in `.github/CODE_OF_CONDUCT.md`, so a person who wants to report harassment has no
address either.

The project also cannot answer the questions a new user, an adopter or an audit asks first: who maintains Ingot, how decisions
are made, how Ingot relates to the upstream Reposilite it forks, and who already runs it. None of `MAINTAINERS.md`,
`GOVERNANCE.md`, `ADOPTERS.md` or `SECURITY-INSIGHTS.yml` exists on main. The harden-http-security change left the contact
address in `SECURITY.md` open because it needs the maintainer. This change closes that gap and adds the rest of the trust
files in one place.

The contact addresses are not known to the author. Until the maintainer supplies them, the drafts keep a
`TODO(onelitefeather)` marker. The first task fills them, and the pull request does not merge with a marker left in any trust
file.

## What Changes

- **Contacts.** `.github/SECURITY.md` and `.github/CODE_OF_CONDUCT.md` get the real private contact address in place of the
  marker. The advisory form stays as the first channel. The same address appears in `SECURITY-INSIGHTS.yml`.
- **`MAINTAINERS.md`.** The current maintainers, their GitHub handle, their role and how to reach them. It links
  `.github/MAINTAINING.md`, which already describes how the fork is kept in sync, and does not repeat it.
- **`GOVERNANCE.md`.** How decisions are taken, how people become and stop being maintainers, and the relation to upstream:
  Ingot is an independent product, not affiliated with or endorsed by Reposilite or its authors, and upstream changes flow
  in through the cherry-pick policy of `.github/MAINTAINING.md`. It refers to `VERSIONING.md` for the version line.
- **`ADOPTERS.md`.** A list of organisations that run Ingot, starting with OneLiteFeather. Entries are added by pull request.
- **`SECURITY-INSIGHTS.yml`.** The OpenSSF Security Insights file with the project header, the license, the contribution
  policy, the security contact and the vulnerability reporting channel, in the schema version current at apply time.
- **Link.** The `Maintainers` section of `.github/CONTRIBUTING.md` links `MAINTAINERS.md` and `GOVERNANCE.md`.

### Out of scope

- The community chat and documentation link placeholders in `.github/CONTRIBUTING.md`, `.github/README.md` and
  `.github/ISSUE_TEMPLATE/config.yml`. They wait for a chat channel and a published handbook, neither of which is decided.
- The DCO sign-off check named in `.github/MAINTAINING.md`.
- `ROADMAP.md`, `SUPPORT.md` and a `CODEOWNERS` file. Each needs a decision of its own.
- Repository settings. Enabling private vulnerability reporting on GitHub is checked by the maintainer in task 2.1 but is
  not changed by a commit.
- A full OpenSSF Best Practices or Security Insights assessment. The file records what is true today.
- Any change to code, configuration, environment variables, the container image or the Helm chart.

## Capabilities

### New Capabilities

- `repository-trust-files`: the security and conduct contacts, the maintainer, governance and adopter files, and the Security
  Insights file that describes them.

## Impact

- **Type and scope.** The change ships under `docs(community)`. The proposal PR ships under `docs(openspec)`. The pull request
  title, squash-merged onto main, is:

  `docs(community): add maintainers, governance and security contacts`

- **Breaking marker.** No breaking change. Nothing an operator configures changes, no volume, manifest, plugin or image changes,
  and a Reposilite installation pointed at Ingot behaves exactly as before. The title carries no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Opt-in.** Not applicable. No runtime behaviour is added.
- **Dependencies.** None. The change does not touch the files of the other Wave A changes (`fix-entrypoint-signal-handling`,
  `add-chart-upgrade-rehearsal`, `fix-schema-init-lock`, `fix-statistics-shutdown-flush`, `fix-atomic-filesystem-upload`).
  It fulfils the contact-address item that `harden-http-security` and `harden-ci-supply-chain` both leave out of scope.
- **Docs.** No `docs/gitbook/` page changes, because no setting, environment variable or manifest changes.
- **Code.** `.github/` (SECURITY.md, CODE_OF_CONDUCT.md, CONTRIBUTING.md) and five new files at the repository root.

## Verification

- `openspec validate add-repository-trust-files --strict` passes.
- `grep -rn 'TODO(onelitefeather)' .github/SECURITY.md .github/CODE_OF_CONDUCT.md SECURITY-INSIGHTS.yml MAINTAINERS.md GOVERNANCE.md ADOPTERS.md`
  prints nothing before the code pull request is opened.
- The contact address in `SECURITY-INSIGHTS.yml` equals the one in `.github/SECURITY.md`.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-repository-trust-files` prints nothing.
- This is a documentation change without production code, so no unit test applies. The checks above replace it.
