---
description: Reference for the repository types of Ingot. Lists the hosted, proxy and group types, the rules a settings update enforces, what an untyped repository does, the derived types the API reports, and the error codes.
---

# Repository types

A repository has one of three declared types, or none. The type says what a repository is for, and Ingot enforces it: a typed repository answers with an error when a request or a setting breaks its rule.

| Type | Stores | Proxies | Takes uploads |
| --- | --- | --- | --- |
| `HOSTED` | Uploaded artifacts | Nothing | Yes |
| `PROXY` | Copies of what it fetches from remote URLs | External URLs only | No |
| `GROUP` | Nothing of its own | Local repositories only | No |

A repository without a declared type keeps the behaviour of earlier versions. It accepts uploads and proxies whatever is listed under its mirrored repositories. Existing configurations need no change.

## Declare a type

The type is the optional `type` field of a repository in the maven settings. Its values are `HOSTED`, `PROXY` and `GROUP`. Leave the field out for an untyped repository. Ingot writes the field only when it is set, so an untyped repository keeps the same settings document.

```json
{
  "repositories": [
    { "id": "releases", "type": "HOSTED" },
    { "id": "central", "type": "PROXY", "proxied": [ { "reference": "https://repo.maven.apache.org/maven2", "store": true } ] },
    { "id": "all", "type": "GROUP", "proxied": [ { "reference": "releases" }, { "reference": "central" } ] }
  ]
}
```

## Rules

Ingot checks the rules when settings are saved. A rejected update is not stored, and the response names every rule it breaks. A `proxied` entry is a *reference*: a local reference is the id of another repository of the instance, and an external reference is a URL.

| Type | Rule | Violation message starts with |
| --- | --- | --- |
| `HOSTED` | `proxied` is empty. | `repository '<id>': type HOSTED takes no proxied repositories` |
| `PROXY` | `proxied` names at least one external URL. | `repository '<id>': type PROXY needs at least one proxied external URL` |
| `PROXY` | No reference is empty. | `repository '<id>': type PROXY has an empty proxied reference` |
| `PROXY` | No reference names a local repository. | `repository '<id>': type PROXY proxies external URLs only` |
| `GROUP` | `proxied` names at least one local repository. | `repository '<id>': type GROUP needs at least one proxied local repository` |
| `GROUP` | No reference is empty. | `repository '<id>': type GROUP has an empty proxied reference` |
| `GROUP` | Every reference names an existing local repository. | `repository '<id>': type GROUP proxies local repositories only` |
| `GROUP` | No reference names the group itself. | `repository '<id>': type GROUP cannot proxy itself` |
| `GROUP` | No member sets `store`, since a group stores nothing. | `repository '<id>': type GROUP stores nothing` |
| `GROUP` | No loop of references leads back to the group. | `repository '<id>': type GROUP is part of a cycle of proxied repositories` |

A loop can run through untyped repositories as well. A loop made of untyped repositories alone is not reported, because it is not a typed repository.

## Untyped repositories

An untyped repository is not checked against any rule above. The rules of the other settings still apply to it. Blank entries in `proxied` are ignored, so a group that lists an empty entry between its local references, as some earlier installations do, stays valid and untyped.

## Derived type

The API reports an effective type for every repository. A declared type is reported as it is declared. An untyped repository is derived from its proxied references, blank entries excluded:

| Proxied references | Derived type |
| --- | --- |
| None | `hosted` |
| Local repositories only | `group` |
| External URLs only | `proxy` |
| Both local and external | `mixed` |

The repository list at `GET /api/maven/repositories` carries two fields for every repository:

| Field | Value |
| --- | --- |
| `type` | `hosted`, `proxy`, `group` or `mixed`, in lowercase |
| `typeExplicit` | `true` when the type is declared, `false` when it is derived |

The members endpoint `GET /api/maven/repositories/{repository}/members` lists the local repositories of a group and of a mixed repository. For any other repository the list is empty.

## Enforcement

A typed `PROXY` or `GROUP` does not accept an upload. The check comes after authentication, so an anonymous request still answers `401`.

| Request | On a `PROXY` or `GROUP` | On a `HOSTED` or an untyped repository |
| --- | --- | --- |
| Deploy (`PUT` or `POST` of an artifact) | `405` | Unchanged |
| Delete (`DELETE` of a file) | `405` | Unchanged |
| Generate a POM (`POST /api/maven/generate/pom/...`) | `405` | Unchanged |

The `405` response names the repository and its type:

```text
Repository 'central' is a proxy and does not accept uploads
Repository 'all' is a group and does not accept uploads
```

A `PROXY` still stores what it fetches from its upstream, so caching keeps working. A `GROUP` never writes to storage; it answers from its members. Cleanup policies still remove mirror copies from a proxy, because cleanup is maintenance and not a request that the routes above refuse.

## Storage usage

The storage usage figures report a `group` without a quota, since a group holds no content of its own. Every other repository is measured with the quota it is configured with. A `mixed` repository holds content of its own, so it is measured with its quota. Earlier versions reported no quota for a mixed repository.

## Error codes

| Status | When |
| --- | --- |
| `400` | A settings update breaks a rule. The message is `Invalid maven settings: ` followed by the rules that are broken, separated by `; `. |
| `401` | A request for a write or a delete has no valid credentials. |
| `405` | An upload, a deletion or a POM generation targets a `PROXY` or a `GROUP`. |

## Related

* [Create a group repository](../how-to/create-a-group-repository.md) walks through a group from its settings to a first request.
