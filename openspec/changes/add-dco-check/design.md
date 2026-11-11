# Design

## Decisions

1. **A workflow step, not the DCO GitHub App.** The check is one workflow file and one script in this repository.
   The alternative, the DCO app, needs an installation on the `OneLiteFeatherNET` organisation by an owner, it holds a
   token with write access to commit statuses on every pull request, and its behaviour lives outside the repository. It
   also cannot tell that a commit is a cherry-pick of an upstream commit: its configuration covers authors, bots and
   merge commits, not ancestry. The workflow runs on the `pull_request` event with `contents: read` only, needs no
   secret, and its rules sit next to the policy they enforce, so a reviewer reads one diff to change the rule.

2. **Cherry-picks are recognised by their trailer and checked against upstream.** `.github/MAINTAINING.md` tells
   maintainers to cherry-pick with `-x`, which appends `(cherry picked from commit <sha>)`. The check accepts a commit
   that carries that trailer when the named commit is an ancestor of `upstream/main`. Two other signals were rejected:
   - *Author identity alone.* A rule "author is the upstream maintainer" is spoofable by setting an author address, and
     it is also incomplete. In the last 12 months 51 of the 57 commits by the upstream maintainer's identity on `main`
     carry no trailer, and upstream has other contributors whose commits arrive the same way.
   - *Patch identity.* Comparing the patch of the commit with the upstream patch fails on every conflict resolution,
     which is the normal case for this fork (see the divergence table in `.github/MAINTAINING.md`).

   The trailer alone is also not trusted: a fake trailer names a sha that is not an ancestor of upstream and fails. The
   ancestry check runs against a blobless fetch of `https://github.com/dzikoysk/reposilite.git`, which is public and read
   only. If that fetch fails, a trailer cannot be verified, and the commit is reported as unverified.

3. **Bot pull requests are exempt as a whole, by the account type of the pull request author.** GitHub sets
   `pull_request.user.type` to `Bot` for accounts of GitHub Apps, such as Dependabot, Renovate and the release bot that
   opens the release pull request with an app token. A human cannot open a pull request under such an account, so the
   exemption cannot be claimed by a commit author line. Commit authors are never an exemption.

4. **Identity of the sign-off.** A `Signed-off-by` line counts when its address equals the author address of the commit,
   case-insensitively. A sign-off for somebody else does not certify this commit. `git commit -s` writes the configured
   address, so contributors who follow CONTRIBUTING meet this rule without a further step.

5. **Merge commits are skipped.** `git rev-list --no-merges` excludes them. Merge commits carry no contributed content
   of their own, and the commits they bring in are checked on their own.

6. **Squash merges.** The check reads the commits of the pull request head. Squash-merging creates a new commit on
   `main` whose message GitHub composes from the pull request title and description, and, depending on the repository's
   default squash setting, from the commit messages of the pull request. The check does not read that squash commit.
   The evidence of the sign-off is the pull request commit, which stays in the pull request record. Whether the squash
   message should keep the `Signed-off-by` lines is an open question below; the check's result does not depend on it.

7. **Report, then enforce.** A repository variable `DCO_MODE` selects the behaviour. An unset value or `report` makes the
   step pass and write one `::warning` annotation per failing commit and a job summary. `enforce` makes the step fail. Any
   other value fails the step, so a typing mistake in the variable cannot silently weaken or break the check.
   The switch from report to enforce is recorded in `.github/CONTRIBUTING.md` and announced in the release notes.

8. **Required status check.** Not required by this change. After the switch to `enforce` and one release cycle without
   incidents, a maintainer adds the check to the branch protection of `main`. This is a repository setting outside the
   files of this change.

## Check behaviour

| Commit | Result in `report` | Result in `enforce` |
|---|---|---|
| Sign-off whose address equals the author address | pass | pass |
| Cherry-pick trailer naming an ancestor of `upstream/main` | pass | pass |
| Cherry-pick trailer naming a sha that is not an ancestor, or no upstream available | warning | fail |
| No sign-off and no trailer, pull request by a human | warning | fail |
| Any commit of a pull request opened by a Bot account | pass | pass |
| Merge commit | skipped | skipped |

## Open questions with defaults

- **Switch date.** Default: no earlier than four weeks after the merge of this change, and only after the first upstream
  sync pull request that followed the merge has used `git cherry-pick -x`. The maintainer confirms the date in the
  announcement. The date stays open until then.
- **Squash message.** Default: the repository setting is not changed, so the squash message is what GitHub composes
  today. A maintainer decides whether it should keep `Signed-off-by` lines of the pull request commits.
- **Bots that commit under a human pull request.** Default: not exempt. A bot account that pushes commits into a human
  pull request has to sign off, or the pull request comes from the bot account itself.

## Files

- `.github/workflows/dco.yml`: triggers `pull_request` into `main` and the job with `permissions: contents: read`. Steps:
  checkout with `fetch-depth: 0` at the pull request head; a blobless fetch of the upstream `main` branch with
  `continue-on-error: true`; the script with the base and head shas, the bot flag from `github.event.pull_request.user.type`
  and the mode from `vars.DCO_MODE`.
- `.github/scripts/check_dco.py`: standard library only. It reads the commits with `git log --no-merges` in a range,
  parses the trailers, runs `git merge-base --is-ancestor` against the upstream ref, prints annotations and writes the
  summary. No network call; the upstream ref is an argument.
- `.github/scripts/tests/test_check_dco.py`: unit tests. Each test builds a temporary repository with `git init` in a
  `tempfile.TemporaryDirectory`, makes commits with fixed dates and identities, creates the upstream ref locally, and calls
  the functions of the script. No test reads the clock or the network.
- `.github/CONTRIBUTING.md`: the "Sign your commits" section gains the check, the mode, the cherry-pick exemption, and
  `git rebase --signoff <base>` for an existing branch, which the section already names.
- `.github/MAINTAINING.md`: the TODO under "Sign-off" is replaced by the description of the check and its exemption.

## Risks

- A contributor who cherry-picks without `-x` gets a failing check in `enforce`. The fix is documented: sign off the
  cherry-pick with `-s`, which makes the fork maintainer the certifier of the change, or repeat the cherry-pick with `-x`.
- The trailer can be typed by hand. It is accepted only when it names an upstream ancestor, and the diff is reviewed as
  any other diff, so a forged trailer on a commit that is not upstream content is a review finding, not a silent pass.
