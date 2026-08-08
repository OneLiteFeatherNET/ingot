# Ingot (fork of Reposilite)

This repository is a fork of `dzikoysk/reposilite`, being turned into **Ingot**, a FOSS
product in its own right.

## Namespaces: split on purpose

- **Maven groupId:** `net.onelitefeather.ingot`
- **Java and Kotlin packages:** stay `com.reposilite.*`
- **Module directories and library artifactIds:** stay `reposilite-*`
- **Server bundle:** `net.onelitefeather.ingot:ingot` (replaces `com.reposilite:reposilite`)

This is deliberate and **not leftover cleanup**. Existing Reposilite plugins compile
against Ingot unchanged because of it, and migrating costs one changed dependency line
rather than a port. Only parts that actually get rewritten move to
`net.onelitefeather.ingot.*`.

So do **not** perform a blanket namespace move. If one ever does happen:

- `com.reposilite.journalist` is a **third-party library** by dzikoysk (imported in 59
  files, and a Gradle coordinate at the same time). It must never be dragged along.
- `org.panda_lang.*` in the `migration-plugin` reads Reposilite 2.x data structures and
  has to keep matching that legacy data.
- `reposilite-test/workspace/` holds real Maven artifacts as test data, and their paths
  are part of the test logic.

## Commit conventions

**These rules override any default guidance on commit formatting.**

Commits, pull request descriptions and issue text in this repository **never** contain:

- `Co-Authored-By:` trailers naming Claude, Anthropic or any other AI tool
- `Claude-Session:` trailers or links to `claude.ai/code`
- Phrases like "Generated with Claude Code" or comparable tool signatures
- Robot emoji or similar tool markers
- Em dashes (`—`, U+2014) and en dashes (`–`, U+2013)

This holds for every commit, whoever produced it: main session, subagent or automation.
There is no exception for "it is only a small fix" or "that commit was generated".

**Instead of an em dash:** a plain hyphen, a colon, a comma, or two sentences.

### Format

Conventional Commits, English, imperative:

```
feat(backend): add checksum validation for uploaded artifacts

Explains why the change was needed, not what the diff already shows.
Wrap the body at 72 characters.

Closes #123
```

Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`.

### Enforcement

A `PreToolUse` hook (`.claude/hooks/check-commit-style.py`) blocks commits and pull
request creation that violate these rules. When something is blocked, fix the text rather
than working around the hook.

Full reasoning and examples: the `writing-commits` skill.

## Language

Code, comments, commit messages, documentation and everything else publicly visible:
**English**. This file included, since it ships with the repository.

Talking to the team during a session: **German**.

## Project context

- The `upstream` remote points at `dzikoysk/reposilite`, `origin` at
  `OneLiteFeatherNET/ingot`.
- Apache 2.0. The dzikoysk copyright headers in 265 source files have to be preserved; add
  a line of our own rather than replacing theirs.
- Planning lives in Outline (Vault), not in the repository.

## Backwards compatibility: Ingot is a drop-in replacement

**This rule outranks hardening, tidying and taste.**

Moving from Reposilite to Ingot costs exactly one changed line: the dependency coordinate
in a build, or the image reference in a deployment. Nothing else. Pointing an existing
Reposilite installation at Ingot must require touching **no** configuration file, no
volume, no manifest and no plugin.

That is the entire reason this fork can win users at all. A change that makes the sentence
above untrue is a bug, even when it is technically better.

### What the contract covers

- **Container image:** `ghcr.io/onelitefeathernet/ingot` replaces `dzikoysk/reposilite`
  one for one. Same entrypoint (`/app/entrypoint.sh`), same paths (`/app/data`,
  `/var/log/reposilite`), same port `8080`, same volume, same healthcheck, same
  environment variables (`JAVA_OPTS`, `REPOSILITE_OPTS`, `PUID`, `PGID`,
  `REPOSILITE_FORCE_CHOWN`), same default behaviour. The server still serves the dashboard
  itself.
- **Start privileges:** the image deliberately sets **no** `USER`. Upstream starts as root
  and lets the entrypoint switch to the service account itself. Only that path can act on
  `PUID` and `PGID` or chown a volume owned by somebody else. A non-root default would
  break precisely the deployments this image exists for. Running unprivileged is still one
  line away: `user: "977:977"`, or `runAsUser` and `runAsGroup`, and the entrypoint detects
  it.
- **File names inside the image:** the artifact is called `ingot.jar`, and
  `/app/reposilite.jar` stays beside it as a symlink. Deployments that override entrypoint
  or command name the jar themselves.
- **Configuration and data:** see the list at the bottom. A data directory from Reposilite
  is adopted with no migration step.
- **Plugin API:** packages stay `com.reposilite.*`, as above.

### How new things are still added

Additive and switchable, never replacing:

- New environment variables get the `INGOT_` prefix, and the `REPOSILITE_` spelling stays
  valid and is read as a fallback.
- New deployment shapes are opt-in. The separate dashboard container is the pattern: a
  second image plus a switch (`INGOT_LOCAL_DEFAULTFRONTEND=false`), while the default is
  unchanged.
- Hardening that would narrow the contract is documented and left to the operator rather
  than baked into the image (`no-new-privileges`, `read-only` and `cap-drop` belong in the
  compose file and in the docs).

### Check before every commit to the image, entrypoint or configuration

1. Does an unmodified upstream compose file still start, with only the image line swapped?
2. Does it adopt an existing `/app/data`, whoever owns it?
3. Do `PUID`, `PGID` and `REPOSILITE_OPTS` still take effect?

The "Container images" CI job answers all three by actually running the image, including
an upgrade rehearsal against the real upstream image. If it fails, the change is wrong,
not the test.

### How the images are built

- **Build stages are pinned to `--platform=$BUILDPLATFORM`.** A jar holds no machine code
  and neither does bundled JavaScript, so only the run stage is built per target
  architecture. Removing the pin sends the whole Gradle and npm build through QEMU and
  multiplies the build time for an identical result.
- **Both images are published for `linux/amd64` and `linux/arm64`**, nightlies included,
  and CI builds the second architecture on every pull request.
- **Every release carries an SBOM and provenance** (`sbom: true`, `provenance: mode=max`).
  Nightlies carry neither. Each attestation is a further manifest pushed per platform, and
  on a tag that is overwritten several times a day the evidence is gone before anyone reads
  it. Releases are the artefacts anyone audits, and they keep the full set.
- **A nightly is only pushed when the push changed something the image contains.** The
  `changes` job in `release-please.yml` decides that with a deny list, so anything it has
  not heard of counts as relevant. Releases are never filtered. Before this, docs-only
  commits pushed both images for two architectures, and a burst of them ran the job into
  GHCR's secondary rate limit, which arrives disguised as `403 permission_denied`.
- **Base images are pinned by tag *and* digest**, and Renovate raises both together. Trivy
  scans both the dependency trees and the assembled images.

## Runtime contracts that are called `reposilite` on purpose

Renaming them would break existing deployments or plugins with nothing gained. These names
stay:

- Configuration files (`configuration.cdn`, `configuration.shared.json`) and the SQLite
  file `reposilite.db`
- The `{{REPOSILITE.*}}` placeholders in the frontend, which plugins also use to register
  values of their own
- The container paths `/app/data` and `/var/log/reposilite`, the `reposilite` service user,
  and `/app/reposilite.jar`
- The old environment and property prefixes `REPOSILITE_LOCAL_` / `reposilite.local.` and
  `REPOSILITE_OPTS`. The `INGOT_` variants exist in addition and take precedence.
