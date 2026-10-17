---
description: Review who did what on an Ingot instance with the dashboard audit log, filter the events, and export them as CSV.
---

# Review the audit log

The audit log records deployments, deletions, settings changes, token and account changes, and sign-ins on the instance. This guide shows how to read it in the dashboard, narrow it to one question, and export the result as CSV.

## Prerequisites

* An account that is an instance admin. The audit API answers only instance admins, and the dashboard page uses it.
* The address of the instance, for example `https://repo.example.org`.
* At least one recorded event. The audit log has no on/off switch (the `audit` settings domain holds only the retention and the address setting), so it records from the first start. Your own sign-in is an event, so a fresh instance is never empty after you sign in.

{% hint style="info" %}
The Audit entry in the navigation is shown to a session that is allowed the node `ingot.admin.audit`. The endpoints behind the page check for an instance admin instead. A principal that holds only `ingot.admin.audit` can therefore open the page but not load events from it.
{% endhint %}

## Read the log

1. Sign in to the dashboard as an instance admin.
2. Open the **Audit** page. On a wide screen it is an entry in the navigation rail. On a phone it is under **More**. You can also open `/audit` on the instance directly.
3. Read the list. The newest event is first. Each row shows the time, the actor (with the client address, if one was recorded), the action, the target, and **Success** or **Failed**.
4. Select **Details** on a row to see the event id, the UTC time, the repository, the path and the event details. Select **Hide details** to fold it again.

### Result

The page shows the heading **Audit log** and a line such as `Showing 50 events, more are available`. The first page holds up to 50 events. Select **Load more** to add the next page. If the list is empty, the page says `No events match these filters.`

A row that reads `5 attempts` is a folded failed sign-in: failed attempts by one name from one address within the same minute share one row.

## Narrow the log to one question

1. Type into the text fields in the filter form and select **Apply filters** (or press Enter):
   * **Search**: free text.
   * **Actor**: the name of the account that acted.
   * **Repository**: the repository the event touched.
2. Pick one action in the **Action** select, for example `Artifact deployed` or `Settings changed`. The first option, **All actions**, removes the filter.
3. Or select one of the chips **Accounts**, **Teams**, **Instance admin**, **Sessions and passwords**. Each chip filters for several identity actions at once. Select the chip again to clear it.
4. Select a time range chip: **All time**, **24 hours**, **7 days**, **30 days** or **Custom**. With **Custom**, the **From** and **To** fields appear. Enter both bounds, or one of them. They are read in your browser's time zone.

The filters are kept in the address of the page (`action`, `actor`, `repository`, `q`, `range`, `from`, `to`). Copy the address to share a filtered view.

### Result

The count line and the rows change to the matching events only. A filter with no match shows `No events match these filters.`

## Export the log as CSV

### From the dashboard

1. Set the filters you want on the **Audit** page. The export uses the current filters. It does not use the 50 rows on screen: it exports every matching event.
2. Select **Export CSV**.

The browser saves `ingot-audit.csv`. If the export fails, the page shows a toast that starts with `Could not export the audit log:`.

### From the API

Use this for scripts. Call `GET /api/audit/export` with a manager credential. The filters are query parameters:

| Parameter | Meaning |
|---|---|
| `actor` | Name of the acting account. |
| `action` | One or more action names. Separate them with commas or repeat the parameter. An unknown name answers `400`. |
| `repository` | Repository name. |
| `q` | Free text. |
| `from`, `to` | ISO-8601 instants such as `2026-10-15T00:00:00Z`. `from` must be before `to`. |

For example, deployments since the start of a day:

```bash
curl -u <admin>:<secret> -o ingot-audit.csv \
  "https://<your-host>/api/audit/export?action=ARTIFACT_DEPLOYED&from=2026-10-15T00:00:00Z"
```

`GET /api/audit` takes the same filters and returns a JSON page with `limit` (1 to 200, default 50) and a `cursor` for the next page.

### Result

The file starts with this header line, followed by one line per event, newest first:

```
id,timestamp,actor,actorAddress,action,repository,path,success,occurrences,details
```

The `details` column holds the event details as JSON. A cell that begins with `=`, `+`, `-` or `@` gets a leading apostrophe so a spreadsheet does not run it as a formula. An export stops at 100000 rows and writes a warning to the server log. Narrow the filters to export the rest.

## Next steps

* Look up what each action means, and how long events are kept, in [Audit events](../reference/audit-events.md).
