---
description: Reference for the cleanup policies of Ingot, with every policy field, scope, schedule rule, the exact keep and delete rules, run records and the cleanup API.
---

# Cleanup policies

A cleanup policy deletes old versions of artifacts from the repositories it lists. Policies are stored in the `cleanup` settings domain, in its `policies` list. The list is empty by default. Ingot deletes nothing until a policy is run by hand, or is enabled and has a schedule.

To set up a policy step by step, follow [Set cleanup policies](../how-to/set-cleanup-policies.md).

## Policy fields

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `id` | Id | string | empty | Unique id of the policy. 1 to 64 characters: letters, digits, dots, hyphens and underscores, starting with a letter or digit. Ids may not repeat. |
| `enabled` | Enabled | boolean | `false` | Only an enabled policy with a schedule runs on its own. Manual runs and previews work either way. |
| `repositories` | Repositories | list of repository ids | empty | The repositories the policy acts on. An empty list means the policy acts on nothing. |
| `scope` | Scope | `SNAPSHOTS`, `RELEASES`, `MIRROR_CACHE` | `RELEASES` | Which versions the policy may touch. See [Scopes](#scopes). |
| `keepLatest` | Keep the newest versions | integer, at least 1, or absent | absent | The newest N versions of each artifact within the scope are never deleted. |
| `olderThanDays` | Older than (days) | integer, at least 1, or absent | absent | Deletes only versions that were last modified more than this many days ago. |
| `notDownloadedForDays` | Not downloaded for (days) | integer, at least 1, or absent | absent | Deletes only versions that were not downloaded for this many days. See [Age criteria](#age-criteria). |
| `groupFilter` | Group filter (optional) | regular expression or absent | absent | A Java regular expression that has to match the whole artifact path, for example `net/onelitefeather/.*`. Empty or absent matches every artifact. |
| `schedule` | When to run, Time (HH:mm) | `manual` or `HH:mm` | `manual` | When an enabled policy runs on its own. See [Schedule](#schedule). |

The dashboard form pre-selects the scope **Snapshots** for a new policy. The default of the setting itself, which applies when a policy in the settings has no `scope`, is `RELEASES`.

At least one of `keepLatest`, `olderThanDays` and `notDownloadedForDays` has to be set. Ingot refuses a policy without one, because it would delete every version.

A settings update that breaks a rule is not stored. It answers `400` with a message that starts with `Invalid cleanup settings: `. The rules are:

* The id matches the pattern above and is used once.
* `keepLatest`, `olderThanDays` and `notDownloadedForDays` are at least 1 when set.
* At least one of the three is set.
* `groupFilter` is a valid regular expression.
* `schedule` is `manual` or a time between `00:00` and `23:59`.

A policy may name a repository that does not exist. It is not an error in the settings, and a preview reports it as a warning.

## Scopes

| Scope | Versions the policy may touch |
| --- | --- |
| `SNAPSHOTS` | Versions whose name ends with `-SNAPSHOT`. A snapshot version that is newer than the newest release of the same artifact is never deleted. This rule applies only when the artifact has at least one release. |
| `RELEASES` | Every version that is not a snapshot. A deleted release cannot be restored. |
| `MIRROR_CACHE` | Copies that a proxied entry with `store` saved in a repository, and only copies that Ingot recorded. Copies cached before Ingot recorded them are never deleted, and a directory that holds uploaded artifacts is never a copy. |

## Versions

A policy deletes whole version directories. Ingot reads a repository as follows:

* A version is a directory that holds a `.pom` file directly and has a parent directory. The parent directory is the artifact, for example `net/onelitefeather/app`, and the directory name is the version.
* A directory below a version belongs to that version.
* The size of a version is the sum of its files. The modification time of a version is the newest modification time of its files.
* A directory without a `.pom` file is not a version, and a policy never deletes it.

The timestamped builds inside one `-SNAPSHOT` directory are not separate versions. The repository setting `preserveSnapshots` controls them, see [Repository settings](repository-settings.md#preserved-snapshots).

Versions are ordered by version number, newest first, not by upload time.

## Rules

For each run, a policy plans its deletions in this order:

1. Ingot takes the versions of the repositories in `repositories`, and keeps the artifacts whose path matches `groupFilter`.
2. It groups the versions by repository and artifact, and works on each group on its own.
3. It keeps the versions that are in the scope as candidates.
4. It protects the candidates that a keep rule names:
    * The newest `keepLatest` candidates.
    * With the scope `SNAPSHOTS`, the snapshots that are newer than the newest release of the artifact.
5. Of the other candidates, it plans to delete those that match every age criterion that is set.
6. If this plan would delete every version of the artifact, it keeps the newest version of the artifact. At least one version of every artifact survives a run.

The plan is sorted by repository and path.

### Age criteria

| Criterion | A version matches when |
| --- | --- |
| `olderThanDays` | Its modification time is earlier than now minus the number of days. |
| `notDownloadedForDays` | Its last download is earlier than now minus the number of days. |

Ingot records downloads only for mirror copies. For every other version, and for copies without a record, the modification time stands in for the last download.

When both criteria are set, a version has to match both. When only `keepLatest` is set, every candidate outside the newest N is deleted. The reason that a preview lists for a version names the rules that selected it, for example `outside the newest 3 versions; older than 30 days`.

### Examples

| Settings | Effect |
| --- | --- |
| `keepLatest: 3` | Deletes every version except the newest 3. |
| `olderThanDays: 30` | Deletes every version older than 30 days, except the newest version of an artifact when it would be the last one. |
| `keepLatest: 3`, `olderThanDays: 30` | Deletes the versions outside the newest 3 that are also older than 30 days. |
| `scope: MIRROR_CACHE`, `notDownloadedForDays: 14` | Deletes cached copies that nobody downloaded for 14 days. They are fetched again on the next request. |

## Schedule

`manual` means the policy runs only when you start it. A time such as `04:30` means once a day at that time, in the time zone of the server.

* A scheduler checks the policies every 30 seconds. A policy starts when the clock of the server shows the scheduled hour and minute and the policy is enabled.
* An instance that is not running at that minute does not start the run for that day. Ingot does not catch up later.
* Instances that share a database claim a run with the key `cleanup:<id>:<date>`, where the date is the UTC date. Only one instance runs a policy per UTC day.

## Runs

A run deletes the planned versions through the repository delete service. The service updates `maven-metadata.xml` and the search index.

| Aspect | Behaviour |
| --- | --- |
| Manual run | Uses the delete permission of the account that starts it. |
| Scheduled run | Has no account and is not checked against a token. |
| Parallel runs | A policy that is already running on the instance answers `409`. |
| History | Each policy keeps its last 20 runs. |
| Audit log | A run records one `CLEANUP_RUN` event with the run id, policy, scope, trigger, status and counts. The single deletions of a run are not recorded as `ARTIFACT_DELETED` events. |
| Log | A run writes a `CLEANUP` line to the server log. |

Fields of a run record:

| Field | Meaning |
| --- | --- |
| `id` | Run id. |
| `policyId` | The policy. |
| `trigger` | `MANUAL` or `SCHEDULED`. |
| `startedAt`, `finishedAt` | ISO-8601 instants. |
| `status` | `SUCCEEDED`, `PARTIAL` or `FAILED`. The dashboard shows **Succeeded**, **Partly failed** and **Failed**. |
| `deletedCount` | Number of versions deleted. |
| `deletedBytes` | Total size of the deleted versions. |
| `failures` | One entry per version that could not be deleted: `<repository>/<path>: <message>`. |

The status is `SUCCEEDED` when nothing failed, including a run with nothing to delete. It is `FAILED` when every deletion failed, and `PARTIAL` when some did.

## Preview warnings

A preview returns warnings next to the planned deletions:

| Warning | Cause |
| --- | --- |
| `repository '<id>' does not exist` | The policy names a repository that is not configured. |
| `repository '<id>' stores no mirrored artifacts, so the MIRROR_CACHE scope finds nothing there` | The scope is `MIRROR_CACHE`, and no proxied entry of the repository has `store` turned on. |
| `repository '<id>' has no recorded mirror copies yet, so MIRROR_CACHE finds nothing there. Copies cached before provenance was recorded are never deleted by MIRROR_CACHE` | The scope is `MIRROR_CACHE`, and the repository has no recorded copy. |
| `copies cached before provenance was recorded are never deleted by MIRROR_CACHE, only copies with a recorded mirror store are considered` | The scope is `MIRROR_CACHE`, and the repository has recorded copies. |

## API

The endpoints need an instance admin. The dashboard calls them from the Maintenance page. Policies themselves are read and written with the settings API, `GET` and `PUT /api/settings/domain/cleanup`. A `PUT` replaces the whole list.

| Method and path | Purpose | Answers |
| --- | --- | --- |
| `POST /api/cleanup/policies/{id}/dry-run` | Lists what a run would delete now. Deletes nothing. | `200` with a preview, `403`, `404` for an unknown policy |
| `POST /api/cleanup/policies/{id}/run` | Runs the policy now. | `200` with the run record, `403`, `404`, `409` when the policy is already running |
| `GET /api/cleanup/runs` | Lists the recorded runs, newest first. The query parameter `policy` limits the list to one policy. | `200` with an array of run records, `403` |

A preview has these fields:

| Field | Meaning |
| --- | --- |
| `policyId` | The policy. |
| `evaluatedAt` | When the plan was made. |
| `count` | Number of versions the plan deletes. |
| `totalBytes` | Total size of those versions. |
| `deletions` | A list of `repository`, `path`, `version`, `sizeBytes` and `reason`. |
| `warnings` | The [warnings](#preview-warnings) of the preview. |

A run is not a snapshot of the preview. A run plans again at the moment it starts, so the deletions can differ if files changed in between.

## Related

* [Set cleanup policies](../how-to/set-cleanup-policies.md) walks through creating, previewing and running a policy.
* [Repository settings](repository-settings.md) describes the repository fields that a policy depends on, such as `preserveSnapshots` and the proxied entries with `store`.
* [Repository types](repository-types.md) lists the rules for hosted, proxy and group repositories. Cleanup policies still remove mirror copies from a proxy.
