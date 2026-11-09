# Design

## Context

The console is written by the root journalist that `ReposiliteFactory.createReposilite(parameters, rootJournalist)` receives.
The default is a `PrintStreamLogger` from the `com.reposilite.journalist` library, which is a third-party library and stays
unchanged. The rolling file is written by tinylog, configured by `tinylog.properties` inside the jar. The `writerJournalist`
format in that file does not decide the console line. The factory overload is already the seam used by the integration tests.

## Decision 1: JSON console output is an Ingot-owned journalist

The JSON formatter is a `Journalist` implemented in `net.onelitefeather.ingot.logging`. It writes to `System.out` and
`System.err`. The single-argument `createReposilite(parameters)` overload passes it to the existing overload only when
`INGOT_LOG_FORMAT=json`. The default path keeps the current `PrintStreamLogger`, so the text output stays byte-identical.

Rejected: changing the journalist library, or formatting JSON through tinylog. Tinylog's file writer is not on the console path,
and changing the library moves code that must not move.

## Decision 2: Disabling the file writer is decided by a spike

The research note proposed `-Dtinylog.writerFile=` as an empty value. That is not verified, and the Gradle caches on the
build host have no tinylog jar to read. Task 2.1 writes a failing test that starts with `INGOT_LOG_FILE=false` in a fresh
JVM and asserts that no file appears in a temporary directory. The spike then tries, in order:

1. a `-Dtinylog.*` system property that removes the writer;
2. programmatic configuration set before the first logger is used, which the journalist start-up must allow;
3. an alternate configuration resource, selected by a system property, if tinylog supports one.

The first option that passes is recorded here before task 3.2. If none passes, this change stops and the proposal is
revised. The default path does not depend on the outcome.

## Decision 3: No compile-time OpenTelemetry dependency

The research note suggested adding `opentelemetry-api` as a runtime dependency. This design does not add it now. The API
would add a jar to the server bundle for a field that is filled only when an agent is attached. Instead, a `TraceContext`
seam returns the active `trace_id` and `span_id`. Its default implementation probes the API by reflection once at start.
If the probe finds no API, both fields are absent. Task 4.2 checks whether the agent makes its API visible to the
application class loader. If it does not, the reflection probe stays, the fields stay absent under the agent, and the
docs say so. If the spike shows that a compile dependency is required, that choice is recorded here before the code pull
request.

## Decision 4: The access record does not depend on the cache bypass

The existing debug line is registered only with `bypassExternalCache` (`JavalinConfiguration.kt:68-71`). The access record
is a separate `BEFORE`/`AFTER` handler pair, registered in the same place as the other opt-in handlers. It is registered
only when `INGOT_ACCESS_LOG=true`, so the default route table does not change. It reads the status and byte count in the
after step and the start time from an injected monotonic source, so tests do not read the system clock.

## Decision 5: Command-line flag beats the variable

`--level` has a default value, so the server cannot tell an unset flag from the default. The level resolver therefore
reads whether the option was given on the command line, and applies `INGOT_LOG_LEVEL` only when it was not. Tests cover
the flag, the variable and the default, and each combination.

## Risks

- A removed file writer that drops messages from other writers. The spike test asserts that console output still appears.
- JSON escaping of messages that contain control characters. The formatter escapes them, and a test covers it.
- The access record adds one line per request at `INGOT_ACCESS_LOG=true`. Operators choose it knowingly; the default writes nothing.
