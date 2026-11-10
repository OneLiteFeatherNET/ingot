# Tasks

Each group is one or more commits of one Conventional Commits type each. This change is documentation only, so every commit
is `docs(support)` and there is no code and no test.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose document-support-policy`: this change and nothing else, branched from the remote main.
  Verify: `openspec validate document-support-policy --strict` passes and the dash grep from the proposal prints nothing.
  The proposal PR is merged before the code PR is opened.

## 2. Maintainer input

- [ ] 2.1 The maintainer confirms or replaces the draft support window from the design (latest minor line fully, previous minor
  line security only for 90 days, older lines nothing), says whether a release branch for the previous line will exist, and names
  the channel for questions. Record the answers in the code PR description, not in a file. Verify: the values in
  `SUPPORT.md` and `.github/SECURITY.md` are the confirmed ones. Group 3 does not start before this is done.

## 3. Support window

- [ ] 3.1 `docs(support)`: add `SUPPORT.md` at the repository root with the parts named in the design and the confirmed window
  from 2.1, and update the `Supported Versions` section of `.github/SECURITY.md` to the same lines with a link to
  `SUPPORT.md`. Do not touch the contact section. Verify: both files name the same lines, `SECURITY.md` no longer says no
  release was published, and the diff of `.github/SECURITY.md` is limited to that section. If `add-repository-trust-files` is
  already merged, rebase onto it first.

## 4. Pull request

- [ ] 4.1 Open the pull request with the title `docs(support): add a support policy and support window`. The description
  follows `.github/PULL_REQUEST_TEMPLATE.md`, carries no session link, and states that no feature depends on support terms.
