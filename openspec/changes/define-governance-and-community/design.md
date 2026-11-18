# Design

## Context

Facts checked on origin/main on 2026-11-18:

- `GOVERNANCE.md` exists on main and is the source of the decision rules. The maintainer decided that its text wins.
- `.github/CONTRIBUTING.md` and `.github/README.md` already link GitHub Discussions in place of the community placeholder. No
  `TODO(onelitefeather)` marker is left in `.github/` or in `GOVERNANCE.md`.
- `.github/ISSUE_TEMPLATE/config.yml` has no TODO comment left. Its community contact link is enabled and points at Discussions. Its
  documentation contact link points at `https://docs.onelitefeather.net/ingot`, and its header comment says documentation goes to the
  Ingot documentation site.
- GitHub Discussions is enabled on the repository (`has_discussions: true`, read on 2026-11-18).
- `.github/CODE_OF_CONDUCT.md` has no attribution line and does not name the Contributor Covenant. Its wording is not the Contributor
  Covenant text. A web search on 2026-11-18 found its opening sentence in other projects' codes of conduct (Mautic, JetBrains and the
  Berlin Code of Conduct), so the origin of the text is not confirmed.
- The CNCF Code of Conduct is an adaptation of the Contributor Covenant 2.0 and routes project-agnostic reports to its Code of Conduct
  Committee (`cncf/foundation`, `code-of-conduct.md`, read on the day of writing). The CNCF page checked did not state whether a project
  must adopt it on acceptance, so that claim is not relied on here.

## Decision 1: decision rules in GOVERNANCE.md (decided 2026-11-18)

The rules of `GOVERNANCE.md` on main are the decision. Where this text and that file differ, the file wins, and the lead maintainer
decides a tie. This change adds one sentence to the file, about security reports, and nothing else about roles or decisions:

- **Roles.** Contributors open issues and pull requests. Maintainers, listed in `MAINTAINERS.md`, review and merge changes, cut releases
  and handle security reports. The lead maintainer is the maintainer marked as lead there, and the lead maintainer appoints new
  maintainers. There is no committer role.
- **Ordinary changes.** While Ingot has one maintainer, the lead maintainer decides. With two or more maintainers, an ordinary change
  needs the approval of one maintainer. No waiting period applies to an ordinary change.
- **Drop-in changes.** A change that touches the drop-in contract stays open for at least 7 days before it merges, unless it is a
  security fix. These rules are on main and are not changed here.
- **Conflict resolution.** If an objection stays open, the lead maintainer decides, and the outcome is recorded in the pull request or
  the issue. A tie is decided the same way, so there is no tie rule of its own.
- **Security reports.** They follow `SECURITY.md` and are not decided in public. Main does not state this in `GOVERNANCE.md`, so the
  sentence is added there.

Dropped from the first draft, because none of them is on main: the committer role, the seven-day lazy consensus for ordinary changes,
and the majority rule under which a tie keeps the current state.

## Decision 2: the upstream relation

It is not repeated. `add-repository-trust-files` states that Ingot is independent of Reposilite and that upstream changes arrive
through `.github/MAINTAINING.md`. The new sections refer to that statement and do not restate it.

## Decision 3: code of conduct text (decided 2026-11-18: keep the text)

The current text is kept. Adopting the CNCF text is not done, for three reasons:

- The CNCF text routes project-agnostic reports to the CNCF Code of Conduct Committee. Ingot is not a CNCF project, so that
  route would send reports to a body with no authority over Ingot.
- The Ingot file has its own scope section that names the OneLiteFeather communities, which the CNCF text does not cover.
- Replacing the text would be a larger change than the task needs, and it would change every reader's expectations of a
  document already in use.

If the project applies to the CNCF, the question is asked again then.

Origin, checked on 2026-11-18: the text does not name the Contributor Covenant and does not use its wording, so the Covenant
attribution does not apply as written. A web search found the same opening sentence in other projects' codes of conduct, which
suggests a shared template, but no source is confirmed. The open question on the origin is task 2.2 in `tasks.md`. The maintainer
decides on an attribution line once a source is named.

## Decision 4: community links (decided 2026-11-18)

GitHub Discussions is the community channel. It is enabled, and `CONTRIBUTING.md` and the README already link it on main. The
documentation link is `https://github.com/OneLiteFeatherNET/ingot/tree/main/docs/gitbook`, the GitBook source in this repository. It
replaces `https://docs.onelitefeather.net/ingot` in the issue template. A documentation site, if one is built later, is a separate
change.

## Decision 5: the issue template

The community contact link is already enabled on main, and the TODO comments at the top of the file are gone. This change changes the
documentation contact link to the GitHub tree of decision 4, and the header comment's sentence about the documentation site to match.
The security contact entry and the commented upstream entry are not changed.
