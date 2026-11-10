# Design

## Context

Verified on this worktree:

- `docs/gitbook/how-to/run-on-kubernetes.md:10` says "Several replicas are not fully supported yet", and the warning hint in
  its section "Running more than one replica" lists limits that are partly resolved by changes that are merged since
  (readiness path, shutdown budget, #113).
- `docs/gitbook/explanation/multi-replica-operation.md` opens with "It does not describe a finished Kubernetes setup" and ends
  with a section "What is not provided yet" (readiness is per replica, no leader election, no shared console, cache or
  counters).
- `docs/gitbook/how-to/upgrade-ingot.md:25` holds the hint "A rolling upgrade ... is not tested".
- `docs/gitbook/reference/upgrade-changes.md` ends the lease section with a "Mixed versions" note: a replica of the previous
  release ignores the leases, so no scheduled cleanup should be enabled until every replica runs the new release.
- `docs/gitbook/how-to/size-the-deployment.md` gives the chart defaults and a method, and states that no measurement of Ingot
  under a recorded workload exists.
- `docs/gitbook/SUMMARY.md` lists the explanation pages under "Architecture and compatibility".
- Supported databases per `openspec/config.yaml`: SQLite default, MariaDB, MySQL, PostgreSQL, H2. The multi-replica page calls
  MariaDB and PostgreSQL the supported choices and MySQL and H2 experimental.
- Tests that back claims: `charts/ingot/tests/ha_smoke.py` (rolling restart, lease takeover, concurrent deploys, sessions,
  cluster view, #124), the container suite (`ClusterBusImageTest`, upgrade rehearsals), `test-server-database-matrix`
  (integration tests on PostgreSQL and MariaDB, #121), and the mixed-version test of the change this one depends on.
- The report of the research and the review set the decision D12: publish one supported topology (PostgreSQL or MariaDB plus S3,
  N replicas, one region) after the smoke test. The review adds scale targets and the lease behaviour with several replicas.

## Decisions

### 1. One explanation page, linked from four places

The statement is an explanation in the Diataxis sense: it says what is supported and why, with reference tables inside. It lives
at `docs/gitbook/explanation/high-availability-support.md`, in the group "Architecture and compatibility", next to the
multi-replica page. The other pages keep their own topic and link to it, so a reader never meets a second copy of the rule.

### 2. Every claim names its test

A table row has three cells: the statement, the evidence (a test or a CI job by name), and the state ("tested on every pull
request", "tested at release", "not tested"). A statement without evidence is "not tested". This keeps the page from outliving
its tests: a reviewer who removes a test sees the row it backed.

### 3. Supported means a promise about behaviour, not about a service level

The page defines "supported" as: the topology is exercised by the project's CI, a failure in it is treated as a bug, and the
default configuration does not need a workaround. It names no response time, no support channel and no commercial offer, which
are outside this change.

### 4. The scale table has rows and no invented numbers

Rows: replicas, repositories, artifacts per repository, total artifacts, search index size, concurrent clients, largest upload,
database size, lease and poll times. Each row has "measured" (a number with the date, the version, the hardware and the
workload), or "not measured". Known constants from the code are listed separately as limits, not as capacity: the 8 MiB
multipart part (`docs/gitbook/how-to/use-s3-storage.md`), the 60 second lease, the 10 second settings poll, the 7 day claim
retention. The page says how a figure gets into the table: a recorded run with the query used, as the sizing page describes.

### 5. The N/N+1 rule is copied from the test change and carries its limits

The rule text is exactly the table of `test-mixed-version-cluster` (design, decision 1), with the limits that change's run found.
This page is the only place the rule is written for operators. If the test change finds that the rule has to be narrower, this
page follows it, and the page is not merged before that is known.

### 6. The multi-replica lease behaviour gets its own section

The review flagged lease issues with several replicas. The page states what the code does: job claims are an insert with a unique
key and a 7 day retention, a cleanup run holds a 60 second lease for its whole run, a lost holder is taken over by the next
check on the same UTC day, a run that was not started at its minute is not caught up later, the search rebuild is one run per
instance with a lease and a saved position, and a previous-release replica ignores the leases. It says what is not provided: no
leader election, and no catch-up of a missed schedule.

## Risks

- **A statement that is ahead of its test.** The pull request is not merged before `test-mixed-version-cluster`, and the rows
  that depend on it say "not tested" if that change is delayed.
- **Numbers that are mistaken for a promise.** Only measured rows carry figures, each with its conditions, and the page says a
  figure is a measurement of one workload.
- **Drift between pages.** The four edited pages keep one sentence and a link, and a link check (the documentation build)
  covers the anchors.

## Test plan

Documentation has no unit test. The checks are: `openspec validate`, the documentation build or link check that the repository
already runs in CI on `docs/gitbook/` (if none exists, a local check that every relative link and anchor in the five edited
pages resolves), the dash grep, and a read through for the words of `CLAUDE.md` (no claim that the contract is broken). A claim
about code is checked against the cited path before it is written.
