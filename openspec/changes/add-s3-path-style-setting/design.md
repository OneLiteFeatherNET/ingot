# Design

## Context

`S3StorageProviderFactory.create` reads `System.getProperty("reposilite.s3.pathStyleAccessEnabled") == "true"` and, when true, passes an
`S3Configuration` with path style to the client builder. `S3StorageProviderSettings` has no field for it. The reference pages
`references/repositories/storage-providers.md` and `repository-settings.md` document the property.

## Decisions

### D1. A nullable boolean

`pathStyleAccess: Boolean? = null`. Null means "ask the property". This keeps the three states a boolean cannot: forced on, forced off,
and unchanged. A plain `false` default would silently override the property for every existing repository, which breaks the
drop-in rule.

### D2. Resolution in one function

A function `resolvePathStyle(setting: Boolean?, property: String?): Boolean` returns `setting ?: (property == "true")`. The factory
passes the property value in, so the test injects it.

### D3. Same wire format

The field is a plain optional key in the shared configuration. No migration, no rename.

## Open questions

- **Q1.** Whether the form shows a three-state control or a checkbox with a "use server default" note. The proposal assumes a select
  with the values "Server default", "On" and "Off".
