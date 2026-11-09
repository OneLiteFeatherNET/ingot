# Design

## Context

Verified against origin/main:

- The default list is `MirroredRepositorySettings.allowedExtensions` (`reposilite-backend/.../maven/application/MavenSettings.kt:277`).
  Only `MirrorService` reads it, and only for proxied entries. Hosted repositories never read it.
- A settings domain is persisted by `SharedConfigurationFacade.updateSharedSettings`. It calls `storeRendered`, which renders
  every domain with its current value (`renderConfiguration`). Nothing writes the defaults at start-up.
- Before the first save, the stored document is empty and the defaults stay in memory. After a save, the stored document
  holds every domain, the `maven` domain included.

## Decision

### D1: a new default does not rewrite stored lists

The new default applies to an installation that has no stored `maven` domain. An installation that already stored one keeps
its list. Ingot adds no start-up migration that edits stored lists.

Why: a stored list is an operator's explicit choice. Rewriting it on start would undo deliberate filtering, and a migration
would be a second moving part for one list entry.

Trade-off: an operator who wants `.sigstore.json` on an installation with a stored list adds it in the settings. The
upgrade notes say so.

Alternative rejected: a migration that appends `.sigstore.json` to every stored list that holds `.asc`. It would change
configurations that operators reviewed, and it would need its own tests for every shape of stored document.

### D2: the default is one list entry, not a new mode

The change adds one value to the existing list. It does not add a switch, a second default list or a wildcard for
signature files, so a reviewer can read the whole change in the diff.
