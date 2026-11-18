## ADDED Requirements

### Requirement: The dashboard console is a web REPL on the shared socket

The dashboard console SHALL connect to `/api/console/sock` with protocol version 1, authenticated by the session cookie of the browser
login. It SHALL show a log pane, an input line, and a confirmation dialog. Its input SHALL NOT be sent as an HTTP request.

#### Scenario: Command from the input line

- **WHEN** a signed-in admin types `status` and presses Enter
- **THEN** an `execute` frame is sent on the open socket, and no HTTP request is made for the command

#### Scenario: Result under the input

- **WHEN** the `result` of that command arrives
- **THEN** the exit code and the output lines are shown under the command, with stderr lines marked as errors

### Requirement: Log pane with level filters

The log pane SHALL show the `log` frames that pass the level filter of the page. The filter SHALL run in the client. The server threshold
SHALL only be raised by the page through `logLevel` in the hello. A line from another replica SHALL show the node that produced it.

#### Scenario: Filter hides lower levels

- **WHEN** the filter is set to `WARN`
- **THEN** `INFO` lines are hidden and no request is sent

#### Scenario: Line from another replica

- **WHEN** a `log` frame arrives with a `node` other than the one in the welcome
- **THEN** the line shows that node

### Requirement: Completion on Tab

Pressing Tab SHALL send one `complete` frame for the text of the input line. A single candidate SHALL complete the input. Several
candidates SHALL complete the common prefix and show the list. The page SHALL NOT send a frame per keystroke.

#### Scenario: One candidate

- **WHEN** the input is `tok` and Tab is pressed, and only `tokens` matches
- **THEN** the input becomes `tokens`

#### Scenario: No frame while typing

- **WHEN** a user types a command name without pressing Tab
- **THEN** no `complete` frame is sent

### Requirement: History per browser tab

The page SHALL keep the commands entered in the current browser tab, and Up and Down SHALL step through them. The history SHALL survive a
reload of the same tab, and SHALL NOT be shared with another tab or sent to the server. A command of a sensitive kind, as the listing
marks it, SHALL NOT be stored.

#### Scenario: Reload keeps history

- **WHEN** the page is reloaded in the same tab
- **THEN** Up shows the last command entered in that tab

#### Scenario: Sensitive command not stored

- **WHEN** `token-generate ci s3cr3t` is entered
- **THEN** Up does not show that line

### Requirement: Confirmation dialog

A `confirm` frame SHALL open a dialog that names the command and the reason. The dialog SHALL have cancel as its default action. Cancel
SHALL send `accept` false, and the confirm action SHALL send `accept` true. The dialog SHALL NOT show the arguments of a sensitive command.

#### Scenario: Cancel is the default

- **WHEN** the dialog opens and the user presses Enter
- **THEN** a `confirm` frame with `accept` false is sent and nothing runs

#### Scenario: Confirmed

- **WHEN** the user confirms `token-revoke ci`
- **THEN** a `confirm` frame with `accept` true is sent and the result shows `confirmed` true

### Requirement: Structured result rendering

A `result` SHALL show its exit code, its output lines with stdout and stderr told apart, and its `data` as a table when `data` is present.
ANSI sequences SHALL be shown as the dashboard shows them today.

#### Scenario: Failed command

- **WHEN** a result has exit code `1`
- **THEN** the exit code is shown and the stderr lines are shown as errors

#### Scenario: Data as a table

- **WHEN** a result of `tokens` carries `data`
- **THEN** the data is shown as a table and the text lines stay below it

### Requirement: Connection state and reconnect

While the socket is down the input SHALL be disabled and the page SHALL say so. The page SHALL reconnect with the backoff of the design and
SHALL send the `resume` of the last boot and `seq` it holds. It SHALL drop a replayed line whose `(node, seq)` it already shows. An `execute`
without a `result` at the drop SHALL show as unknown, and SHALL NOT be sent again.

#### Scenario: Drop during a command

- **WHEN** the socket closes while a command runs
- **THEN** the command shows as unknown, the input is disabled until the socket is back, and the command is not sent again

#### Scenario: Replayed line

- **WHEN** a resumed connection replays a line the page already shows
- **THEN** the line is shown once

### Requirement: Refusal by the server

When the server closes the socket with 1008, the page SHALL show the sign-in state and SHALL NOT reconnect in a loop.

#### Scenario: Session expired

- **WHEN** the socket is closed with 1008 after a session expiry
- **THEN** the page asks the user to sign in again and makes no further attempt until the user does
