# Tasks

Each group is one or more commits of one Conventional Commits type each. The check ships under `ci(dco)`. The
documentation commit ships under `docs(contributing)`. No group changes an operator-facing configuration key,
environment variable, image or manifest, so no page under `docs/gitbook/` changes.

The unit tests are plain Python with the standard library and `unittest`. Each test creates its own repository in a
`tempfile.TemporaryDirectory` with fixed author and committer dates, creates the upstream ref locally, and calls the
script. They read no clock, make no network call and share no state. They live in `.github/scripts/tests/`, which the
`images` job of `ci.yml` runs on every pull request.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-dco-check`: this change and nothing else. Verify:
  `openspec validate add-dco-check --strict` passes and the dash grep from the proposal prints nothing.

## 2. Check behaviour, tests first

- [ ] 2.1 `ci(dco)`: add `.github/scripts/tests/test_check_dco.py` with failing tests, one behaviour each: a commit with a
  sign-off whose address equals the author address passes; a sign-off for another address fails; a commit without
  sign-off fails in `enforce` and warns in `report`; a commit with a cherry-pick trailer naming an ancestor of the local
  upstream ref passes; a trailer naming a sha that is not an ancestor fails; a trailer when no upstream ref exists is
  reported as unverified; a bot pull request passes all commits; an author name equal to the upstream maintainer without
  a trailer fails; a merge commit is skipped; an unknown `DCO_MODE` value fails with the value in the message. Verify:
  `python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v` fails on main with an import error
  naming `check_dco`, and each test name states one behaviour.

## 3. Check script and workflow

- [ ] 3.1 `ci(dco)`: add `.github/scripts/check_dco.py` (standard library only) with the behaviour of `design.md`, and make
  the tests of 2.1 pass. Verify: the whole discover run is green, and the script prints an annotation per failing commit.
- [ ] 3.2 `ci(dco)`: add `.github/workflows/dco.yml`: triggers `pull_request` into `main`, `permissions: contents: read`,
  checkout with `fetch-depth: 0` at the pull request head, a blobless fetch of `https://github.com/dzikoysk/reposilite.git`
  `main` with `continue-on-error: true`, and the script with the base and head shas, the bot flag from
  `github.event.pull_request.user.type` and the mode from `vars.DCO_MODE`. Every `uses:` names a commit SHA with a
  version comment. Verify: the YAML parses, a test in 2.1's module that reads the workflow file asserts the trigger, the
  permission and the SHA form, and the check passes on this pull request in `report` mode.

## 4. Documentation

- [ ] 4.1 `docs(contributing)`: update the "Sign your commits" section of `.github/CONTRIBUTING.md` with the check, the
  report and enforce modes, the cherry-pick exemption and `git rebase --signoff <base>` for existing branches. Verify:
  the section names every command of the spec scenario, and the existing Apache and DCO wording stays unchanged.
- [ ] 4.2 `docs(contributing)`: replace the TODO under "Sign-off" in `.github/MAINTAINING.md` with the description of the
  check and of `git cherry-pick -x`. Verify: the file no longer contains the string `TODO(onelitefeather): add the check`,
  and the divergence table is unchanged.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `ci(dco): check sign-off on pull request commits`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of a contributor whose
  change reaches main without a record of who certified it, and of the upstream sync that must not be blocked, without
  workflow internals beyond what a reviewer needs. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the
  body has no em or en dash.
