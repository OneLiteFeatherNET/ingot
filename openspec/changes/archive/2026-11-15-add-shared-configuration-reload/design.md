# Design

## Context

The file source of shared settings is read once. `LocalSharedConfigurationProvider` reports no update and is not mutable, so
the scheduled poll in `SharedConfigurationPlugin` never runs for it, and a save writes the file back. A GitOps workflow mounts
the file from a ConfigMap, which is read-only and is updated by the kubelet through a `..data` symlink swap.

## Decisions

### One switch for watching and for the read-only rule

The reload and the read-only rule are one switch, not two. A file that is watched but still writable would be overwritten by
the next save from the API, and the poll would then load the overwritten content as a change. A file that is read-only but not
watched would reject saves and never show the operator's commits. The two only make sense together.

### Content hash, not modification time

The provider compares the SHA-256 of the content it last loaded with the hash of the file read now. A modification time check
misses a rewrite that keeps the time, and it reacts to a touch with no change. The hash also follows the symlink target, so the
kubelet's `..data` swap is seen as the change it is. Hashing a settings file takes microseconds at a ten second interval.

### Baseline recorded when the content is handed to the loader

The hash is recorded from the content that `fetchConfiguration` returns, not from a separate read in the poll. A file that
changes between the check and the load is then loaded with its own hash, and the next poll compares against what was really
applied. A document that does not parse therefore moves the baseline too, which makes the ERROR appear once per content and
not on every poll.

### The guard sits in the facade

The `409` is checked in `SharedConfigurationFacade.updateDomain` and `updateSharedSettings`, after the domain lookup, not in the
HTTP endpoint. Plugins call the facade directly, so a guard in the endpoint would leave a second write path open. The endpoint
keeps its current order of checks, so an unknown domain still answers `404`.

### Broken documents are logged by the reload path

The parse failure is logged in `reloadIfChanged`. Today a document that does not parse is skipped without a message on the
reload path. The new log line applies to every provider that reaches this path, which changes only the log output of the
database source. Settings values are unchanged.

## Consequences

- The API cannot change a domain under the switch, so a correction has to be committed to the file. A broken file keeps the
  previous settings until the corrected file is mounted.
- The dashboard keeps showing editable forms for those domains until its own change lands. A save then shows the `409` message.
- Other instances in a cluster each read their own copy of the file. Cluster bus events do not carry file content.
