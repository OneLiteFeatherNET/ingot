# Proposal

## Why

Ingot writes its log in one shape only: text lines to the console, and a rolling debug file under `/var/log/reposilite`
(`tinylog.properties:24-31`, always on). An operator running Ingot in a cluster meets four limits.

The file is written whether or not anyone reads it. Under a read-only root filesystem it needs an `emptyDir`, and the
staging values carry one only so that the server starts.

The console line has no trace id. Even with the OpenTelemetry agent attached, a slow request cannot be joined to its trace
in Loki or Elasticsearch without regex parsing. The code has no MDC or ThreadContext, and the build has no OpenTelemetry
dependency.

The levels are fixed. The console threshold can only be set with `--level`, and the per-package levels are written into the
properties file.

The one access record is a debug line. It is only registered when the external cache bypass is on
(`JavalinConfiguration.kt:68-71`), so a default install records no per-request line at all.

## What Changes

Every new setting is opt-in. Each one reads the `INGOT_` name first and falls back to the `REPOSILITE_` spelling. With nothing
set, the output is the same as today.

- **File writer opt-out.** `INGOT_LOG_FILE=false` stops the rolling file writer. The file is still written by default. How
  the writer is removed before the first logger is used is an open verification (task 2.1). If tinylog cannot drop it at
  runtime, the change is revised before the code pull request opens.
- **JSON console output.** `INGOT_LOG_FORMAT=json` writes one JSON object per line to stdout, with `time`, `level` and
  `message`. `text`, the default, keeps today's output. The rolling file stays plain text in both modes.
- **Trace identifiers.** When an OpenTelemetry span is active on the thread, JSON lines carry `trace_id` and `span_id`. Without
  an agent the two fields are absent.
- **Levels from the environment.** `INGOT_LOG_LEVEL` sets the console threshold, with the same values as `--level`.
  `INGOT_LOG_LEVELS=pkg=level,...` sets per-package levels when the server starts. The command-line flag wins over the
  variable. An unknown value stops the start and the message names the variable.
- **Access log.** `INGOT_ACCESS_LOG=true` writes one INFO record per request: method, path without the query string,
  status, duration, response bytes, client address (resolved through the trusted proxy list of `INGOT_TRUSTED_PROXIES`)
  and user. The record is registered whether or not the cache bypass is on, and never contains credentials or cookies.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for every new variable and its fallback, and
  `docs/gitbook/how-to/monitor-status-and-failures.md` for the JSON lines, trace fields and the access log.

### Out of scope

- Chart values such as `logLevel` or `logFormat`. They belong to the chart changes (`add-chart-observability`).
- A runtime endpoint or console command that changes levels. Levels change only at start.
- Creating spans, wiring the OpenTelemetry agent into the chart, and span attributes (N10).
- Changes to the text format, the file name, the file rotation policy, or the entrypoint's `-Dtinylog.writerFile.*` flags.
- Changes to the existing debug request line in `EndpointAccessLoggingHandler`.
- Log shipping, sampling, and metrics. Request metrics belong to `add-cluster-metrics`.

## Capabilities

### New Capabilities

- `log-output`: the rolling file opt-out, the JSON and text console formats and the trace fields.
- `log-levels`: the console threshold and the per-package levels from the environment.
- `access-log`: the opt-in per-request record and what it may contain.

## Impact

- **Type and scope.** The change ships under `feat(logging)`. The pull request title, squash-merged onto main, is:

  `feat(logging): add json logs, env log levels and an access log`

- **Breaking marker.** No breaking change. No operator has to change a configuration key, variable, volume, manifest or
  plugin. An installation pointed at Ingot logs exactly as before. The title has no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Defaults.** Unchanged. The file is written, the console is text, the levels are as today, and no access record is written.
- **Dependencies.** None. The change touches `JavalinConfiguration.kt`, which `add-overload-protection` and
  `add-cluster-metrics` also touch. No order is required, but their pull requests may need a rebase.
- **Code.** `reposilite-backend` (logging, `web/application`, `web/infrastructure`) and `docs/gitbook/`. The OpenTelemetry API
  is not added as a compile dependency (see `design.md`).

## Verification

- Red tests first, one behaviour each, run through `heavy` with `--max-workers=2`.
- `openspec validate add-structured-logging --strict` passes.
- The Container images job stays green with no `INGOT_LOG_*` variable set, which covers the upstream compose file and the
  upgrade rehearsal.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-structured-logging` prints nothing.
