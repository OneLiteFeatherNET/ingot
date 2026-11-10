# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui` unless a group says
otherwise. Tests are written first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps
or real timers (the poller takes an injected timer, clock and `fetch`), no network, no shared mutable state, a fresh component
and a fresh fake API per test, and assertions with messages. Runs are targeted: `npx vitest run <path>` in `reposilite-frontend`,
through `heavy` when more than one file runs. Tasks 3 to 6 start only after `add-nexus-import` is merged and its API matches design D3, and task 4.2 needs the
`enabledPlugins` field of `add-plugin-ui-extension-point`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-nexus-import-wizard`: this change and nothing else. Verify:
  `openspec validate add-nexus-import-wizard --strict` passes and the dash grep from the proposal prints nothing.

## 2. Contract check

- [ ] 2.1 Compare design D1 and D3 with the merged `add-plugin-ui-extension-point` (the `enabledPlugins` field) and
  `add-nexus-import` (routes, job states, pause reasons, field names). Where they differ, the other change wins: adjust the module in
  3.2 and list the differences in the pull request. If Q1 is answered differently from its default, stop and ask the maintainer.

## 3. API module, poller and state

- [ ] 3.1 `test(ui)`: write failing tests for the poller: first interval 2 seconds, growth by 1.5 times to a 15 second cap while
  the answer is unchanged, reset on change, stop when the job is finished, pause while hidden and one poll on show, retry on the cap
  after a `5xx`, stop on `403` and `404`, `401` handed to the sign-in hook. Use fake timers and an injected visibility source.
  Verify: they fail on the missing module.
- [ ] 3.2 `feat(ui)`: add the poller and `migrationApi.js` with one function per route of design D3, returning parsed problem
  bodies on failure. Verify: the tests of 3.1 pass and the API module has no import of the step components.
- [ ] 3.3 `test(ui)`: write failing tests for the step gating function as a pure function of the page state and the job list: which
  step is open, the step chosen after a reload from the jobs, redirect target for a closed step, stale acceptance by fingerprint
  (selection, target names, conflict policy), Sync disabled reasons. Verify: they fail before 3.4.
- [ ] 3.4 `feat(ui)`: add the gating function, the wizard store (view state, mapping and acceptance in memory) and the password
  holder (a module variable outside reactive state, dropped on leaving the route, sign-out, `401` and on request). Verify: the tests of
  3.3 pass and no test finds the password in any store, storage or URL.

## 4. Route and mounting

- [ ] 4.1 `test(ui)`: write failing tests: with `nexus-import` in `enabledPlugins` and an instance administrator, `/settings/migration`
  and `/settings/migration/<step>` resolve to `settings-migration` ahead of `settings` and the entry is listed under System; an
  unknown step redirects to the first open step; without the plugin in `enabledPlugins` the path shows the not-found page and
  no entry is listed; without the permission no entry is listed and no API call is made; `INGOT_LOCAL_PLUGINUI=false` (a manifest without
  pages) still lists the entry; no bundled settings domain is called `migration`; existing `/settings/<domain>` routes are
  unchanged. Verify: they fail before 4.2.
- [ ] 4.2 `feat(ui)`: add the static registry entry with its `requires` predicate, the manifest read and the `settings-migration`
  route, in the group and with the route style of `add-repositories-admin-page` if it is on main, and in the Instance group of Settings
  otherwise. Verify: the tests of 4.1 pass and the existing router and registry tests pass unedited.

## 5. Steps

- [ ] 5.1 `test(ui)`: write failing component tests for Connect and Discover: the password sent only in request bodies, a reference accepted
  as text, nothing in storage or the address, no TLS control and `INGOT_EXTRA_CA_CERTS` named on a trust failure, the discovery table
  and histogram, unsupported formats excluded.
  Verify: they fail before 5.2.
- [ ] 5.2 `feat(ui)`: add the Connect and Discover steps. Verify: the tests of 5.1 pass.
- [ ] 5.3 `test(ui)`: write failing tests for Mapping and Dry run: warnings for empty and duplicate names, the conflict policy
  control with `refuse` preselected and the `overwrite` confirmation, a taken name from the report with the `<name>-nexus` proposal
  and the stale acceptance after taking it, the `400` problem body for a reserved name, the loss report table with filters and
  downloads, blockers link to rows, the accept control. Verify: they fail before 5.4.
- [ ] 5.4 `feat(ui)`: add the Mapping and Dry run steps. Verify: the tests of 5.3 pass.
- [ ] 5.5 `test(ui)`: write failing tests for Sync, Verify and Cut-over: attach to the unfinished import job on load, per-repository rows,
  buttons follow the job state, `READ_ONLY` and `CREDENTIALS_NEEDED` shown in words with the `PUT` credentials form, conflicts
  listed, cancel confirmation, `MISMATCH` and `CONFLICTED` block the verify item, the verify item derived after a reload, the
  manual ticks not stored, no delta or alias control, snippets carry no secret, the freeze item calls nothing. Verify: they fail before 5.6.
- [ ] 5.6 `feat(ui)`: add the Sync, Verify and Cut-over steps. Verify: the tests of 5.5 pass.

## 6. Accessibility

- [ ] 6.1 `test(ui)`: write failing tests: one `h2` per step and `aria-current="step"`, focus on the heading after a step change,
  error summary focus and `aria-describedby`, table captions and `aria-sort`, status text beside every icon, the progress element
  values, the live region throttle with a fake clock (one update per ten seconds, ten point threshold), indeterminate state,
  dialog focus trap and return. Verify: they fail before 6.2.
- [ ] 6.2 `feat(ui)`: implement what 6.1 requires. Verify: the tests of 6.1 pass.

## 7. Documentation and manual check

- [ ] 7.1 `docs(ui)`: add a how-to page for the wizard steps, link it from
  `docs/gitbook/how-to/import-from-other-repository-managers.md`, and list it in `docs/gitbook/SUMMARY.md`. The page states
  that a literal password lives in browser memory only while the wizard is open (and that a secret reference avoids it), that Nexus is never written to, what a reload does, and how to provide a private CA through `INGOT_EXTRA_CA_CERTS`. No configuration key is
  added by this change, so no reference page changes.
- [ ] 7.2 Manual pass, recorded in the pull request: keyboard only through all seven steps, one screen reader run
  (Orca or NVDA) on the Sync step, 320 px width, reduced motion on, high contrast.

## 8. Pull request

- [ ] 8.1 Open the pull request with the title `feat(ui): add the nexus import wizard`. The description follows
  `.github/PULL_REQUEST_TEMPLATE.md`, carries no session link, and states that the page appears only with the plugin enabled and
  that there is no breaking change.
