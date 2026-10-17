---
description: Use the Ingot access check to see whether a user or machine account may use a permission node or an artifact path, optionally through one of its tokens, and which rule decided. Covers the dashboard form and the GET /api/identity/check endpoint.
---

# Check what a principal can do

The access check answers one question: may this principal do this, and which rule decided? It changes nothing. Use it after you change a team, before a build depends on a new rule, and when a request is refused and you need to know why.

The check has two modes:

* **Node mode** decides one permission node, such as `ingot.repository.releases.deploy`.
* **Artifact path mode** decides one path inside a repository, such as `net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom`, for one action. It answers the way a real request for that path answers.

## Prerequisites

* A signed-in principal. You can check yourself. To check another principal, you need the instance admin role.
* For a token check, the name of the token. A token can only narrow what its principal may do, so the check with a token shows the effect of its node list.

## Steps in the dashboard

1. Open **Access**, then the **Check** tab. The page is also at `/access/check`.
2. In **Principal**, enter the name of a user or machine account. The field suggests names.
3. Under **Check by**, choose **Node** or **Artifact path**.
4. For **Node**, enter the node, for example `ingot.repository.releases.deploy`. For **Artifact path**, enter the **Repository**, the **Path** inside it and the **Action**, which is **Read**, **Deploy** or **Delete**.
5. Optionally choose a **Token** of the principal. The answer then includes the narrowing of that token.
6. Select **Check**.

The page shows the verdict, the rule that decided, and the steps that led there. The address of the page holds the question, so you can link to a check.

## Steps with the API

The endpoint is `GET /api/identity/check`. Node mode needs `principal` and `node`:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=ci-deploy&node=ingot.repository.releases.deploy&token=github-releases"
```

Artifact path mode needs `principal`, `repository`, `path` and `action`. The `node` parameter is optional here. If you give a node as well, its action must equal `action`:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=ci-deploy&repository=releases&path=net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom&action=deploy&token=github-releases"
```

### Parameters

| Parameter | Mode | Meaning |
| --- | --- | --- |
| `principal` | Both | The user or machine account to check. Required. |
| `node` | Node mode | A concrete node. Wildcards such as `*` are refused. |
| `repository` | Path mode | The repository the path is in. A name, without wildcards. |
| `path` | Path mode | The path inside the repository, without the repository name. |
| `action` | Path mode | `read`, `deploy` or `delete`. Can be left out when a `node` gives the action. |
| `token` | Both | Optional. The name of one token of the principal. Narrows the answer. |

### Reading the answer

| Field | Meaning |
| --- | --- |
| `allowed` | The verdict. `true` means the request would be allowed. |
| `decision.decidingNode` | The text of the node that decided. Empty when no node matched. |
| `decision.decidingKind` | The kind of the deciding rule: `ARTIFACT`, `REPOSITORY`, `ADMIN`, `EVERYTHING`, `GRANT` for a role grant, or `INSTANCE_ADMIN` when an instance admin passes. Empty when no rule matched. |
| `decision.source` | Where the rule came from: `USER`, `TEAM`, `INHERITED_TEAM`, `LEGACY`, `TOKEN`, or `DEFAULT` when nothing matched. |
| `decision.subject` | The owner of the rule, such as the team name. |
| `decision.explanation` | The verdict in one sentence, such as `Denied by the artifact node '...' of alice (USER)`. |
| `trace` | Every rule and grant that was considered. Each entry has `matched`, and a `reason` that says why it did or did not decide. |

The answer also repeats `principal`, `node`, `repository`, `path` and `action`, as the question named them.

## Rules of the answer

* **Repository nodes are decided as a request for the repository.** A node such as `ingot.repository.releases.read` is answered as a request for the root of `releases`.
* **Artifact nodes are decided by their own rules.** An unqualified node such as `ingot.artifact.net.onelitefeather:mycelium-bom.deploy` asks about that artifact in every repository. The qualified form asks about one repository.
* **A token narrows the answer.** When the token denies the request, the decision source is `TOKEN`. A token can never allow what its owner may not do.
* **An instance admin passes denied requests.** For a repository node or an artifact path, a denial does not restrict an instance admin. Such a check answers `allowed: true` with `decidingKind` set to `INSTANCE_ADMIN`. The node list of a token still narrows an admin. An artifact node in node mode is decided by its own rules only.

## Errors

| Status | Cause |
| --- | --- |
| `400 Bad Request` | A parameter is missing or invalid, for example a wildcard in `node`, a path check without `repository`, or an unknown action. |
| `403 Forbidden` | You asked about another principal without the instance admin role. |
| `404 Not Found` | The principal or the token does not exist. |

## Result

A check that you expect to allow returns `"allowed": true`, and one that you expect to deny returns `"allowed": false`. The `decision.explanation` names the node that decided, so you can fix the rule and check again.

To list every principal that has access to a repository, with the reason for each, use `GET /api/identity/repositories/<repository>/access`. That call needs the instance admin role.

## Related

* [Grant permissions with nodes](grant-permissions-with-nodes.md) shows recipes whose checks are on this page.
* [Restrict a token to artifacts](restrict-token-to-artifacts.md) shows how a token check reveals the narrowing.
* [Permission nodes](../reference/permission-nodes.md) lists the node grammar.
