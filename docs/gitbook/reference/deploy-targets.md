---
description: Reference for the deploy target of an Ingot server, the values all and server, the three sources it is read from, their precedence, the legacy defaultFrontend switch it replaces and the start-up refusal of unknown values.
---

# Deploy targets

The deploy target names what an Ingot server instance serves. It replaces the boolean `defaultFrontend` for new deployments and keeps that boolean working for existing ones.

| Target | Serves | Does not serve |
| --- | --- | --- |
| `all` | The dashboard, the API and the repositories. This is the default and the behaviour of every instance that sets no target. | Nothing. |
| `server` | The API and the repositories. The settings endpoint and the custom frontend handler stay registered. | The dashboard routes. `GET /browse` and the other dashboard pages are not answered with the dashboard document. |

The target is a deploy setting, not a key of the local configuration. It is not part of `configuration.cdn`, and it has no `INGOT_LOCAL_` spelling.

## Sources and precedence

The server takes the target from the first of these sources that is set. A source that is set to an empty value counts as not set.

| Order | Source | Example |
| --- | --- | --- |
| 1 | Command line option `--target` | `--target server` |
| 2 | System property `ingot.target` | `-Dingot.target=server` |
| 3 | Environment variable `INGOT_TARGET` | `INGOT_TARGET=server` |
| 4 | Legacy switch `defaultFrontend`, see below | `defaultFrontend: false` in `configuration.cdn` |

The values are `all` and `server`, written in lower case. Any other value is refused, see [Unknown values](#unknown-values).

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

## Unknown values

The server stops at start when a target source carries a value other than `all` or `server`. This happens before the port is bound, and the message names the source and lists the valid values:

```
Unknown deploy target 'sever' from INGOT_TARGET. Valid values: all, server.
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

An existing deployment that sets neither needs no action. Its effective target is `all`.

## Related pages

* [Environment variables](environment-variables.md#deploy-target)
* [Configuration file and startup parameters](configuration-and-startup.md#command-line-parameters)
* [Run the dashboard in a separate container](../how-to/run-split-dashboard-container.md)
