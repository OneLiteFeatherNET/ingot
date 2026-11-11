# Proposal

## Why

`.github/CONTRIBUTING.md` asks every contributor to sign off their commits under the Developer Certificate of Origin,
and `.github/MAINTAINING.md` says the sign-off "is not enforced by CI yet". A contributor who reads the guide can
therefore open a pull request without a sign-off, and the maintainers find out only when they read the diff. The
licence statement that the sign-off provides is what lets Ingot distribute a contribution under Apache 2.0 with a
record of who certified it, and an unchecked rule gives that record only for the contributors who happen to follow it.

The check has to be narrower than "every commit has a sign-off". Ingot is a fork of Reposilite, and the fork is kept
in step with upstream by cherry-picking upstream commits (`.github/MAINTAINING.md`, "Syncing"). Upstream does not use
the DCO, so those commits carry no sign-off, and a plain rule would make syncing impossible.

Facts checked against origin/main (`db0717b2b`):

- No DCO workflow exists in `.github/workflows/`. `ci.yml`, `release-please.yml` and `scorecard.yml` do not check
  sign-offs.
- The last 12 months of `main` hold 649 commits. 51 of them carry a `Signed-off-by` line. 57 commits are authored by the
  upstream maintainer's identity `dzikoysk <dzikoysk@dzikoysk.net>`, and 6 of those carry a
  `(cherry picked from commit ...)` trailer. The other 51 upstream commits have no trailer and no sign-off.
- `upstream/main` is reachable as a remote (`upstream` points at `dzikoysk/reposilite`), and the upstream commit
  `161d9ae817ef71a433e24ee62cd8f963ada132b8` that `release-please-config.json` uses as bootstrap is an ancestor of it.
- Bots open pull requests here: 42 commits of the last 12 months are by `dependabot[bot]`, and Release Please opens the
  release pull request with a GitHub App token. `renovate.json` is present as well.

## What Changes

- **C1 A check on pull requests.** A new `.github/workflows/dco.yml` runs on `pull_request` into `main`. It checks every
  non-merge commit of the pull request, and it runs with `contents: read` only, on the `pull_request` event, so a fork's
  token never gets a write scope.
- **C2 Exemptions, by evidence, not by name.** A commit is accepted when it carries a `Signed-off-by` line whose address
  equals the commit's author address, or when it names an upstream commit with `(cherry picked from commit <sha>)` and
  that commit is an ancestor of `upstream/main`. A pull request opened by a GitHub App account (`user.type == "Bot"`),
  such as Dependabot, Renovate or the release bot, is accepted as a whole. Author names are never an exemption.
- **C3 Report, then enforce.** A repository variable `DCO_MODE` selects the behaviour. The first mode is `report`: the
  check passes, writes a warning per commit and a job summary. The second mode is `enforce`, which fails the check. The
  switch date is an open question; the default is in `design.md`.
- **C4 Contributor documentation.** `.github/CONTRIBUTING.md` explains the check, the cherry-pick exemption and how to
  add a sign-off to an existing branch. `.github/MAINTAINING.md` replaces its TODO with the description of the check and
  its exemption, and states that `git cherry-pick -x` is the way to keep the exemption.
- **C5 Tests.** A unit test module for the check script runs with the existing `images` job of `ci.yml`.

### Out of scope

- The DCO GitHub App. It is discussed in `design.md` and not used.
- Making the check a required status check. That is a repository setting that a maintainer changes after the switch to
  `enforce`.
- Rewriting history to add sign-offs to the existing 649 commits. The check looks at the commits of new pull requests.
- The governance files, `SECURITY.md` and the contact addresses, which are a separate change set.
- Changes to the DCO text itself. CONTRIBUTING keeps the DCO 1.1 wording it has.

## Capabilities

### New Capabilities

- `developer-certificate-of-origin`: the sign-off check of pull request commits, its exemptions for upstream cherry-picks
  and for bot pull requests, its report and enforce modes, and the contributor guidance that describes them.

## Impact

- **Type and scope.** The change ships under `ci(dco)`. The scope names the check, so release-please and the changelog
  read the commit as a CI change. The pull request title, squash-merged onto main, is:

  `ci(dco): check sign-off on pull request commits`

  Inside the pull request each commit keeps one type: the check commits use `ci(dco)`, and the documentation commit
  uses `docs(contributing)`. The squash commit on main carries only the title.
- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, image, manifest or plugin
  changes for an operator. Contributors who open a pull request without a sign-off see a failing check after the switch
  to `enforce`, which is a change for contributors and is stated in CONTRIBUTING.
- **Behaviour that changes.** Every pull request gets one more check. It reads the commits of the pull request. Only
  when a cherry-pick trailer is present does it also fetch a blobless copy of the upstream history, which takes longer
  than the rest of the check.
- **Code.** `.github/workflows/dco.yml`, `.github/scripts/check_dco.py`, `.github/scripts/tests/test_check_dco.py`,
  `.github/CONTRIBUTING.md` and `.github/MAINTAINING.md`.

## Verification

- The unit test module is written first and fails on main, because the script does not exist yet. Every test creates
  its own temporary git repositories, and the upstream history is a local ref that the test creates, so no test needs the
  network.
- On the pull request, the check passes for a signed commit, a bot pull request and a cherry-pick, and reports the
  failing commit for an unsigned one in `report` mode.
- `openspec validate add-dco-check --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-dco-check`
  prints nothing.
