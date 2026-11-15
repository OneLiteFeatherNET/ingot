# Design

## Support file

`SUPPORT.md` is plain Markdown at the repository root. It has four parts: where to ask a question (GitHub Discussions or issues,
as `.github/ISSUE_TEMPLATE/config.yml` already routes), where to report a vulnerability (a link to `.github/SECURITY.md`, never
a copy of its text), which release lines receive fixes, and the statement that support never gates a feature.

## Support window

The window itself is a maintainer decision and this change does not invent one. A draft that is consistent with how releases
are made today (Release Please from main, `VERSIONING.md`) is: the latest minor line receives every fix; the previous minor line
receives security fixes only, for 90 days after the next minor line ships; older lines receive nothing. The first task asks the
maintainer to confirm or replace it, and the values that land in `SUPPORT.md` and in the `Supported Versions` table of
`.github/SECURITY.md` are the same ones. If the maintainer decides that only the latest line is supported, the table has one
row. Backporting needs a release branch, which the repository does not have yet, so a "previous line" row is only honest if
the maintainer accepts creating one.

## Security policy table

`.github/SECURITY.md` today says no release was published and lists only `main`. Releases `v1.0.0`, `v1.1.0` and `v1.1.1`
exist, so the table is stale. Only its `Supported Versions` section changes here, which keeps the hunk away from the contact
section that `add-repository-trust-files` fills. That is the only overlap between the two changes.

## Consequences

- A reader finds the window in one place, and the two files cannot drift because one task writes both.
- The window becomes a promise. Changing it later is a documentation change that touches both files.
