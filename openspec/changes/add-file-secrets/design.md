# Design

## Context

`LocalConfigurationFactory.getEnvironmentVariables` strips `INGOT_LOCAL_` and `REPOSILITE_LOCAL_` and merges the two
spellings, the `INGOT_` one winning per key. `getProperties` does the same for `ingot.local.` and `reposilite.local.`, and
`loadCustomPropertiesViaReflections` applies the environment layer first and the property layer over it. The documented
order is: default, file, environment (`REPOSILITE_` then `INGOT_`), system property (`reposilite.` then `ingot.`), command line.

Property keys are lower-cased and carry no separators, so `_FILE` cannot be carried over literally to the property form.

## Decisions

### D1. A `_FILE` value takes the place of the plain value of its own source

The file is an alternative spelling of the value at the same step of the precedence order. The environment layer is built
first, with the file value standing in for the plain value of that layer. The property layer is built the same way. The two
layers are then merged exactly as today, so a system property still beats an environment variable, whichever spelling each
one used. This keeps the documented order unchanged and needs no new step in it.

### D2. The resolver works on the raw variables

The stripping is done on keys, which loses the name the operator typed. A failure has to name `INGOT_LOCAL_KEYPASSWORD_FILE`
or `REPOSILITE_LOCAL_KEYPASSWORD_FILE`, so the resolver reads the raw entries of each source, keeps the original name next to
each value, and reads the file only when it is needed (no plain value in the same source, and a known key).

The resolver is a pure function of the source maps and a file reader, with the reader passed in. The unit tests pass real files
in a `@TempDir` and a fake logger; no test reads the real environment.

### D3. Reading rules

- The content is decoded as UTF-8. Content that is not valid UTF-8 is an unreadable file.
- Trailing `\n` and `\r` characters are removed. Nothing else is trimmed, so a leading space stays.
- An empty file gives the empty string, as an empty plain variable does. `keyPassword` defaults to the empty string, so an
  empty secret file is a legitimate value, and refusing it would be a second rule to document.
- A relative path is resolved against the working directory parameter, as `configuration.cdn` paths are. An absolute path is
  used as it is.

### D4. Warnings reach the log through the root journalist

`ReposiliteFactory` calls `LocalConfigurationFactory.createLocalConfiguration(null, parameters)`, so every warning the factory
writes today is dropped on the server start path. The warning for "plain and `_FILE` both set" must reach the log, so the call
passes the root journalist that `createReposilite` already receives. Q6 is expected to make the same change. If Q6 has
already done it, this change only uses it. The consequence is that the existing info lines of the factory (the file load and
the external overrides) become visible at start. That is the documented behaviour, and it is accepted.

### D5. The start failure mirrors the deploy target refusal

A new `LocalConfigurationFileException` (an `IllegalArgumentException`, like `UnknownDeployTargetException`) carries the
message. `main` in `ReposiliteLauncher` catches it, prints one line to standard error and exits with status 1, with no stack
trace. The factory runs before any port is bound, so nothing is listening when it fails.

### D6. The property form takes a `.file` suffix

The property form is `ingot.local.<key>.file` (for example `-Dingot.local.keyPassword.file=/run/secrets/key`), and the
`reposilite.local.` spelling works the same way. The suffix keeps the dotted style of the property names. The environment form
keeps the `_FILE` suffix of the Docker convention.

### D7. Unknown `_FILE` keys are reported and not read

A `_FILE` variable whose key is not a local setting is logged as unknown, as an unknown plain variable is today, and its file
is not read. Reading a file that no setting uses would only add a failure mode.

## Risks

- A secret written to the log. The warning names variables only. The existing info line keeps its first and last character
  rule and never logs the content of a file.
- Q6 changes the same function. If Q6 changes how an unknown key is reported, D7 follows that change and the test for D7 is
  adjusted in the same commit.
