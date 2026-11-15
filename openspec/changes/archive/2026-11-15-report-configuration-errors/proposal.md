# Proposal

## Why

Two readers of configuration fail quietly. An operator who makes a typo sees Ingot start, with defaults in effect and no reason
given.

The first is the shared settings file. When `configuration.shared.json` is not valid JSON, `SharedConfigurationFacade`
(`loadSharedSettingsFromString`, lines 126-128) takes the parse error and continues with an empty map. No domain is loaded,
nothing is logged at error level, and the start goes on.

The second is the local configuration read from environment variables and system properties
(`LocalConfigurationFactory.applyCustomProperties`). A boolean is read with `toBoolean()` (line 62), so `yes` becomes `false`
without a word. A number that does not parse throws a bare `NumberFormatException` (lines 63-64). The exception carries the
raw input but not the name of the setting, so the operator cannot tell which of the many variables is wrong.

The values in effect stay as they are today. This change only makes the failures visible.

## What Changes

- **Shared settings file.** A document that cannot be parsed logs one ERROR. It names the file (`configuration.shared.json`,
  through the provider name), the line and column that the parser reports, and that the domains fall back to defaults. The
  parser message is left out of the log, because it can quote part of the file, and the file holds credentials. An empty or
  missing file logs nothing.
- **Number and long settings.** A value that does not convert logs one ERROR that names the setting and the expected type
  (`int` or `long`). The existing exception is still thrown with the same type and message, so startup fails as it does today.
- **Boolean settings.** A value other than `true` or `false`, ignoring case, logs one WARN that names the setting and says that
  `true` or `false` is expected. The value itself is not written to the log. It still becomes `false`, as it does today.
- **Provider name.** The name of the local shared-settings provider includes the file path, so the existing "loaded from" and
  "changed in" lines name the file too.

### Out of scope

- Failing startup on an unreadable shared file, and any strict mode or `--check-configuration` command. Those belong to N14.
- Changing the exception type or message of a conversion error. The log line carries the name; the exception stays as it is.
- Type errors inside a valid shared file, and unknown domain names, which are ignored today. Both are separate changes.
- File-based secrets (`*_FILE`), which is `add-file-secrets` (Q8). It touches the same factory and follows this change.
- Documentation. No configuration key, environment variable or manifest changes, so no page under `docs/gitbook/` changes.

## Capabilities

### New Capabilities

- `configuration-errors`: which configuration problems are logged, at which level, and what the log line names.

## Impact

- **Type and scope.** The change ships under `fix(configuration)`. The pull request title, squash-merged onto main, is:

  `fix(configuration): log unreadable shared and local settings`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change, and every setting keeps the value it has today. Two log texts change: the provider name in the "loaded from" lines
  gains the file path, and new ERROR and WARN lines appear for the cases above. Nothing in the plugin API changes.
- **Code.** `reposilite-backend` (`configuration/shared`, `configuration/local`) and their tests.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`, for example
  `:reposilite-backend:test --tests 'com.reposilite.configuration.*'`.
- `openspec validate report-configuration-errors --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/report-configuration-errors` prints nothing.
