# Tasks

Each group is one or more commits. Every commit has one Conventional Commits type and scope. The code pull request is squash-merged under
the title in task 7.1, so its commit on `main` is `build(ui)`; the groups below name the type of each commit they produce.

Every test follows F.I.R.S.T.: no sleeps or waits by time, no reads of the system clock, no network, no shared mutable state, and no write
to disk. Scanner fixtures are strings built inside each test. Repository tests only read `reposilite-frontend/src/`. Each test names one
behaviour and asserts with a message that names the file and the token. Every test is seen to fail before the code it covers exists.
Test runs go through `heavy`. The gate's tests run with `npx vitest run src/style-gate` inside `reposilite-frontend`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-ui-style-gate`: this change and nothing else, with the tasks unchecked and branched from the
  remote `main`. Verify: `openspec validate add-ui-style-gate --strict` passes and the dash grep prints nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: in `src/style-gate/scan.test.js`, one fixture per matcher of rules 1 to 8, each with a positive case (a
  violating string that must be reported with the right file and match) and a negative case (a compliant string that must not be
  reported). The negatives include `rounded-corner-large`, `shadow-elevation-2`, `bg-(--md-sys-color-surface)`, `data-ingot-component`,
  `text-on-surface`, `md-body-small`, `querySelector` with a data-test selector, and prose such as "Use a colour in the form #RRGGBB".
  Verify: it fails, because `scan.js` does not exist.
- [ ] 2.2 `test(ui)`: in `src/style-gate/ratchet.test.js`, pure cases for the three ratchet outcomes: a new key is unexpected, a key whose
  count fell is stale, an allowlist entry that matches nothing is dead, and an exact match yields no problem. Include the case where an
  allowlisted occurrence is subtracted before the comparison. Verify: it fails, because `ratchet.js` does not exist.
- [ ] 2.3 `test(ui)`: in `src/style-gate/tailwind.test.js`, a token that Tailwind builds (`bg-surface-container`), a project `@utility`
  (`md-body-small`), a `@theme` name (`rounded-corner-large`) and the `@utility container` all resolve, and an unknown name
  (`card-editor`) does not. Verify: it fails, because `tailwind.js` does not exist.
- [ ] 2.4 `test(ui)`: in `src/style-gate/rules.test.js`, the rule table lists eight rules with the ids of the design, each with a scope and
  an allowlist file name. Verify: it fails.
- [ ] 2.5 `test(ui)`: in `src/style-gate/gate.test.js`, one describe block per rule (`style-block`, `arbitrary-value`, `raw-colour`,
  `non-md3-default`, `inline-style`, `class-token`, `class-selector`, `inline-svg`). Each test reads `src/`, applies the rule with its
  baseline and allowlist, and asserts that there is no unexpected violation and no stale entry, with a message that names the file and the
  token. Also a test that every allowlist entry has a non-empty reason and that every baseline file parses. Verify: it fails, because the
  baseline and allowlist files do not exist yet.

## 3. Scanner and baselines

- [ ] 3.1 `build(ui)`: add `@tailwindcss/node` 4.3.3 as a devDependency of `reposilite-frontend`, pinned to the same version as
  `tailwindcss`, and update `package-lock.json` with `npm install --save-dev --save-exact @tailwindcss/node@4.3.3` through `heavy`. Verify:
  `npm ls @tailwindcss/node tailwindcss` shows one version each, and `npm run license-check` still passes.
- [ ] 3.2 `build(ui)`: implement `src/style-gate/scan.js`, `rules.js`, `sources.js`, `tailwind.js` and `ratchet.js` until the tests of group 2
  pass. `sources.js` excludes `*.test.js` and `src/style-gate/`. Verify: `npx vitest run src/style-gate/scan.test.js
  src/style-gate/ratchet.test.js src/style-gate/tailwind.test.js src/style-gate/rules.test.js` passes.
- [ ] 3.3 `build(ui)`: generate the eight baseline files and the allowlist files from a throwaway script in the scratchpad (not committed),
  then review them by hand. The review checks the seven `<style>` files, the seven `color-mix()` files, the six dangling hooks (`card-editor`,
  `snippet-url`, `settings-view`, `settings-save-bar`, `one-of-container`, `file-uploads`; `pane-sheet` is already gone through
  `refactor-md3-token-utilities`), the three hex literals, and the
  allowlist entries for data-driven bindings and the safe-area `calc`. Record the exact counts per rule in the pull request description.
  Verify: `npx vitest run src/style-gate` passes and the reviewed files are committed under `src/style-gate/baseline/` and
  `src/style-gate/allowlist/`.
- [ ] 3.4 `build(ui)`: make the repository test of group 2.5 pass for every rule, and confirm each rule's test fails when a scratch fixture
  adds one violation, run in a temporary copy of `src/` through `heavy`, never in the working tree. Verify: the full gate passes on the
  branch and the scratch run reports exactly the added violation.

## 4. Lint phase

- [ ] 4.1 `build(ui)`: in `reposilite-frontend/eslint.config.js`, remove the `warnPhase` helper, its `toWarn` and `isOff` functions and the
  `.map(warnPhase)` call, so the severities the file configures apply again. Verify: `npm run eslint` fails on the current tree, which is
  the expected red state before 4.2.
- [ ] 4.2 `build(ui)`: run `npx eslint --suppress-all` and commit `reposilite-frontend/eslint-suppressions.json`. Before running it, check
  the flag names against the pinned ESLint 10.4.1 with `npx eslint --help`, and record the actual flags in the pull request if they differ.
  Verify: `npm run eslint` exits 0 with no output apart from the suppressed count.
- [ ] 4.3 `build(ui)`: run `npx eslint --prune-suppressions` and commit the result if it changes anything. Verify: a second run leaves the
  file byte-identical, and a scratch file with a semicolon and an unused variable, checked and then deleted, makes `npm run eslint` fail.

## 5. Documentation

- [ ] 5.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, section "Rules for contributors", add one bullet that names the gate
  (`src/style-gate/`), says what it rejects, and says that the baseline may only shrink. Do not change any other rule. Verify: the dash grep
  prints nothing, and the bullet names only paths that exist (`src/style-gate/`, `src/styles/md3/`).

## 6. Check

- [ ] 6.1 No commit. Run `npm test` in `reposilite-frontend` through `heavy` on the final branch. Verify: the whole unit suite passes,
  which includes the existing `src/style.test.js`, `src/styles/md3/*.test.js` and `src/anchors/anchors.test.js`.
- [ ] 6.2 No commit. Run `npm run eslint` and `npx eslint --prune-suppressions --dry-run` if the installed ESLint supports it, otherwise
  prune and check `git diff --exit-code` afterwards. Verify: both pass.
- [ ] 6.3 No commit. Verify: `git diff --name-only <base>...HEAD` lists only the files named in the proposal's Impact section, with no file
  under `src/` outside `src/style-gate/`, no workflow file, and no file under `openspec/` (the code pull request carries none).
- [ ] 6.4 No commit. Verify: `openspec validate add-ui-style-gate --strict` passes and
  `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-ui-style-gate` prints nothing.
- [ ] 6.5 No commit. The change has no visible effect, so no screenshots at 320, 600 or 1440 pixels are taken. Verify: the proposal's
  Impact section says so, and no file under `src/components/`, `src/styles/` or `src/style.css` differs from the base.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `build(ui): fail frontend checks on custom css and token bypasses`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description follows the rules of the repository: it tells the story of a
  contributor whose new palette class passed review and reached the dashboard, and of the baseline that lets the gate fail on new
  bypasses without blocking the existing ones. It carries the per-rule counts from 3.3, the result of 6.1 and 6.2, and the note that the
  change is not BREAKING and has no visible effect. It carries no session link and no attribution line, and no em or en dash. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body has no dash
  characters of the two forbidden kinds.
