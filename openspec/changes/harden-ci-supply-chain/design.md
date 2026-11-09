# Design

## Pinning mechanism: a Renovate preset, not manual bumps

Pinning 18 references by hand is a one-time edit. Keeping them pinned is the real task: without a rule, the next
Renovate run turns a SHA back into a tag bump, or a maintainer copies a tag from an example. The chosen mechanism is
Renovate's GitHub Actions digest preset in `renovate.json`. It rewrites a tag to a SHA with the version in a comment,
which is the form the repository already uses for `gradle/actions` and `docker/*`.

Not chosen: a workflow linter as the gate (zizmor or actionlint). It would catch a tag-only reference at review time,
but it adds a required check and a tool to maintain. It stays a follow-up, as the proposal says.

## Interaction with the existing grouping

The shared preset groups the reusable workflows of `OneLiteFeatherNET/workflows` as `OneLiteFeather workflows` and
automerges minor updates of them. The GitHub Actions group automerges minor updates only.

With digest pins, a release changes the SHA and the comment together, so Renovate reports it as a minor update when the
version moves. A change of SHA that keeps the same tag (a re-tag or a digest refresh) is a digest update, which neither
automerge rule matches, so it stays a pull request for a human to read. That is the intended behaviour: a SHA changing
under an unchanged tag is the case this change exists to catch.

This is an expectation derived from the preset rules, not a run. Task 3.2 verifies it with a Renovate dry run before the
code pull request merges. If the dry run shows a different grouping, the grouping rule is adjusted in that same pull
request.

## Scorecard reports, it does not gate

The Scorecard job uploads results to code scanning and the OpenSSF API. It fails only when the tool itself fails, not
when a check scores low. A gate would need branch protection to be in place first, which is a repository setting outside
this change.

## Token scopes: explicit blocks, no narrowing of existing writes

Every job gets a `permissions` block so that a reviewer reads the scope where the job is defined. The write scopes that
exist today (release-please, jar, chart, container) are kept as they are. Narrowing them would change how releases are
published and is not needed to make the scope visible.
