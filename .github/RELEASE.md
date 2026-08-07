# Release guide

Releases are automated. There is no command a maintainer runs by hand, and there is no
release branch: the version lives in the repository and
[Release Please](https://github.com/googleapis/release-please) owns it.

## How a release happens

1. Commits land on `main` following [Conventional Commits](https://www.conventionalcommits.org/).
   `feat` bumps the minor version, `fix` the patch, and a `!` or a `BREAKING CHANGE:`
   footer the major.
2. Release Please opens or updates a release pull request. It carries the changelog and
   the version bump across `build.gradle.kts`, the frontend manifests and the Compose
   example. Do not edit those version lines by hand.
3. Merging that pull request creates the tag and the GitHub release. The same workflow run
   then publishes the Maven artifacts to `repo.onelitefeather.dev`, attaches the standalone
   `ingot-<version>.jar` to the release, and pushes the container image to
   `ghcr.io/onelitefeathernet/ingot`.

A push to `main` that produces no release still refreshes the `nightly` container image.

Everything above lives in [`release-please.yml`](workflows/release-please.yml) and
[`release-please-config.json`](../release-please-config.json).

## The release pull request needs its CI approved

This repository is still a GitHub fork of dzikoysk/reposilite, so workflow runs on pull
requests land in `action_required` and wait for a maintainer to press "Approve and run".
That includes the release pull request Release Please opens, which is why its checks look
stuck rather than failing.

Two ways out, both in Settings → Actions → General: relax "Fork pull request workflows", or
leave the fork network so the repository stands on its own. The second is irreversible, and
the attribution does not depend on it: that lives in NOTICE, the README and the git history.

## What a release needs

- `ONELITEFEATHER_MAVEN_USERNAME` and `ONELITEFEATHER_MAVEN_PASSWORD` repository secrets
  for the Maven publish. The container push authenticates with the built-in `GITHUB_TOKEN`
  and needs no secret.

## Rebuilding a container image

The `Release Please` workflow takes a `workflow_dispatch` with a `container_version` input.
It runs only the container job, for the version you name, and leaves the release itself
alone.

## Adding a file whose version has to follow

Add it to `extra-files` in `release-please-config.json` and put an
`x-release-please-version` marker on the line holding the version, or use the `json`
updater with a JSON path. Keep markers out of snippets that readers copy: a stale example
version is a smaller problem than a confusing one.
