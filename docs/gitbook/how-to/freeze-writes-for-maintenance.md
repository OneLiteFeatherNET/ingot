---
description: Turn on read-only mode so that every replica refuses writes while reads keep working, for a backup, a database failover or a storage cutover, and turn it off again.
---

# Freeze writes for maintenance

Read-only mode stops writes on every replica of an instance, and reads keep working. Use it when a copy of the data must not change while it is taken, for example a backup, a database failover or a move to other storage. Nothing has to be stopped, and the state is kept in the database, so a replica that restarts or joins later reads the same state.

## What happens while it is on

* `PUT`, `POST`, `PATCH` and `DELETE` on the API and on the Maven protocol answer `503` with a `Retry-After` header of 30 seconds, and nothing is written. The body is an RFC 9457 problem document.
* `GET` and `HEAD` keep working. Login and session refresh keep working, so users can still sign in.
* The read-only toggle itself keeps working, so you can turn the state off again.
* A caller without access still gets `401` or `403`, as before. Authentication and authorization are checked first.
* Scheduled cleanup runs, the nightly search verify and the search index rebuild do not start. A run that falls due while read-only is on is skipped for that day and is not started later. Runs resume the next day once read-only is off.
* The health endpoint `/api/status/health` stays `UP`.
* A `GET` on a proxy repository still stores the file it fetches, so the mirror keeps serving. Read-only mode does not block that cache fill.

## Turn it on

Pick one of these. The dashboard and the API need the maintenance area (`ingot.admin.maintenance`), which the instance admin always has.

{% tabs %}
{% tab title="Dashboard" %}
Open the status page and switch read-only mode on. The dashboard shows a banner while the state is on.
{% endtab %}

{% tab title="API" %}
```bash
curl -u "<token-name>:<token-secret>" -X POST https://ingot.example.com/api/admin/read-only
```

Check the state with `GET /api/admin/read-only`. The answer carries `enabled` and `source`.
{% endtab %}

{% tab title="Environment" %}
Set `INGOT_READ_ONLY=true` and restart the instance. The environment then forces read-only on this instance whatever is stored, and the toggle answers `409` for as long as the variable is set. Use this for a standby that must never accept writes. See [Environment variables](../reference/environment-variables.md#read-only-variables).
{% endtab %}
{% endtabs %}

The state reaches the other replicas within five seconds through the database. With a cluster bus configured, they re-read it at once. Wait at least 10 seconds after the change before you start the copy, so that writes which were in flight have finished.

## Turn it off

```bash
curl -u "<token-name>:<token-secret>" -X DELETE https://ingot.example.com/api/admin/read-only
```

Or switch it off on the dashboard. If the state is forced by `INGOT_READ_ONLY`, unset the variable and restart the instance instead. Turning the state on or off writes one audit event with the actor, so you can see who froze writes and when.

## Mixed clusters

Only the stored state is shared. An instance that the environment forces read-only refuses writes while the other replicas accept them. Operators who run such a mixed cluster must know this: a deploy that lands on a writable replica is not refused by the forced one. Keep the forced replica out of the load balancer's write path, or do not force it.

## Backups

Read-only mode alone does not give a consistent copy of an SQLite file, because the file is still open for the running instance. For SQLite, keep stopping the instance for the file copy. For MariaDB and PostgreSQL, read-only mode followed by the wait above gives a copy with no writes in it. See [Back up and restore](back-up-and-restore.md#without-stopping-the-instance).

## Related

* [Back up and restore](back-up-and-restore.md)
* [Environment variables](../reference/environment-variables.md)
* [API errors](../reference/api-errors.md)
* [HTTP API](../reference/http-api.md)
* [Audit events](../reference/audit-events.md)
