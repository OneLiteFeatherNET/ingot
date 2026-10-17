---
description: Reference for the Ingot console. Lists every console command with its aliases, arguments and options, the access rules of the local and remote console, and the --token startup option for temporary tokens.
---

# Console commands

The console runs commands on a live server. This page lists every command that the server registers, the ways to reach the console, and the startup options that belong to the console and to temporary tokens.

## Ways to reach the console

| Entry point | How it receives commands | Who may use it |
| --- | --- | --- |
| Local console | Lines read from the standard input of the server process. | Anyone with access to that process. There is no login. |
| `POST /api/console/execute` | The request body is one command line. | A caller that is an instance admin. |
| `GET /api/console/log` (SSE) | Streams the log. It does not run commands. | A caller that is an instance admin. |
| `/api/console/sock` (WebSocket) | The first message authenticates, every later message is a command line. | A caller that is an instance admin. |

Rules for the remote entry points:

* The caller authenticates with HTTP Basic credentials (`name:secret`) or with the session of a browser login. On the WebSocket, the first message is `Authorization:<name>:<secret>` or `Authorization:Session <jwt>`. On the SSE stream, a request without an `Authorization` header uses the session cookie.
* The check is the instance admin role. It is the same for every command. No command has a permission of its own.
* The admin area `ingot.admin.console` exists in the node grammar, but the three remote entry points do not check it.
* `POST /api/console/execute` answers `400` for an empty command and for a command longer than 1024 characters, and `401` when the caller is not an instance admin.
* The response has a `status` (`SUCCEEDED` or `FAILED`) and the output lines of the command.

The local console starts with the server unless you disable it with `--disable-interactive-console`. If the process has no standard input, the server logs `Interactive CLI is not available in current environment.` and the local console stays off. After the start, the server runs `status` once and prints a hint to use `help` or `?`.

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

These commands work on the legacy token store through the access token facade. See [Upgrade changes](upgrade-changes.md) for how Ingot reads these tokens and [Token migration](../explanation/token-migration.md) for the reason.

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

## Related

* [Upgrade changes](upgrade-changes.md): how tokens are migrated and what happens to a `--token` token.
* [Upgrade from Reposilite 2.x](../how-to/upgrade-from-reposilite-2.md): uses `token-import`.
* [Token migration](../explanation/token-migration.md): why tokens become machine accounts.
