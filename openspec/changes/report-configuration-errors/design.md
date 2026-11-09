# Design

## Context

Verified against origin/main:

- `SharedConfigurationFacade.loadSharedSettingsFromString` wraps `readTree` in `supplyThrowing` and falls back to
  `emptyMap()` (lines 126-128). The result is `ok()`, so the caller starts normally.
- `LocalConfigurationFactory.applyCustomProperties` converts with `toBoolean()` (line 62), `toInt()` (line 63) and `toLong()`
  (line 64). `String.toBoolean()` is true only for the word `true`, ignoring case.
- `ReposiliteFactory.createReposilite` calls `createLocalConfiguration(null, parameters)` (line 39) before any journalist
  exists. The conversion error is therefore thrown on that first call. `LocalConfigurationPlugin` runs the factory a second time
  with a journalist, but only when the first call did not throw.
- `LocalSharedConfigurationProvider.name()` returns the constant `"local file-system"`, so the facade cannot name the file
  today.

## Decisions

### D1. A conversion error is written to stderr when no journalist exists

The first call in `ReposiliteFactory` has no journalist, and it is the call that throws. A log line sent only through the
journalist would never be written for a bad number. When the journalist is null, the ERROR goes to `System.err` with the same
text. When a journalist exists, it is used. The thrown exception is unchanged, so the exit path is the same.

Alternative rejected: wrap the exception in one that names the key. That changes the exception type that callers see, which
the proposal keeps out of scope.

### D2. The parser message is not logged

Jackson messages can quote the token that failed, for example `Unrecognized token 'hunter2'`, and the shared file holds mirror
and LDAP credentials. The log names the exception class and the line and column from `JsonProcessingException.location`.
The existing debug output keeps using `SettingsSecrets.redactDocument`, and this change adds no new output of the document.

### D3. The provider name carries the path

`LocalSharedConfigurationProvider` already holds the resolved `configurationFile`. Putting it into `name()` lets the facade
keep one code path for every provider. The remote provider keeps its name, `remote database`, which has no file.

### D4. Case is ignored for booleans, as it is today

`true`, `TRUE` and `False` convert without a warning, because they already mean what the operator wrote. The warning covers
only values such as `yes`, `on`, `1` and `enabled`.

## Testing

Each behaviour is tested first, with a captured appender that writes a real logger to memory, following
`DeployTargetWarningTest`. No test reads the console or a file outside a `@TempDir`, and no test sleeps.
