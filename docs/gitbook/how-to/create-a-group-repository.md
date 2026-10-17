---
description: Create a group repository in Ingot that serves several local repositories under one URL, from the settings to a first request, with the checks that catch a wrong reference.
---

# Create a group repository

A group repository serves the artifacts of several local repositories under one address. Build tools then need one repository URL instead of one per repository. A group stores nothing of its own, so uploads go to the repository that holds them.

## Before you begin

* Know which local repositories the group should serve, for example `releases` and `snapshots`. Each one must already exist in the maven settings.
* Be a manager of the instance, so that you can change the maven settings.
* Read the [repository types reference](../reference/repository-types.md) once. It lists the rules that the steps below satisfy.

## Choose the members

List the local repositories in the order a request should search them. A request answers from the first member that holds the file. Members must be local, so do not add a URL here.

## Declare the group

Add a repository with `type` set to `GROUP`, and list its members under `proxied` with their ids:

```json
{
  "id": "all",
  "type": "GROUP",
  "visibility": "PUBLIC",
  "proxied": [
    { "reference": "releases" },
    { "reference": "snapshots" }
  ]
}
```

Leave `store` out of every member. A group stores nothing, so Ingot refuses a member that sets it.

Save the settings in the dashboard, or send the whole maven domain to `PUT /api/settings/domain/maven`. A saved group appears in the repository list with `"type": "group"` and `"typeExplicit": true`.

## Check the members

Ask the group for its members:

```text
GET /api/maven/repositories/all/members
```

The answer lists `releases` and `snapshots`, in the order you set. A member that is not visible to the caller is left out, as in the repository list.

## Use the group

Point the build at the group URL, for example:

```xml
<repository>
  <id>all</id>
  <url>https://repo.example.org/all</url>
</repository>
```

Deploy to a member, not to the group. A deployment to the group answers `405` with `Repository 'all' is a group and does not accept uploads`:

```bash
curl -u deployer:secret -X PUT --data-binary @app-1.0.jar \
  https://repo.example.org/releases/com/example/app/1.0/app-1.0.jar
```

## When the save is refused

A `400` response means that a rule is broken, and nothing was stored. The message names the repository and the rule. Common causes:

| Message ends with | Fix |
| --- | --- |
| `proxies local repositories only, but 'https://...' is not a repository of this instance` | Remove the URL from the group, or move it into its own `PROXY` repository and list that repository instead. |
| `but 'ghost' is not a repository of this instance` | Check the spelling of the id, or create the repository first. |
| `cannot proxy itself` | Remove the group's own id from its `proxied` list. |
| `stores nothing` | Remove `store` from the member. |
| `is part of a cycle of proxied repositories (all -> team -> all)` | Break the loop, for example by removing a group from another group's members. |
| `has an empty proxied reference` | Remove the entry with the empty reference. |

## Verify the result

1. Request a file that only a member holds, through the group URL. The group answers with it.
2. Check the repository list. The group shows `"type": "group"` and `"typeExplicit": true`.
3. Check the storage usage. The group is listed without a quota, since it stores nothing.
