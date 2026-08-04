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

TODO(onelitefeather): decide on the contribution agreement model (no CLA, DCO sign-off, or a
CLA) and document it here. This has to be settled before the first outside pull request is
merged, because changing it afterwards would require re-licensing contributions that were
already accepted under the previous terms.

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

## Code of Conduct

By participating in this project you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).
