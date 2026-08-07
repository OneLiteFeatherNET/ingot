# Contributing

This document describes how to contribute to Ingot, the Maven artifact repository maintained by OneLiteFeather.
When contributing to this repository, please first discuss the change you wish to make via
[issue](https://github.com/OneLiteFeatherNET/ingot/issues), as every pull request should address an issue.

Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite) by dzikoysk and is licensed under the
Apache License 2.0. See the `NOTICE` file in the repository root for attribution details.

## Creating an issue

Creating an issue to ask a question is fine. You can also reach the maintainers through our community channel:

TODO(onelitefeather): community link

Remember to include enough information if you're reporting a bug, so the problem can be reproduced.

## Creating a PR

Every pull request will be considered.

### Sign your commits

Ingot uses the [Developer Certificate of Origin](https://developercertificate.org/). There
is no contributor licence agreement to sign: instead, every commit carries a line stating
that you have the right to submit it under the project's licence.

```
Signed-off-by: Your Name <your.email@example.com>
```

`git commit -s` adds it for you. The name and address have to be real ones you can be
reached at; anonymous contributions cannot be signed off.

By signing off you certify the [DCO 1.1](https://developercertificate.org/) in full, which
in short means the contribution is yours to give, or you received it under a compatible
licence, and you are fine with it being distributed under the Apache License 2.0 as part of
this project.

Missing a sign-off is fixable: `git commit --amend -s` for the last commit, or
`git rebase --signoff <base>` for a branch, then force push.

### How to increase the chance of having your PR merged

1. If a related issue does not exist, create a new one to start a discussion about it.
2. Try to write tests for your change. There are a lot of examples in the `test` directories.
3. Format your code so it looks like the rest of the sources, and avoid unrelated changes such as
   drive-by refactors that are not part of the subject of your PR.
4. Keep the Apache License headers of existing files intact. If you substantially change a file that
   carries an upstream copyright header, add your own line instead of replacing the existing one.

### Commit messages

Commit messages, pull request descriptions and issue texts follow the conventions documented in
[`CLAUDE.md`](../CLAUDE.md) in the repository root: [Conventional Commits](https://www.conventionalcommits.org/),
written in English, in the imperative mood.

Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`.

Example:

```
feat(backend): add checksum validation for uploaded artifacts

Explains why the change was needed, not what the diff already shows.
Wrap the body at 72 characters.

Closes #123
```

## Running

The project has several modules that use different tech stacks.
Visit the README.md of each module to see details about how to run it and work with it:

* [Backend](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-backend) - sources of the main application
  * [Plugins](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-plugins) - plugin system related subprojects
* [Frontend](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-frontend) - sources of the default frontend in Vue
* [Site](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-site) - sources of the documentation website

## Maintainers

Ingot is a fork, so keeping it reconcilable with upstream is part of maintaining it.
[MAINTAINING.md](MAINTAINING.md) describes how upstream changes are pulled in, which areas
deliberately diverge, and which names are kept for compatibility rather than left behind.

## Code of Conduct

By participating in this project you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).
