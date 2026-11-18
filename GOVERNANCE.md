# Governance

This file describes how decisions about Ingot are taken, how people become and stop being maintainers, and how
Ingot relates to the upstream project it forks. The people involved are listed in [MAINTAINERS.md](MAINTAINERS.md).

## Roles

- **Contributors** open issues and pull requests. No role is needed.
- **Maintainers** review and merge changes, cut releases and handle security reports. Their list is
  [MAINTAINERS.md](MAINTAINERS.md). The **lead maintainer** is the maintainer marked as lead there.

## Drop-in changes

A change touches the drop-in contract when it alters the container image, the configuration, the compatibility with
Reposilite installations, or the plugin API. Operators and plugin authors rely on these, so such a change stays open
for at least 7 days before it merges, which gives them time to object. Security fixes are exempt from this waiting
period.

## How decisions are taken

While Ingot has one maintainer, the lead maintainer decides:

- An ordinary change is decided by the lead maintainer. A pull request from outside the project merges only with
  the lead maintainer's approval.
- A drop-in change is open for at least 7 days before it merges.

Once Ingot has two or more maintainers:

- An ordinary change needs the approval of one maintainer.
- A drop-in change needs the agreement of the second maintainer, or no objection within 7 days.
- If an objection stays open, the lead maintainer decides. The outcome is recorded in the pull request or the issue
  where the question was raised.

Security reports follow [.github/SECURITY.md](.github/SECURITY.md) and are not decided in public.

## Becoming and stopping being a maintainer

- The lead maintainer appoints new maintainers. Once Ingot has two or more maintainers, a new maintainer needs the
  agreement of all existing maintainers. An appointment is made by a pull request that adds an entry to
  [MAINTAINERS.md](MAINTAINERS.md).
- A maintainer steps down by a pull request that removes their entry.
- A maintainer who has been inactive for twelve months becomes emeritus. An emeritus maintainer is recognised for
  their work but has no rights in the project.
- If the lead maintainer is inactive for twelve months, the administrators of the OneLiteFeather GitHub organisation
  take over the project.

## Relation to upstream Reposilite

Ingot is an independent product. It is not affiliated with, and not endorsed by, [Reposilite](https://github.com/dzikoysk/reposilite)
or its authors. Ingot is a fork of Reposilite and keeps its Apache License 2.0 and the upstream copyright notices.

- Ingot has its own version line, which starts at 1.0.0. An upstream release is not an Ingot release. The reasoning
  is in [VERSIONING.md](VERSIONING.md).
- Upstream changes come in one commit at a time through the cherry-pick policy in
  [.github/MAINTAINING.md](.github/MAINTAINING.md). Upstream history is not merged wholesale.

## Changes to this file

This file changes by pull request, and the same rules as above apply to that pull request.
