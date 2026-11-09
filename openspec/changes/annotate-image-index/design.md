# Design

## Where the base image comes from

The base image is pinned in each Dockerfile by tag and digest, and Renovate raises both together. Two ways to fill
`base.name` and `base.digest` were considered:

- **Hard-code the values in the workflow.** Simple, but a Renovate bump in the Dockerfile would leave the annotation stale until
  someone edits the workflow too. The annotation would then describe a base the image no longer uses.
- **Read the run stage `FROM` line at build time (chosen).** The Dockerfile stays the single source. The step strips any
  `--platform` flag, splits off `@sha256:...`, and writes the name and digest as step outputs. Only the run stage is read, because
  only the run stage ends up in the image.

## Annotation levels

The metadata action's default level is `manifest`, so the index would get nothing. `DOCKER_METADATA_ANNOTATIONS_LEVELS` is set to
`manifest,index` so that the index and each platform manifest carry the same set. The labels stay as they are, because labels live
in the image config and annotations live beside it.

## Check against the pushed index

The check reads the raw index after the push instead of the build output, because the build output does not show what the registry
stored. The script takes a file, so the tests can use fixture files in a temporary directory and no test contacts a registry.
