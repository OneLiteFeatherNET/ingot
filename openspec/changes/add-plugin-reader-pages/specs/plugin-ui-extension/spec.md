# Spec Delta

## MODIFIED Requirements

### Requirement: Data routes require the page area

For a plugin that registered an admin page, the server SHALL require one of the areas named by its admin pages for every request below
`/api/plugins/<plugin>/`, after authentication, answering `401` or `403` with the usual problem body. The only exception is a `GET` or
`HEAD` request to a source that a reader page of the same plugin declares (`plugin-reader-pages`), which SHALL be handled without the
area check. A plugin that registered no admin page SHALL see no change.

#### Scenario: A route the plugin forgot to guard is guarded

- **WHEN** a caller without the page's area calls `GET /api/plugins/<plugin>/anything`, and `anything` is not a reader source
- **THEN** the answer is `403` and the plugin's handler does not run

#### Scenario: Another plugin's routes are untouched

- **WHEN** a plugin without an admin page serves `/api/plugins/<other>/status` as before
- **THEN** the request is handled exactly as it was before this change

#### Scenario: A declared reader source is not guarded by the admin area

- **WHEN** an anonymous caller calls `GET /api/plugins/<plugin>/releases`, and `releases` is a reader source of that plugin
- **THEN** the admin area check does not run, and the plugin handler answers
