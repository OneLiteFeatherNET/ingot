---
description: Reference for the deploy target of an Ingot server, the values all, server and worker, the three sources it is read from, their precedence, the legacy defaultFrontend switch it replaces, the background jobs switch and the start-up refusals.
---

# Deploy targets

The deploy target names what an Ingot server instance serves. It replaces the boolean `defaultFrontend` for new deployments and keeps that boolean working for existing ones.

| Target | Serves | Does not serve |
| --- | --- | --- |
| `all` | The dashboard, the API and the repositories. This is the default and the behaviour of every instance that sets no target. | Nothing. |
| `server` | The API and the repositories. The settings endpoint and the custom frontend handler stay registered. | The dashboard routes. `GET /browse` and the other dashboard pages are not answered with the dashboard document. |
| `worker` | The status probes and the `/metrics` route when the Prometheus plugin is installed. It runs the singleton background jobs, see [Worker target](#worker-target). | Repositories, the API, the settings, the dashboard and every other route. |

The target is a deploy setting, not a key of the local configuration. It is not part of `configuration.cdn`, and it has no `INGOT_LOCAL_` spelling.

## Sources and precedence

The server takes the target from the first of these sources that is set. A source that is set to an empty value counts as not set.

| Order | Source | Example |
| --- | --- | --- |
| 1 | Command line option `--target` | `--target server` |
| 2 | System property `ingot.target` | `-Dingot.target=server` |
| 3 | Environment variable `INGOT_TARGET` | `INGOT_TARGET=server` |
| 4 | Legacy switch `defaultFrontend`, see below | `defaultFrontend: false` in `configuration.cdn` |

The values are `all`, `server` and `worker`, written in lower case. Any other value is refused, see [Unknown values](#unknown-values). The legacy switch never selects `worker`.

## Legacy switch

When no target source is set, the legacy switch decides. The switch is read from `configuration.cdn`, from `INGOT_LOCAL_DEFAULTFRONTEND`, from its `REPOSILITE_LOCAL_` spelling and from the matching system properties, under the same rules as every other local key (see [Configuration file and startup parameters](configuration-and-startup.md#order-of-precedence)).

| `defaultFrontend` | Effective target when no target source is set |
| --- | --- |
| `false` | `server` |
| `true` or not set | `all` |

The mapping goes one way only. Setting a target never writes `defaultFrontend`, and an existing `configuration.cdn` is not changed by the target.

## Agreement and mismatch

When a target source is set and the legacy switch is `false` while the target is `all`, the target wins. The server logs one warning at start. The warning names `all` and `defaultFrontend=false`, and it names the source of the target.

A target of `server` with `defaultFrontend=false` agrees with the switch and logs nothing. A `defaultFrontend` value of `true` never produces the warning, because `true` is the default and does not say anything about the target.

To remove the warning, set the target to `server` to match the switch, or remove `defaultFrontend=false` and keep the target.

## Worker target

The worker runs the background jobs of a cluster and serves no repository or dashboard traffic. Run it as its own deployment next to the `server` replicas, and point it at the same database, cluster bus and object storage as they use.

On target `worker` the server answers these routes and nothing else:

* `GET /api/status/health`, `GET /api/status/ready` and `GET /api/status/instance`
* `GET /metrics`, when the Prometheus plugin is installed

Every other request answers `404`, and nothing is written to storage. A `GET` or `PUT` of a repository path, `GET /api/settings` and `GET /` all answer `404`.

The worker starts these singleton jobs:

* the scheduled cleanup runs
* the audit retention purge
* the search index rebuild, verify and token sweep schedulers
* the search outbox drain

Each job is claimed before it runs, as on the other targets, so two workers that tick at the same minute run a scheduled job once. The statistics flush, the shared settings reload, the status snapshots and the cluster bus plumbing run on every target, the worker included.

The worker refuses to start without shared state, see [Shared state](#shared-state).

## Background jobs switch

`INGOT_BACKGROUND_JOBS` decides whether a `server` instance starts the singleton jobs listed under [Worker target](#worker-target). The switch is opt-in. When it is unset, or set to `true`, a `server` instance starts those jobs as before, so existing deployments need no change.

| Source | Precedence |
| --- | --- |
| Command line option `--background-jobs` | 1, highest |
| System property `ingot.background-jobs` | 2 |
| Environment variable `INGOT_BACKGROUND_JOBS` | 3 |

A source that is set to an empty value counts as not set. The switch has no `REPOSILITE_` spelling. Its only accepted values are `true` and `false`.

| Target | `INGOT_BACKGROUND_JOBS=false` | `INGOT_BACKGROUND_JOBS=true` or unset |
| --- | --- | --- |
| `all` | Refused at start, see [Refusal lines](#refusal-lines). | The jobs run. |
| `server` | The instance does not start the singleton jobs. | The jobs run. |
| `worker` | Refused at start, see [Refusal lines](#refusal-lines). | The jobs run. |

When the switch is `false` on `server`, the instance does not schedule the cleanup runs, the audit retention purge, the search index rebuild, verify and token sweep, or the search outbox drain. The statistics flush keeps running. Those jobs then run only on a worker, so at least one worker must run in the cluster. A `server` with the switch off also needs shared state, see [Shared state](#shared-state).

## Shared state

The worker, and a `server` with `INGOT_BACKGROUND_JOBS=false`, refuse to start unless the instance has shared state. Each of these is an unmet requirement:

* the database is embedded SQLite, which is the default database
* no cluster bus is configured, see `INGOT_CLUSTER_URL` in [Environment variables](environment-variables.md#cluster-bus-variables)
* a repository is stored on the local file system, unless `INGOT_ALLOW_SHARED_FILESYSTEM` is `true`, see [Environment variables](environment-variables.md#deploy-target)

The check runs before the web server binds its port. It does not run on target `all`, nor on `server` with the default switch, so existing installs start as before.

The refusal is one line with exit code 1. It starts with the subject of the check and names every unmet requirement:

```
Ingot did not start: target worker needs shared state, but ...
Ingot did not start: target server with INGOT_BACKGROUND_JOBS=false needs shared state, but ...
```

The requirements are named with these phrases: `the database is embedded sqlite`, `no cluster bus is configured` and `is stored on the local file system`. When a repository is named, its id is quoted before the last phrase.

## Refusal lines

Each refusal ends the start and prints one line.

Unknown deploy target. The line names the source and lists the valid values:

```
Unknown deploy target 'reader' from INGOT_TARGET. Valid values: all, server, worker.
```

Invalid background jobs value. The line names the source and lists the valid values:

```
Invalid background jobs value 'yes' from INGOT_BACKGROUND_JOBS. Valid values: true, false.
```

Contradictory setting. `INGOT_BACKGROUND_JOBS=false` on target `all` or `worker`:

```
Ingot did not start: INGOT_BACKGROUND_JOBS=false is only valid on target server.
```

Shared state. The two lines are shown in [Shared state](#shared-state).

## Unknown values

The server stops at start when a target source carries a value other than `all`, `server` or `worker`. This happens before the port is bound, and the message names the source and lists the valid values:

```
Unknown deploy target 'reader' from INGOT_TARGET. Valid values: all, server, worker.
```

The server does not fall back to another target. A misspelled `server` must not publish the dashboard on an address that was meant for the API only.

## Examples

Start an API-only instance from the server image. Replace `<version>` with the release you run.

```sh
docker run -d -p 8080:8080 -e INGOT_TARGET=server ghcr.io/onelitefeathernet/ingot:<version>
```

The same instance with the legacy switch needs no new variable. It keeps working unchanged:

```sh
docker run -d -p 8080:8080 -e INGOT_LOCAL_DEFAULTFRONTEND=false ghcr.io/onelitefeathernet/ingot:<version>
```

An existing deployment that sets neither needs no action. Its effective target is `all`, and the background jobs switch is unset, so its jobs run as before.

## Related pages

* [Environment variables](environment-variables.md#deploy-target)
* [Configuration file and startup parameters](configuration-and-startup.md#command-line-parameters)
* [Run the dashboard in a separate container](../../set-up/deploy/run-split-dashboard-container.md)
