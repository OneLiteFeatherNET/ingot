---
description: Send the audit events of an Ingot instance to a log collector or SIEM as JSON lines, through standard output or a file, with rotation, filtering, replicas and retention.
---

# Forward audit events to a SIEM

The audit log is a table that admins read in the dashboard or export as CSV. A security team usually wants the events in its own collector, next to the other logs. The JSON lines sink writes each audit event as one line, to standard output, to a file, or to both. This guide shows the two targets, how to filter the lines in the collector, and what the operator must keep in mind for replicas, retention and personal data.

Nothing changes until you set one of the variables. The table keeps working as before.

## Before you begin

* The variables of the sink are set on the instance, in the same place as the other environment variables of your deployment.
* A collector that reads container output or files, for example Loki, Elasticsearch, Splunk or a cloud log service, or a sidecar that does.
* For a file, a writable path. Under a read-only root filesystem, use a volume, as for the log file. The data directory `/app/data` of the container image is such a volume.

## Choose a target

| Target | Variable | Use it when |
| --- | --- | --- |
| Standard output | `INGOT_AUDIT_SINK_STDOUT=true` | The platform already collects container output. This needs no volume, so it is the usual choice on Kubernetes. The lines share the stream with the normal log, and the `schema` field tells them apart. |
| File | `INGOT_AUDIT_SINK_FILE=<path>` | The collector reads files, or you want audit lines in a stream of their own. The file needs a volume that the collector or a sidecar can read, and rotation is yours to set up. |

Pick one target per replica. With both set, every event is written twice, once to each target, and a collector that reads both counts it twice.

## Write to standard output

Add the variable to the existing service. Only the environment changes, the image line stays as it is:

```yaml
services:
  ingot:
    environment:
      INGOT_AUDIT_SINK_STDOUT: "true"
```

The lines appear in the container output next to the log. Check them with:

```bash
docker logs ingot 2>&1 | grep '"schema":"ingot.audit"' | tail -n 1
```

## Write to a file

Set the path to a location on a volume that survives a restart. The directory is created when it is missing:

```yaml
services:
  ingot:
    environment:
      INGOT_AUDIT_SINK_FILE: /app/data/audit/audit.jsonl
```

The file is created with mode `0640`, owned by the user that runs the server. The sink appends to it and never rotates, compresses or deletes it.

### Rotate with logrotate

Rotate the file with `logrotate` in `create` mode. The sink notices that the path names a new file within a second, so it needs no signal. Adapt the paths and the user to your host:

```text
/var/lib/ingot/audit/audit.jsonl {
    daily
    rotate 30
    compress
    missingok
    notifempty
    create 0640 reposilite reposilite
}
```

`copytruncate` also works, because the file is opened in append mode.

### Read the file from a sidecar

In a pod or a compose project, a sidecar container can mount the same volume read-only and ship the file. Any agent that tails a file and parses JSON works, for example Fluent Bit with its `tail` input and the `json` parser.

## Filter the lines in the collector

Every line has `"schema":"ingot.audit"`. Filter on that field, so that audit lines are separated from the normal log, and keep the rule that matches `version` in mind.

For Loki, with the container logs in one stream:

```text
{container="ingot"} | json | schema="ingot.audit"
```

For Vector, a filter transform on the container logs:

```toml
[transforms.audit_only]
type = "filter"
inputs = ["ingot_logs"]
condition = '.schema == "ingot.audit"'
```

A rule that also checks `version` keeps matching after an upgrade, since `version` changes only when a field breaks. Consumers must ignore fields they do not know, so a new field does not require a change in the collector.

## Run several replicas

Each replica writes the events it recorded, and every line names it in `node`. The table is the shared record, and the lines are the stream for the collector. Merge the streams by `time`. Lines of different replicas are not ordered against each other, so do not rely on the order of arrival. A scheduled job, such as the retention purge, is written by the replica that ran it.

## Set retention and erasure

`retentionDays` in the audit settings deletes rows from the table. It does not apply to the lines. The lines are kept as long as the collector and the rotated files keep them, so set those to the retention your policy requires.

Ingot never rewrites the file or the collector's store. If a person must be erased from the lines, do it in the collector, or delete the files that hold them. Ingot does not claim that this meets any particular legal requirement.

## Check that it works

1. Make a change that is audited, for example save the `maven` settings domain or create a token.
2. Find the line for it in the output or the file. It has the action, the actor and the time of the change.
3. Look for warnings. A line that the sink could not keep is counted, and at most one warning a minute names the counts and the last error:

```text
AUDIT | JSON sink lost 3 line(s) on a full queue and 0 line(s) on write errors, last error: none
```

A full queue means the writer is slower than the events, for example a disk that does not keep up. A write error names the file and the cause, such as a missing permission or a full disk.

## Next steps

* Read every field, the compatibility rule and the personal data notes in [Audit events](../reference/audit-events.md#json-lines-sink).
* Read the switches in [Environment variables](../reference/environment-variables.md#audit-sink-variables).
* Read the audit log in the dashboard in [Review the audit log](review-audit-log.md).
