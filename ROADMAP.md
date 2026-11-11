# Roadmap

This page lists the changes that are proposed but not yet merged into `main`. Each entry links the proposal that
describes the change in full, and says in one line what the change does for you.

Dates are not promised. Planning detail lives outside this repository, in the project's planning tool. Once a
change is merged, it leaves this list.

## Product

| Change | What it does for you |
|:--|:--|
| [`add-chart-upgrade-rehearsal`](openspec/changes/add-chart-upgrade-rehearsal/proposal.md) | Moving a release from the upstream Helm chart to the Ingot chart with one changed line, rehearsed in CI |
| [`add-openapi-diff-gate`](openspec/changes/add-openapi-diff-gate/proposal.md) | Pull requests that break the REST API fail unless they carry a marker |
| [`add-repository-format-spi`](openspec/changes/add-repository-format-spi/proposal.md) | Repository formats beyond Maven, with Maven 2 as the default |
| [`add-repository-policy-fields`](openspec/changes/add-repository-policy-fields/proposal.md) | Version policy, a read-only flag and a write policy view per repository |
| [`add-structured-logging`](openspec/changes/add-structured-logging/proposal.md) | JSON log output, log levels from the environment and an access log |
| [`fix-entrypoint-signal-handling`](openspec/changes/fix-entrypoint-signal-handling/proposal.md) | SIGTERM reaches the server process, so shutdown completes its flushes and releases |
| [`gate-plugin-abi`](openspec/changes/gate-plugin-abi/proposal.md) | Plugin API compatibility enforced in CI, where today it is reported |

## Documentation and governance

| Change | What it does for you |
|:--|:--|
| [`document-support-policy`](openspec/changes/document-support-policy/proposal.md) | Where help is found and which release lines receive fixes |
| [`add-repository-trust-files`](openspec/changes/add-repository-trust-files/proposal.md) | Maintainers, governance, adopters and the security contacts |
