# Tasks

These tasks wait for the decision on the wire format of the challenge (option a, b or c in `design.md`). They also wait
until a plugin needs the challenge. Nothing is started before both.

Every commit follows its own Conventional Commits type. The tests are committed first, as `test(identity)`, and seen to
fail before the production commit, which is `feat(identity)`. Every test follows F.I.R.S.T.: no `Thread.sleep`, the clock
is an injected `java.time.Clock`, a temporary directory per test, and no shared mutable static state.

## 1. Implementation

Depends on pull request 1 of `add-login-protection` (per-account throttle), merged into the remote main branch.

- [ ] 1.1 `test(identity)`: failing tests: with no provider registered, a delayed dashboard login is answered `429` with no
  challenge; with a provider registered, it is asked only for a delayed dashboard login and never for Basic or Bearer; a
  verified challenge lets one password check through, and a wrong password continues the delay; a plugin compiled against the
  previous plugin API still loads (the default methods). Verify: the tests fail before 1.2.
- [ ] 1.2 `feat(identity)`: add `LoginChallengeProvider` to the plugin API with default methods, the selection in `PluginLoader`
  (at most one, the first by load order, with a warning when several are registered), and the branch in `LoginService.login`.
  Verify: 1.1 passes and `gate-plugin-abi` reports only additive changes.
- [ ] 1.3 `docs(identity)`: document the interface for plugin authors in `docs/gitbook/`, with the statement that Ingot ships no
  provider and calls no third-party service. Verify: the page says so in its first paragraph.

## 2. Open the pull request

- [ ] 2.1 Open the code pull request against `main` with the title `feat(identity): add a login challenge extension point for plugins`,
  after pull request 1 of `add-login-protection` is merged, with the sections of `.github/PULL_REQUEST_TEMPLATE.md` filled in.
  Verify: `gh pr view` shows the title and the base is `main`.
