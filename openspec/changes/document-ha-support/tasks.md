# Tasks

Every commit of this change has the type `docs` and the scope `gitbook`. There is no production code, so there is no unit test
to write first. The checks below are written down before the page and run after it. Every claim about code is checked against
the cited path before it is written.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose document-ha-support`: this change only, branched from the remote main branch. Verify:
  `openspec validate document-ha-support --strict` passes and the dash grep prints nothing.

## 2. The statement page

- [ ] 2.1 `docs(gitbook)`: write `docs/gitbook/explanation/high-availability-support.md` with the sections supported topology, not
  supported, rolling upgrade rule, failure behaviour with the lease section, scale and sizing, and evidence. Take the rule from
  `test-mixed-version-cluster` once it has merged. Verify: every row has a test or "not tested", no figure lacks its conditions,
  and the page has no dash of the two kinds.
- [ ] 2.2 `docs(gitbook)`: add the page to `docs/gitbook/SUMMARY.md` under "Architecture and compatibility". Verify: the summary
  lists it once.

## 3. Stale sentences

- [ ] 3.1 `docs(gitbook)`: replace the "not fully supported yet" sentence and the matching hint in
  `docs/gitbook/how-to/run-on-kubernetes.md` with a link to the statement, keeping the limits that are still true. Verify: the
  remaining limits match the code.
- [ ] 3.2 `docs(gitbook)`: in `docs/gitbook/explanation/multi-replica-operation.md`, replace "does not describe a finished Kubernetes
  setup" and update "What is not provided yet", and link to the statement. Verify: no statement in the page contradicts the new page.
- [ ] 3.3 `docs(gitbook)`: in `docs/gitbook/how-to/upgrade-ingot.md` replace the "not tested" hint with the order of a rolling
  upgrade and a link, and in `docs/gitbook/reference/upgrade-changes.md` link the mixed versions note to the rule. Verify: both
  pages link to the anchor that exists.

## 4. Checks

- [ ] 4.1 Run the documentation link check, or when none exists check by hand that every relative link and anchor of the five edited
  pages and the summary resolves. Verify: no broken link.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `docs(gitbook): add a high availability support statement`, after
  `test-mixed-version-cluster` has merged. Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the
  story of an operator who could not tell whether Ingot supports several replicas. It carries no session link and no attribution
  line. Verify: `gh pr view --json title` shows the exact title and the body has no em or en dash.
