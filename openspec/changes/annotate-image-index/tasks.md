# Tasks

Each group is one or more commits, each with one Conventional Commits type. Tests are written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network and no registry
access, a temporary directory per test with fixture files, and assertions with messages. The check script takes a file path, so
no test pulls an image.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose annotate-image-index`: this change and nothing else. Verify:
  `openspec validate annotate-image-index --strict` passes and the dash grep from the proposal prints nothing.

## 2. Check script

- [ ] 2.1 `test(docker)`: write failing tests for `.github/scripts/check-image-annotations.sh` as a function of a raw index file
  and the expected values: an index with all five annotations and both base references passes; a missing annotation fails and
  names the key; an empty value fails; a missing `base.digest` fails; a file that is not JSON fails with a message; an unknown
  argument fails with usage. Each test writes its fixtures into its own temporary directory. Verify: they fail because the script
  does not exist yet.
- [ ] 2.2 `ci(docker)`: add the script so the tests of 2.1 pass. It reads `docker buildx imagetools inspect --raw` output from a
  file and checks the index and every platform manifest it lists. Verify: the tests of 2.1 pass.

## 3. Workflow

- [ ] 3.1 `ci(docker)`: in `.github/workflows/release-please.yml`, read the run stage `FROM` line of both Dockerfiles in a step and
  write the base name (fully qualified, without digest) and the base digest to the step outputs. Extend the two metadata steps with
  `DOCKER_METADATA_ANNOTATIONS_LEVELS: manifest,index` and an `annotations` block that holds the five existing values and the two
  base references. Pass `annotations: ${{ steps.meta.outputs.annotations }}` (and the dashboard equivalent) to all four
  build-and-push steps, keeping each retry `with:` block identical to its first attempt. Verify: `actionlint` is clean on the
  file and the diff touches only the build steps.
- [ ] 3.2 `ci(docker)`: after the server and dashboard pushes, run the check script on the pushed server and dashboard references
  and on each pushed version tag. Verify: on a `workflow_dispatch` run the job log shows both indexes passing the check.

## 4. Docs

- [ ] 4.1 Not applicable. No configuration key, environment variable or manifest changes, so no page under `docs/gitbook/` is
  updated. Verify: `git diff --stat origin/main -- docs/gitbook` is empty for this change.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `ci(docker): add oci annotations to the image index and manifests`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of an operator inspecting a tag and
  finding the index empty, without workflow step names. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body has no
  em or en dash.
