# Spec Delta

## Purpose

An operator or a script runs console commands on a running Ingot from a separate `ingot` program, interactively or not, with the
token kept out of the command line and the shell history.

## ADDED Requirements

### Requirement: Token never on the command line

The CLI SHALL read the token from `INGOT_TOKEN`, from the file named by `INGOT_TOKEN_FILE`, or from a credentials helper, and SHALL NOT
accept it as an argument. It SHALL NOT print the token in output, errors or logs, and SHALL send it only in the `Authorization` header.

#### Scenario: No flag

- **WHEN** the CLI is started with `--token abc`
- **THEN** it exits with code `2` and the usage says to use `INGOT_TOKEN`, `INGOT_TOKEN_FILE` or a credentials helper

#### Scenario: Verbose output

- **WHEN** a command fails with `--verbose` set
- **THEN** the diagnostic output contains no part of the secret

### Requirement: Configuration order and contexts

The CLI SHALL resolve each setting from the flag, then the `INGOT_` environment variable, then the selected context of the config file, then the
default. It SHALL refuse a config file that holds a token value, and SHALL read no `REPOSILITE_` variable.

#### Scenario: Environment beats context

- **WHEN** the context names `https://a` and `INGOT_URL=https://b` is set
- **THEN** requests go to `https://b`

### Requirement: Exec mode

`ingot exec <command> [args]` and commands on standard input SHALL run non-interactively. The CLI SHALL write only command output
(or JSON) to standard output, SHALL NOT prompt unless standard input and output are terminals, and with `--output json` SHALL print one
valid JSON object per command.

#### Scenario: Script fails the step

- **WHEN** `ingot exec token-revoke ci` runs in a pipe without `--yes`
- **THEN** nothing is revoked, no prompt is shown and the exit code is `3`

#### Scenario: Confirmed in a script

- **WHEN** the same command runs with `--yes`
- **THEN** the token is revoked and the exit code is `0`

#### Scenario: Argument with a space

- **WHEN** an argument contains a space
- **THEN** the CLI exits with `2` before sending anything

### Requirement: Exit codes

The CLI SHALL exit `0` on success, `1` when the command failed, `2` for usage errors, parse errors and unknown commands, `3` when a dangerous
command is not confirmed, `4` when authentication or permission fails, `5` when the server cannot be reached and `6` when the server lacks a
needed feature.

#### Scenario: Wrong token

- **WHEN** the server refuses the token
- **THEN** the exit code is `4`

#### Scenario: Server down

- **WHEN** the server cannot be reached within the timeout
- **THEN** the exit code is `5`

### Requirement: REPL mode

The CLI SHALL offer an interactive mode with line editing, a history file with owner-only permissions, completion of command names,
aliases, options and fixed values from `/api/console/commands`, and multi-line output. `Ctrl-C` SHALL discard the current line and `Ctrl-D` on an
empty line SHALL leave with exit code `0`. Colour SHALL be used only when standard output is a terminal and `NO_COLOR` is unset. Lines of a
`sensitive` command SHALL NOT be saved to history.

#### Scenario: Confirmation in the REPL

- **WHEN** the user enters `stop` in the REPL
- **THEN** the CLI asks `y/N` on the terminal and sends nothing unless the answer is yes

#### Scenario: Older server

- **WHEN** the server has no `/api/console/commands`
- **THEN** the CLI notes it once, runs in plain mode and warns that dangerous commands will run at once

### Requirement: TLS

The CLI SHALL verify the server certificate against the default trust store plus an optional CA file, SHALL warn on every run when verification
is disabled with `--insecure`, and SHALL NOT allow disabling it in the config file.

#### Scenario: Custom CA

- **WHEN** `INGOT_CA_CERT` points to the CA that signed the server certificate
- **THEN** the connection succeeds without `--insecure`
