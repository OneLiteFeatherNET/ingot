# Design

## Warn level by rule overlay, not by flag

The roadmap says ESLint "starts at warn level". The configuration sets every rule to `error`, and `--max-warnings` only
counts warnings, so no command-line flag turns an error into a warning. The phase is therefore built into
`eslint.config.js`: a helper maps each rule's severity to `warn`, including the array form, and it is applied to the
recommended sets and the local rules. The flip back to `error` is the same helper removed, which keeps the diff of that
later change small.

Alternative considered: leave the configuration alone and gate only on the test job. Rejected, because the lint step
would then never run in CI and the warning count could not be seen trending down.

## One new job, not steps in `Licenses`

The frontend steps could be appended to the `licenses` job, which already installs the npm tree. A separate `frontend` job
was chosen so that a failing test shows as its own check and a licence failure is not hidden behind it. The cost is one
more `npm ci`, which the npm cache makes cheap.

## Tests run with `npm test`

`npm test` is `vitest run`, a single non-watch run, and is what the other proposals already use. Its configuration is
`reposilite-frontend/vitest.config.js`, which scopes the run to `src/**` and `server/**`.
