# Spec Delta

## Purpose

The server writes its log to stdout in a form a log collector can parse, and may stop writing the rolling file, without
changing the default output.

## ADDED Requirements

### Requirement: Rolling file writer is on by default and can be turned off

The server SHALL keep writing the rolling file under `/var/log/reposilite` (or the configured directory) when no logging
variable is set. With `INGOT_LOG_FILE=false` the server SHALL NOT create or write the rolling file or its latest link. The
variable `REPOSILITE_LOG_FILE` SHALL be read when `INGOT_LOG_FILE` is unset. Console output SHALL be the same in both cases.

#### Scenario: Default start

- **WHEN** the server starts with no logging variable set
- **THEN** a log file is created in the log directory and receives the startup lines

#### Scenario: File writer turned off

- **WHEN** the server starts with `INGOT_LOG_FILE=false`
- **THEN** no file is created in the log directory and the startup lines still appear on stdout

#### Scenario: Legacy spelling

- **WHEN** the server starts with `REPOSILITE_LOG_FILE=false` and `INGOT_LOG_FILE` unset
- **THEN** no file is created in the log directory

### Requirement: Console format is text by default and JSON on request

The server SHALL write text lines to stdout when `INGOT_LOG_FORMAT` is unset or `text`, in the same form as before this
change. With `INGOT_LOG_FORMAT=json` the server SHALL write one JSON object per line to stdout with the fields `time`
(ISO 8601 in UTC), `level` and `message`. The `time` value SHALL come from an injected clock. The rolling file SHALL stay in
text form in both modes.

#### Scenario: JSON line is parseable

- **WHEN** the server logs a message containing quotes, a backslash and a newline with `INGOT_LOG_FORMAT=json`
- **THEN** the stdout line is one complete JSON object that parses and returns the original message

#### Scenario: No color codes in JSON

- **WHEN** the server logs at INFO with `INGOT_LOG_FORMAT=json`
- **THEN** the stdout line contains no ANSI escape sequence

#### Scenario: File stays text

- **WHEN** the server runs with `INGOT_LOG_FORMAT=json` and the file writer is on
- **THEN** the rolling file contains text lines and no JSON objects

#### Scenario: Unknown format refused

- **WHEN** the server starts with `INGOT_LOG_FORMAT=xml`
- **THEN** the start stops and the message names `INGOT_LOG_FORMAT` and the accepted values `text` and `json`

### Requirement: Trace identifiers appear when a span is active

When an OpenTelemetry span context is visible to the server, a JSON line SHALL carry `trace_id` and `span_id` as lowercase
hexadecimal strings of the active span. When no span context is visible, both fields SHALL be absent from the line. They
SHALL NOT be written as null.

#### Scenario: Active span

- **WHEN** a log line is written inside a span whose trace id is `4bf92f3577b34da6a3ce929d0e0e4736`
- **THEN** the JSON line contains `"trace_id":"4bf92f3577b34da6a3ce929d0e0e4736"` and a `span_id` field

#### Scenario: No agent

- **WHEN** no OpenTelemetry API is visible to the server
- **THEN** the JSON line contains neither `trace_id` nor `span_id`

