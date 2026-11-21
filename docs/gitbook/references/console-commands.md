---
description: Reference for the Ingot console. Lists every console command with its aliases, arguments and options, the access rules of the local and remote console, the version 1 protocol of the console WebSocket, and the --token startup option for temporary tokens.
---

# Console commands

The console runs commands on a live server. This page lists every command that the server registers, the ways to reach the console, the protocol of the console WebSocket, and the startup options that belong to the console and to temporary tokens.

## Ways to reach the console

| Entry point | How it receives commands | Who may use it |
| --- | --- | --- |
| Local console | Lines read from the standard input of the server process. | Anyone with access to that process. There is no login. |
| `POST /api/console/execute` | The request body is one command line. | A caller with the console area. |
| `GET /api/console/log` (SSE) | Streams the log. It does not run commands. | A caller with the console area. |
| `/api/console/sock` (WebSocket) | Two protocols on one path. In the legacy protocol the first message authenticates and every later message is a command line. In [version 1](#websocket-protocol-version-1) every message is a JSON frame. | A caller with the console area. |

Rules for the remote entry points:

* The caller authenticates with HTTP Basic credentials (`name:secret`) or with the session of a browser login. On the WebSocket, the first message is `Authorization:<name>:<secret>` or `Authorization:Session <jwt>`. On the SSE stream, a request without an `Authorization` header uses the session cookie.
* The check is the console area, the node `ingot.admin.console`. All three remote entry points check it, and it is the same for every command. No command has a permission of its own. An instance admin holds `ingot.admin.*` and so has the console area as well. See [Permission nodes](identity/permission-nodes.md).
* `POST /api/console/execute` answers `400` for an empty command and for a command longer than 1024 characters, `401` when the caller is not authenticated, and `403` when the caller has no console area. The WebSocket and the SSE stream refuse both cases the same way, as an authentication failure.
* The response has a `status` (`SUCCEEDED` or `FAILED`) and the output lines of the command. Add `?structured=true` to get an exit code and the output with its streams instead. See [Structured answers](#structured-answers).
* Every command that reaches execution through `POST /api/console/execute` or the WebSocket is recorded in the audit log as `CONSOLE_COMMAND_EXECUTED`, with the token as the actor. See [Audit events](identity/audit-events.md).

The local console starts with the server unless you disable it with `--disable-interactive-console`. If the process has no standard input, the server logs `Interactive CLI is not available in current environment.` and the local console stays off. After the start, the server runs `status` once and prints a hint to use `help` or `?`.

## Structured answers

Scripts and the [command line client](cli.md) need an exit code and the output with its streams. Both come from `POST /api/console/execute?structured=true`. The request body is the same command line as before, and a request without the parameter gets the answer it always got.

```json
{
  "status": "SUCCEEDED",
  "exitCode": 0,
  "output": [{"stream": "stdout", "text": "Ingot <version> Status"}],
  "data": null,
  "node": "ingot-0"
}
```

| Field | Meaning |
| --- | --- |
| `status` | `SUCCEEDED` or `FAILED`, as in the plain answer. |
| `exitCode` | `0` success, `1` the command failed, `2` a parse error or an unknown command. `3` is the answer of a dangerous command that was not confirmed, see below. |
| `output` | The output lines in order. `stream` is `stdout`, or `stderr` for every line of a failed command. Colour sequences are removed. |
| `data` | The same information as a structured value, for `tokens`, `status` and `stats`. It is `null` for every other command. The text lines are still in `output`. The token secrets and identifiers never appear in `data`. |
| `node` | The instance that ran the command. `level`, `status` and `failures` answer for one node, so this field tells you which one. |

The `data` of `tokens` is a list with one entry per token: `name`, `permissions` and `routes` (each with a `path` and its `permissions`). The `data` of `status` holds the version, whether the instance is alive, the uptime in milliseconds, the memory use, the thread count and the number of recorded failures. The `data` of `stats` holds the unique and all record counts and the matched requests of the search.

### Command listing

`GET /api/console/commands` returns the registered commands, for help and completion:

```json
{
  "version": 1,
  "commands": [
    {
      "name": "token-generate",
      "aliases": [],
      "description": "Generate a new access token",
      "dangerous": false,
      "sensitive": true,
      "scope": "cluster",
      "parameters": [{"label": "<name>", "index": "0", "required": true, "description": "Access token name"}],
      "options": [{"names": ["--secret", "-s"], "description": "Override generated token with custom secret", "arity": "1"}],
      "subcommands": []
    }
  ]
}
```

`sensitive` is true for a command that can hide its arguments in the log, such as `token-generate`. `dangerous` is true for a command that asks for confirmation in some form. The version is `1`. A client that does not know a version should refuse the listing.

`scope` says where a command acts when several replicas run behind a load balancer. `node` means the command answers for, or changes, only the replica that runs it: `level`, `status`, `failures`, `stats` and `cache`. `cluster` means it acts on the shared state and gives the same answer on every replica. Every other command is `cluster`, and so is a command from a plugin. A client should read an unknown value as `cluster`. The field was added within version 1, so a client that does not know it can ignore it.

### Confirmation of dangerous commands

A structured request for a dangerous command runs only with `confirm=true`. Without it the server answers `409` with the body `{"error": "confirmationRequired", "command": "<name>", "reason": "<description of the command>"}` and runs nothing. These are the dangerous commands:

* `stop`
* `token-revoke`
* `token-regenerate`
* `settings-reencrypt`
* `settings-decrypt`
* `cache` with the action `purge` (the `stats` action is not dangerous)

A command from a plugin is not dangerous unless the plugin says so. The confirmation is a guard against a mistake in a script, not a permission. The token that calls the command decides whether it may run at all.

A plain request (without `structured=true`) never asks, as before.

## WebSocket protocol version 1

`/api/console/sock` speaks two protocols on the same path. The first message of a connection decides which one:

* A first message that starts with `Authorization:` opens the **legacy protocol**, which Reposilite clients speak. It is unchanged: the log arrives as raw text lines, `keep-alive` is echoed, and a command line gets no answer frame.
* A first message that is a JSON object with `"type": "hello"` opens **version 1**. The dashboard console and the interactive mode of the [command line client](cli.md) use it.

The upgrade request is the same for both, so a legacy client never notices that version 1 exists. A socket that sends nothing stays open, as it always did. The worker deploy target serves no console, so the path does not exist there.

### Frames

Every frame is one UTF-8 text message holding one JSON object, and its `type` names it. A request that expects an answer carries an `id` that the client chooses, and the answer carries the same `id`. A client ignores the fields it does not know, because fields can be added within version 1.

Client to server:

| Type | Fields | Answer |
| --- | --- | --- |
| `hello` | `protocols` (the versions the client speaks), `token`, optional `client`, `logLevel` and `resume` | `welcome`, or an `error` and a close |
| `execute` | `id`, `command` (one command line), optional `confirm` (default `false`) | `result`, or first a `confirm` for a dangerous command |
| `confirm` | `id` (of the `execute` it answers), `accept` | the `result` of that `execute` |
| `complete` | `id`, `line`, optional `cursor` (default the end of the line) | `complete` |

Server to client:

| Type | Fields | Meaning |
| --- | --- | --- |
| `welcome` | `protocol`, `boot`, `seq`, `replay` | The connection is open. `boot` names the server process, `seq` is the number of its last log line, and `replay` says what a `resume` got. |
| `log` | `seq`, `node`, `level`, `text`, `dropped` | One log line. `seq` is unique within one `boot`. `node` names the replica that logged the line. `dropped` counts the lines lost before this one. |
| `result` | `id`, `status`, `exitCode`, `output`, `data`, `node`, `confirmed` | The answer to an `execute`. The fields are those of the [structured answer](#structured-answers), plus `confirmed`, which is true when the command ran after a confirmation. |
| `confirm` | `id`, `command`, `reason` | A dangerous command waits for the user. `command` is the command name only, so the arguments of a command, a secret among them, are never sent back. |
| `complete` | `id`, `replace` (`start`, `end`), `candidates` | The words that may replace the characters from `start` to `end` of the line: command names, aliases, options and fixed values. |
| `error` | `code`, `message`, optional `id`, `retryable`, `supported` | A refused request, or the reason the connection closes. |

A session looks like this. The token is the value `INGOT_TOKEN` holds, `name:secret`:

```json
{"type": "hello", "protocols": [1], "client": "ingot-cli/1.3.0", "token": "ci-rotation:<secret>"}
{"type": "welcome", "protocol": 1, "boot": "8f2c1e0a-...", "seq": 418, "replay": null}
{"type": "execute", "id": "1", "command": "token-revoke ci-old"}
{"type": "confirm", "id": "1", "command": "token-revoke", "reason": "Revoke token"}
{"type": "confirm", "id": "1", "accept": true}
{"type": "result", "id": "1", "status": "SUCCEEDED", "exitCode": 0, "output": [{"stream": "stdout", "text": "Token for 'ci-old' has been revoked"}], "data": null, "node": "ingot-0", "confirmed": true}
```

### Commands and confirmation

* At most one command waits per connection. While a dangerous command waits for its `confirm`, another `execute` gets an `error` with the code `busy` and `retryable: true`. A `complete` is answered at once, also while a command waits.
* A dangerous command (the list is in [Confirmation of dangerous commands](#confirmation-of-dangerous-commands)) runs at once when its `execute` carries `"confirm": true`. Otherwise the server sends a `confirm` frame and waits for the answer.
* A `confirm` with `accept: false`, or no answer within 120 seconds, runs nothing. The `result` then has `status` `FAILED`, `exitCode` `3`, no output and `confirmed: false`. A command that waits when the connection closes is dropped and does not run.
* A `confirm` for an id that no command waits for gets an `error` with the code `unknown_id`.
* Every command that runs, and every declined one, is recorded as `CONSOLE_COMMAND_EXECUTED` with the channel `websocket`.

### Authentication

* The `token` of the hello is checked like the first message of the legacy protocol: `name:secret` for a token, or `Session <jwt>` for the session of a browser login. The brute force limit of the console sign-in path applies, and the principal needs the console area.
* A refused hello gets an `error` with the code `unauthorized`, and the connection is closed with `1008`.
* The principal is checked again before each `execute` and before each accepted `confirm`. A revoked token, a removed console area or a session that is no longer valid gets an `error` with the code `unauthorized`, and the connection is closed with `1008`. Nothing runs. A client should not reconnect after `1008`.
* No frame, error or audit event contains the secret.

### Limits

| Limit | Value | What happens |
| --- | --- | --- |
| Frame size | 4096 bytes | A larger frame gets an `error` with the code `too_long`. The connection stays open. |
| Command line | 1024 characters | A longer or empty command gets an `error` with the code `bad_command`, where `POST /api/console/execute` answers `400`. |
| Log rate | 200 lines per second, bursts of 500 | Lines over the rate are dropped and counted in the `dropped` of the next delivered line. |
| Outbound queue | 1000 frames per connection | When the client reads too slowly, log lines are dropped first and counted. `result`, `confirm`, `complete` and `error` frames are never dropped. When the queue holds nothing else, the server sends an `error` with the code `slow_consumer` and closes with `1013`. |
| Log level | the server's visible level | `logLevel` in the hello can raise the level of the lines a connection receives, never lower it below what the server shows. |

A slow client never holds up the server: the thread that logs only queues the line, and one writer per connection sends it.

### Error codes and close codes

| `error` code | Meaning | Connection |
| --- | --- | --- |
| `unsupported_protocol` | The hello lists no version the server speaks. `supported` lists the versions it does. | Closed with `1002`. |
| `unauthorized` | The token or the session is refused, or no longer valid. | Closed with `1008`. |
| `shutdown` | The server is stopping. | Closed with `1001`. |
| `slow_consumer` | The client does not read its frames. | Closed with `1013`. |
| `busy` | A command waits for its confirmation. Send the `execute` again later. | Stays open. |
| `bad_frame` | The frame is not one JSON object, or an `execute` or `complete` has no `id`. | Stays open. |
| `bad_command` | The command line is empty or too long. | Stays open. |
| `too_long` | The frame is larger than 4096 bytes. | Stays open. |
| `unknown_type` | The `type` is not one of the client frames above. | Stays open. |
| `unknown_id` | No command waits for the `id` of this `confirm`. | Stays open. |
| `confirmation_required` | The command still asks for its confirmation after it was accepted. | Stays open. |

### Reconnect

A connection is one session. When it drops, the client opens a new one and sends `resume` in its hello, with the `boot` of the last welcome and the `seq` of the last log line it holds:

```json
{"type": "hello", "protocols": [1], "token": "ci-rotation:<secret>", "resume": {"boot": "8f2c1e0a-...", "seq": 412}}
```

* When `boot` names the running process, the server replays the cached lines after that `seq` and answers `"replay": "resumed"`.
* When it does not, because the server restarted or another replica took the connection, the server replays its whole line cache and answers `"replay": "full"`. A client de-duplicates the lines on `node` and `seq`.
* Without `resume`, the server replays nothing and sends only new lines.
* A command that had no `result` when the connection dropped may or may not have run. The server keeps no results for a resume, so the client reports it as unknown and does not send it again. The audit log records what ran.
* The dashboard and the command line client wait about 1 second before the first attempt, double the wait after each failed one, and stop at 30 seconds. They do not reconnect after `1008` or `unsupported_protocol`.

### Several replicas

A WebSocket stays on the replica that accepted it, so a load balancer needs no sticky sessions for the protocol to work. A command runs on that replica, and each `result` names it in `node`. The commands with the `scope` `node` in the [command listing](#command-listing) answer for that replica only. A reconnect can reach another replica, which the client notices from a `full` replay. Lines of other replicas arrive with their own `node` when the console log fan-out of the cluster is on. Proxies in front of Ingot need WebSocket upgrade support.

## How commands are parsed

* A command line is split at single spaces. Quotes are not interpreted, so an argument cannot contain a space.
* An unknown command returns `Unknown command <input>` with status `FAILED`.
* A missing required argument returns the error and the usage of the command with status `FAILED`.
* Plugins can add commands. A plugin registers them through `CommandsSetupEvent`. The commands below are the ones in the server itself. No bundled plugin adds a command.

## Commands

### Overview

| Command | Alias | Syntax | Description |
| --- | --- | --- | --- |
| `help` | `?` | `help [<command>]` | List of available commands. |
| `stop` | `shutdown` | `stop` | Shutdown server. |
| `level` | none | `level <level>` | Change current level of visible logging. |
| `status` | none | `status` | Display summary status of app health. |
| `stats` | none | `stats [<repository>] [<filter>]` | Display collected metrics. |
| `failures` | none | `failures` | Display all recorded exceptions. |
| `cache` | none | `cache <target> <action> [<repository>] [--top=<n>]` | Inspect or purge an Ingot cache. |
| `tokens` | none | `tokens` | List all generated tokens. |
| `token-generate` | none | `token-generate [--secret=<secret>] [--silent] <name> [<permissions>]` | Generate a new access token. |
| `token-modify` | none | `token-modify <name> <permissions>` | Change token permissions. |
| `token-rename` | none | `token-rename <name> <new name>` | Change token name. |
| `token-revoke` | none | `token-revoke <name>` | Revoke token. |
| `token-regenerate` | none | `token-regenerate <name> [--secret=<secret>] [--silent]` | Regenerate token secret. |
| `token-export` | none | `token-export <file>` | Export access tokens to file. |
| `token-import` | none | `token-import <file>` | Import access tokens from file. |
| `route-add` | none | `route-add <access_token> <path> <permissions>` | Add new route to access token or modify existing one. |
| `route-remove` | none | `route-remove <access_token> <path>` | Remove route from access token. |
| `settings-reencrypt` | none | `settings-reencrypt` | Seal the stored settings secrets with the active settings key. |
| `settings-decrypt` | none | `settings-decrypt` | Write the stored settings secrets in clear, for a downgrade. |

Square brackets mark optional arguments. All commands need the instance admin role on the remote entry points and nothing on the local console.

### Server commands

| Command | Arguments and options | Behavior |
| --- | --- | --- |
| `help` | `[<command>]`: the command to describe. | Without an argument, prints every command with its arguments and description. With a command name, prints the usage of that command. An unknown name fails with `Unknown command '<name>'`. |
| `stop` | none | Logs a warning and shuts the server down one second later. |
| `level` | `<level>`: the new threshold. The code default is `info`. | Sets the lowest level of log lines that the console shows. The accepted names come from the logging library and are not listed here. An unknown name sets the status to `FAILED`, but the output line still says that the level was set. |
| `status` | none | Prints the version, whether the server is active, the uptime, the memory use of the process, the active threads, the number of recorded failures and the latest released version. |
| `stats` | `[<repository>]`: limit to one repository, default all.<br>`[<filter>]`: a number or a phrase. | Prints the number of unique and of all resolved requests, then the matching records. A number is the limit of records, the default limit is 20. Any other text is a phrase that filters the records. |
| `failures` | none | Prints all recorded exceptions, or `No exception has occurred yet`. |

### `cache`

`cache <target> <action> [<repository>] [--top=<n>]`

| Argument or option | Required | Values | Meaning |
| --- | --- | --- | --- |
| `<target>` | yes | `resolution` | The cache to work on. Any other value fails with `Unknown cache '<target>'. Supported: resolution`. |
| `<action>` | yes | `stats`, `purge` | `stats` lists the entries of the cache per repository. `purge` clears the cache. Any other value fails. |
| `[<repository>]` | no | A repository name | Limit the command to one repository. The default is all repositories. An unknown name fails with `No repository named '<name>' found`. |
| `--top=<n>` | no | A number, default `20` | Number of entries that `stats` prints per repository. |

`stats` prints the number of cached entries per repository. Each listed entry shows `auth` or `anon`, the path prefix, the destination (`(local)`, a remote host or `(missing metadata)`) and the hit count. A repository without a resolution cache prints `resolution cache disabled`. `purge` prints how many entries it cleared across how many repositories.

### Token commands

These commands work on the legacy token store through the access token facade. See [Upgrade changes](../release-notes/upgrade-changes.md) for how Ingot reads these tokens and [Token migration](architecture/token-migration.md) for the reason.

| Command | Arguments and options | Behavior |
| --- | --- | --- |
| `tokens` | none | Prints the number of tokens, then each token with its permissions and its routes. A token without routes prints `~ no routes ~`. Temporary tokens from `--token` are listed too. |
| `token-generate` | `<name>`: the token name, required. It cannot contain `:`.<br>`[<permissions>]`: permission letters, default none.<br>`--secret=<secret>`, `-s`: use this secret instead of a generated one.<br>`--silent`: do not print the secret. | Creates a persistent token. Prints the secret unless `--silent` is set. The only permission letter is `m`, which marks the token as a manager token (`access-token:manager`). An unknown letter fails. |
| `token-modify` | `<name>`: the token to change.<br>`<permissions>`: the new permission letters. | Removes the existing permissions of the token and sets the given ones. Fails with `Token '<name>' not found` for an unknown name. |
| `token-rename` | `<name>`: the token to change.<br>`<new name>`: one word, the new name. | Changes the name of the token. Fails for an unknown name. |
| `token-revoke` | `<name>`: the token to revoke. | Deletes the token. The command prints `Token for '<name>' has been revoked` also when no token has that name. |
| `token-regenerate` | `<name>`: the token to change.<br>`--secret=<secret>`, `-s`: use this secret.<br>`--silent`: do not print the secret. | Replaces the secret of the token and prints the new one unless `--silent` is set. Fails for an unknown name. |
| `token-export` | `<file>`: path of the JSON file. | Writes all tokens to the file, temporary tokens included. The path is relative to the working directory. An existing file is overwritten. |
| `token-import` | `<file>`: path of a JSON file with tokens. | Reads the file and imports each token. The path is relative to the working directory. Prints one line per token, `Access token '<name>' has been imported.`. An unreadable file fails with the reason. |

### Route commands

Routes are the legacy path grants of a token.

| Command | Arguments | Behavior |
| --- | --- | --- |
| `route-add` | `<access_token>`: the token name.<br>`<path>`: the path prefix, for example `/releases/com/reposilite`.<br>`<permissions>`: letters `r` (read) and `w` (write). | Adds the route to the token. A path without a leading `/` gets one and the command says so. A route with the same path is replaced. Unknown letters fail and print the available ones. |
| `route-remove` | `<access_token>`: the token name.<br>`<path>`: the path of the route. | Removes the route with that path for every permission. Fails for an unknown token. |

### Settings commands

These commands act on the secret values of the shared settings. They need a settings key in `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE`, see [Settings encryption](settings/settings-encryption.md). Neither command takes an argument.

| Command | Arguments | Behavior |
| --- | --- | --- |
| `settings-reencrypt` | none | Seals every stored secret value with the active key and stores the key record. Prints `Sealed <n> stored secret values with the settings key id <key id>`, then `second factor seeds: <n> re-sealed, <n> already sealed with the active key`. A seed that no configured key opens is named in the output and left unchanged. Run it once on one replica after a key rotation. Without an active key it fails with `No settings key is active, set INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE first`. |
| `settings-decrypt` | none | Writes every stored secret value in clear and clears the key record. Prints `Wrote <n> stored secret values in clear, remove the settings key before starting an older build`. It needs the active key, and fails without one. Take an export first. |

Both commands fail with `The stored settings could not be written, the settings may be managed by a file or changed concurrently` when the stored settings cannot be saved. The steps for a rotation and for a return to an older build are in [Encrypt settings secrets](../manage/operate/encrypt-settings-secrets.md).

The help text of `route-add` states the matching rule: a route matches as a case-insensitive byte prefix, so `/releases` also covers `/releases-snapshots`. A trailing `/` anchors the route at a segment boundary, so `/releases/` does not match `/releases-snapshots`.

{% hint style="info" %}
The commands do not create user accounts, teams or permission nodes. Use the dashboard or the identity API for those.
{% endhint %}

## Startup options for the console

These options belong to the server start. Put them after `-jar ingot.jar`, or in `INGOT_OPTS` in a container.

| Option | Alias | Default | Effect |
| --- | --- | --- | --- |
| `--disable-interactive-console` | `--disable-it` | off | Do not read commands from the standard input. The remote console is not affected. |
| `--no-color` | none | off | Disable ANSI colors in console output. |
| `--channel=<name>` | `--level` | `INFO` | Default logging channel. An unknown name falls back to `INFO`. |
| `--test-env` | `--debug`, `-d` | off | Enable test mode. In test mode the local console and the automatic `status` at startup are off. |
| `--token=<name:secret>` | `-t` | none | Create a temporary token. See the next section. |

| Environment variable | Effect |
| --- | --- |
| `INGOT_OPTS` | Startup arguments of the container entrypoint, for example `--token admin:secret`. |
| `REPOSILITE_OPTS` | Read as a fallback when `INGOT_OPTS` is unset or empty. |

The entrypoint prepends `--working-directory=/app/data` unless the arguments already contain `--working-directory` or `-wd`.

## The `--token` option

`--token name:secret` creates a temporary access token at startup. Use it to get a first admin credential, or to regain access when no admin can sign in.

| Property | Value |
| --- | --- |
| Format | `name:secret`. The value is split at the first `:`, so the secret can contain `:`. The name cannot. |
| How many | The option can be repeated, one token per occurrence. |
| Storage | In memory only. Nothing is written to the database or to a file. |
| Lifetime | Until the server stops. Pass the option again on every start. |
| Permission | The instance admin role (`access-token:manager`). The token has no expiry date. |
| Authentication | HTTP Basic with `name` and `secret`. See the following section. |
| Audit | The creation is not written to the audit log. |
| Last-admin rule | A temporary token does not count as an admin when Ingot checks that at least one usable admin remains. |
| Upgrade | A temporary token is not stored and not migrated. |

### Where the token is valid

The token is valid for HTTP Basic authentication. Basic accepts the name and the secret of any token the access token facade holds, and the temporary token is one of them. That includes the console endpoints above.

The token is not valid for the other two ways to authenticate:

* A `Bearer` request names its token through the token id inside the secret. The lookup uses the identity tables, so a secret from `--token` names no token and the request fails.
* A browser session is issued by a login of a user principal. A temporary token is not a user principal.

{% hint style="warning" %}
A temporary token replaces a persistent token with the same name. At startup the persistent token is deleted with its secret, its rights and its legacy grants. The machine principal of that name stays as an empty shell. A test in the repository pins this behavior. Pick a name that no existing token uses.
{% endhint %}

{% hint style="info" %}
In the container, pass the option through `INGOT_OPTS`, for example `INGOT_OPTS=--token admin:secret`. A value in `INGOT_OPTS` replaces the arguments of an existing `REPOSILITE_OPTS`, because the entrypoint reads only one of them.
{% endhint %}

## Database snapshot: `--snapshot-database`

`--snapshot-database` is a startup option, not a console command. It writes one online snapshot of the SQLite database into the `backups` directory of the working directory, prints its name and size, and exits without starting the server, the plugins, a port or the console. The audit log names the actor `command-line`.

```sh
java -jar ingot.jar --working-directory=/app/data --snapshot-database
```

In a container, run the same command in the running container, as the service account, as shown in [Back up and restore](../manage/operate/back-up-and-restore.md):

```sh
docker exec --user reposilite <container> java -jar /app/ingot.jar --working-directory=/app/data --snapshot-database
```

On success the command prints `Snapshot written: <file name> (<size> bytes) in backups/` and exits with `0`. On failure it prints `Ingot did not write a snapshot: <reason>` to the error output and exits with `1`. Only SQLite databases are supported. For MariaDB or PostgreSQL the command refuses before it connects and names the dump tool to use instead.

## Related

* [Upgrade changes](../release-notes/upgrade-changes.md): how tokens are migrated and what happens to a `--token` token.
* [Encrypt settings secrets](../manage/operate/encrypt-settings-secrets.md): the rotation steps behind `settings-reencrypt` and `settings-decrypt`.
* [Back up and restore](../manage/operate/back-up-and-restore.md): when to take a snapshot and how to restore one.
* [Upgrade from Reposilite 2.x](../manage/upgrade/upgrade-from-reposilite-2.md): uses `token-import`.
* [Token migration](architecture/token-migration.md): why tokens become machine accounts.
