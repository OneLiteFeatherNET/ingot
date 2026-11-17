# Design

## Context

The repository already has `.github/SECURITY.md`, `.github/CODE_OF_CONDUCT.md` and `.github/CONTRIBUTING.md`. GitHub shows the first two in its own security and conduct tabs from `.github/`, and `CONTRIBUTING.md` links both. The new files have no such constraint.

## Decision 1: where the new files live

The new files `MAINTAINERS.md`, `GOVERNANCE.md`, `ADOPTERS.md` and `SECURITY-INSIGHTS.yml` go to the repository root. Trust and governance files are commonly read there, and the root is where a reviewer looks first. The existing `.github/` files stay where they are, so no link that works today breaks.

Alternative considered: all trust files under `.github/`. Rejected, because it would split the governance set across two directories without a gain for GitHub's own surfaces.

## Decision 2: which reporting channel is primary

The GitHub private advisory form stays the first channel in `SECURITY.md`. A single private mailbox is the second channel, for reporters without a GitHub account and for the case where the form is unavailable. No public chat or issue is named for security reports.

Alternative considered: the advisory form alone. Rejected, because the policy would then have no route for a reporter who cannot use GitHub, and the open marker would remain. The mailbox address itself is the maintainer's decision (task 2.1) and is not chosen here.
