# Governance

This file describes how decisions about Ingot are taken, how people become and stop being maintainers, and how
Ingot relates to the upstream project it forks. The people involved are listed in [MAINTAINERS.md](MAINTAINERS.md).

## Roles

- **Contributors** open issues and pull requests. No role is needed.
- **Maintainers** review and merge changes, cut releases and handle security reports. Their list is
  [MAINTAINERS.md](MAINTAINERS.md).

## How decisions are taken

- An ordinary change is decided in its pull request. It merges once one maintainer has approved it and no other
  maintainer has an open objection.
- A change that alters what operators configure, the container image, or the compatibility with Reposilite
  installations and plugins needs the approval of two maintainers.
- If an objection stays open, the maintainers decide by majority vote. The outcome is recorded in the pull request
  or the issue where the question was raised.

## Becoming and stopping being a maintainer

- A current maintainer proposes a contributor by a pull request that adds an entry to [MAINTAINERS.md](MAINTAINERS.md).
  The proposal passes with a majority of the current maintainers.
- A maintainer steps down by a pull request that removes their entry.
- A maintainer who has been inactive for twelve months may be removed by a majority vote of the other maintainers,
  announced in the pull request that removes their entry.

## Relation to upstream Reposilite

Ingot is an independent product. It is not affiliated with, and not endorsed by, [Reposilite](https://github.com/dzikoysk/reposilite)
or its authors. Ingot is a fork of Reposilite and keeps its Apache License 2.0 and the upstream copyright notices.

- Ingot has its own version line, which starts at 1.0.0. An upstream release is not an Ingot release. The reasoning
  is in [VERSIONING.md](VERSIONING.md).
- Upstream changes come in one commit at a time through the cherry-pick policy in
  [.github/MAINTAINING.md](.github/MAINTAINING.md). Upstream history is not merged wholesale.

## Changes to this file

This file changes by pull request, and the same rules as above apply to that pull request.
