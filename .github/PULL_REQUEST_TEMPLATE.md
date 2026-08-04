# Description

<!-- What does this change do, and why is it needed? Link the issue it resolves, e.g. "Closes #123". -->

## Type of change

<!-- Keep the one that applies, delete the rest. -->

- [ ] `fix` - bug fix
- [ ] `feat` - new feature
- [ ] `refactor` - internal change without behaviour change
- [ ] `perf` - performance improvement
- [ ] `docs` - documentation only
- [ ] `test` - tests only
- [ ] `build` / `ci` / `chore` - tooling, dependencies, pipelines
- [ ] Breaking change (configuration, API or behaviour that existing deployments rely on)

## How was this tested?

<!--
Describe the verification, not the intent. For example:
- `./gradlew test` (or the specific test class you added)
- manual steps: deployed an artifact with `mvn deploy`, checked the file browser
Paste relevant output if it helps the reviewer.
-->

## Checklist

- [ ] Commit messages follow the Conventional Commits convention documented in `CLAUDE.md` (English, imperative, one of `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`)
- [ ] Tests cover the change, or the change is not testable and I explained why above
- [ ] `./gradlew build` passes locally
- [ ] Documentation and configuration samples are updated if the change is user-facing
- [ ] Existing copyright headers are preserved; new files carry the appropriate header
- [ ] No credentials, tokens or personal data are included in the diff
