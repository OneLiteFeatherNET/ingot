---
description: Reference for the ingot command line client, which runs console commands on a running Ingot from a terminal or a script. Lists the commands, the options, the environment variables, the config file, the token sources, the exit codes and the behaviour of the interactive mode over the console WebSocket.
---

# Command line client

`ingot` runs the [console commands](console-commands.md) of a running Ingot from another program. It talks to the console API with a token, so it works from a workstation, a CI job or a pod in the same cluster. It does not read the server's files and it does not start a server.

Two modes exist:

* **Exec mode** runs one command, or a script of commands, and exits with a code that says what happened. Use it in scripts and CI. It sends one HTTP request per command line (`POST /api/console/execute?structured=true`). A prompt never appears unless the program runs on a terminal.
* **Interactive mode** opens a prompt with line editing, history and completion, and shows the server log above the prompt. It starts when the program runs with no words on a terminal. It keeps one connection to the console WebSocket open for the whole session, in [protocol version 1](console-commands.md#websocket-protocol-version-1), the same protocol the dashboard console uses.

## Usage

```bash
ingot exec status
ingot exec token-revoke ci-rotation --yes
ingot exec - < maintenance.txt          # one command per line, from standard input
ingot                                   # interactive mode, on a terminal
```

| Form | Meaning |
| --- | --- |
| `ingot exec <command> [args...]` | Runs one command. The arguments are joined with single spaces, which the server splits on. |
| `ingot exec -` | Runs the commands of standard input, one per line. Blank lines and lines that start with `#` are skipped. |
| `ingot` | The interactive mode. Without a terminal it prints the usage and exits with code 2. |

An argument that contains a space is refused with exit code 2 before anything is sent. The server splits a command line on spaces and does not read quotes, so such an argument could not reach the command intact.

## Options

Options are given before or after the command. A value option takes the next word.

| Option | Variable | Meaning |
| --- | --- | --- |
| `--url <url>` | `INGOT_URL` | Base URL of the server, for example `https://ingot.example.org`. A context path is kept, so `https://example.org/ingot` is served under `/ingot`. |
| `--context <name>` | `INGOT_CONTEXT` | The context of the config file to use. |
| `--token-file <path>` | `INGOT_TOKEN_FILE` | A file whose first line is the token. |
| `--credential-helper <program>` | `INGOT_CREDENTIAL_HELPER` | A program that prints the token on its first line. It runs without arguments and without a shell. |
| `--ca-cert <path>` | `INGOT_CA_CERT` | A PEM certificate of a CA that signed the server certificate. It is added to the default trust store. |
| `--insecure` | `INGOT_INSECURE=true` | Turns off certificate verification. A warning is printed on every run. Only a flag or the variable can turn it on, never the config file. |
| `--output text\|json` | `INGOT_OUTPUT` | `text` prints the output lines. `json` prints one JSON object per command, with the exit code, the node and the structured data when the command has one. |
| `--timeout <seconds>` | `INGOT_TIMEOUT` | Time to wait for the server. The default is 30. |
| `--yes` | none | Confirms a dangerous command without asking. |
| `--keep-going` | none | In a script, runs the commands after a failed one. The exit code is then the highest one. |

The token has no option. The `--token` option is refused with exit code 2, because a token on the command line stays in the shell history and shows in the process list.

## Environment variables

| Variable | Meaning |
| --- | --- |
| `INGOT_URL` | The server URL. |
| `INGOT_TOKEN` | The token as `name:secret`. The first source of the token. |
| `INGOT_TOKEN_FILE` | The file with the token. |
| `INGOT_CREDENTIAL_HELPER` | The helper program that prints the token. |
| `INGOT_CONTEXT` | The context of the config file to select. |
| `INGOT_CONFIG` | The config file to read instead of the default location. |
| `INGOT_CA_CERT` | The CA certificate to trust in addition to the default store. |
| `INGOT_INSECURE` | `true` turns off certificate verification. |
| `INGOT_OUTPUT` | `text` or `json`. |
| `INGOT_TIMEOUT` | Seconds to wait for the server. |
| `NO_COLOR` | Set and not empty, it removes the colour of the server output. Colour is also removed when standard output is not a terminal. |
| `XDG_STATE_HOME` | The directory under which the interactive mode keeps its history, see [History](#history). |

The client reads no `REPOSILITE_` variable. These names are new, so no older spelling is kept. `NO_COLOR` and `XDG_STATE_HOME` are the common names other programs read too.

## Order of precedence

Each setting is resolved from the first source that gives it:

1. The command-line option.
2. The `INGOT_` environment variable.
3. The selected context of the config file.
4. The default.

The token is the exception. It comes from `INGOT_TOKEN`, then the token file, then the credentials helper. The first one that is set is used, and the client does not look at the others.

## Config file

The config file names servers, so that a script does not need to repeat the URL and the token source. It is read from `INGOT_CONFIG`, then `$XDG_CONFIG_HOME/ingot/config.yaml`, then `~/.config/ingot/config.yaml`.

```yaml
current: production
contexts:
  - name: production
    url: https://ingot.example.org
    tokenFile: /home/operator/.config/ingot/production.token
    caCert: /home/operator/.config/ingot/internal-ca.pem
  - name: staging
    url: https://staging.ingot.example.org
    credentialHelper: /usr/local/bin/ingot-token-from-vault
```

Each context takes `url`, `tokenFile`, `credentialHelper` and `caCert`. The client refuses a config file that holds a `token` value or an `insecure` setting, and says to use `tokenFile` instead. Keep the token file readable only by its owner. The client prints a warning when the file is readable by other users, and the warning names the file, never the token.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | The command succeeded, or the script ran all its commands successfully. |
| `1` | The command failed. Its failure lines are on standard error. |
| `2` | A usage error, a parse error, an unknown command, or an argument with a space. |
| `3` | A dangerous command was not confirmed. Nothing ran. Pass `--yes` in a script that means it. |
| `4` | The server refused the token, or the token has no console area. In interactive mode also when the server ends the session because the token was revoked or the session expired. |
| `5` | The server could not be reached within the timeout. |
| `6` | The server is older than this client needs: in exec mode it has no structured console API, in interactive mode it does not speak version 1 of the console WebSocket protocol. |

In a script with several commands, the client stops at the first command that does not exit with `0`, unless `--keep-going` is given. It then exits with that code.

## Output streams

Standard output carries the output of the command and nothing else. Failure lines of a command go to standard error, and so do warnings and notices. A pipe such as `ingot exec tokens | grep ci` therefore sees only the lines that the command printed.

## Confirmation of dangerous commands

A command that changes state in a way that is hard to undo waits for confirmation. These commands are dangerous: `stop`, `token-revoke`, `token-regenerate`, `settings-reencrypt`, `settings-decrypt`, and `cache` with the action `purge`.

* On a terminal, the client asks `Run '<command>' (<reason>)? [y/N]`. Only `y` or `yes` runs it.
* In a pipe or a script, it never asks. The command does not run and the exit code is `3`, unless `--yes` is given.

The confirmation guards against a typing mistake in a script. It is not a permission. A token that may use the console can confirm its own commands, so the token decides what can run.

## Interactive mode

On a terminal, `ingot` opens a prompt that names the context of the config file, or the server URL when no context is selected, such as `ingot (production)> `.

* Each line is one command. It is sent over the console WebSocket, and its output prints when the result arrives. No HTTP request is made while the session runs.
* Tab asks the server once for the words that may stand at the cursor: command names, aliases, options and fixed values. Typing sends nothing.
* Log lines of the server print above the prompt as they arrive. When the server dropped lines because the client read too slowly, a line says how many.
* A dangerous command asks `Run '<command>' (<reason>)? [y/N]` before it runs. Only `y` or `yes` runs it. Enter, Ctrl-C and Ctrl-D answer no, and the command does not run. `--yes` answers yes for the whole session.
* Ctrl-C while a command runs stops the wait for its result. The command keeps running on the server, its result is not shown, and the connection stays open. Ctrl-C at the prompt discards the line being typed.
* `:quit` leaves. Ctrl-D on an empty line also leaves, with exit code `0`.
* Lines starting with `:` are the client's own commands, and any other line goes to the server:

| Builtin | Effect |
| --- | --- |
| `:quit` | Leaves the interactive mode with exit code `0`. |
| `:output text\|json` | Switches the output of the next commands. `json` prints one JSON object per command, as in exec mode, with the `node` that ran it and `confirmed`. |
| `:context` | Prints the context of the session. |
| `:context <name>` | Ends the session and opens a new one with that context of the config file, as if `--context <name>` had been given. A context the config file does not resolve is refused and the session goes on. |

### History

The lines typed in interactive mode are kept for the next session in `$XDG_STATE_HOME/ingot/history`, or `~/.local/state/ingot/history` when `XDG_STATE_HOME` is not set. The client creates the file and its directory readable only by their owner, and narrows an existing file to its owner. A line that may carry a secret is not written to it: `token-generate`, `token-regenerate`, and any line with a `--secret` or `-s` option. When the file cannot be created, the client prints a warning and keeps the history of the session in memory only.

### Reconnect

When the connection to the server drops while a command runs, or a line is typed after it dropped, the client says so and opens a new one. It waits about 1 second before the first attempt and doubles the wait after each failed one, up to 30 seconds. Ctrl-C leaves while it waits.

* The new connection resumes the log after the last line the client showed, so no line is shown twice. When the server restarted, or another replica answers, the server shows its cached log again, and the client says so.
* A command that had no result when the connection dropped may or may not have run. The client says so and does not send it again. Check the audit log, or run a command that shows the state, before you repeat it.
* A line typed while the connection is down is not sent. Type it again once the client says it reconnected.
* When the server stops, the client reports that the command may or may not have run and reconnects after one wait.
* When the server ends the session with `1008`, because the token was revoked, the console area was removed or the session expired, the client does not reconnect and exits with code `4`.

### Commands on several replicas

Behind a load balancer the session stays on one replica, and every command of the session runs there. Some commands act only on the replica that runs them: `level`, `status`, `failures`, `stats` and `cache`. The [command listing](console-commands.md#command-listing) marks them with the `scope` `node`. Every other command, plugin commands included, has the `scope` `cluster` and gives the same answer on every replica. The JSON output names the replica in `node`. To reach a particular replica, point `--url` at it rather than at the load balancer.

### Older servers

The interactive mode needs a server that speaks version 1 of the console WebSocket protocol. An older server refuses the opening message of the session the way it refuses a wrong token, so the client then asks for the command listing over HTTP. When the server has none (`GET /api/console/commands` answers `404`), it is a release older than the protocol: the client says so and exits with code `6` before it runs anything. Any other answer means the token was refused, and the exit code is `4`.

Exec mode needs the structured console API: against a server release that predates it, each command ends with exit code `6`, and the client reports that once for each command. A server that has the structured API but no command listing runs commands in exec mode.

## Container image

The client ships as `ghcr.io/onelitefeathernet/ingot-cli` for `linux/amd64` and `linux/arm64`. The image runs as user `10001`, its entrypoint is `ingot`, and it has no volume. Give it the server URL and a token file through a secret, as in [Run the command line client](../manage/operate/run-the-cli.md).

## Related

* [Console commands](console-commands.md): the commands, their arguments and what the structured answer carries.
* [Environment variables](configuration/environment-variables.md): the rest of the variables of the server and the images.
* [Audit events](identity/audit-events.md): each command run through the client is recorded as `CONSOLE_COMMAND_EXECUTED`.
