---
description: Reference for access tokens in Ingot. Covers the secret formats, how credentials are sent, identity and machine account tokens, scopes and nodes as token narrowing, legacy tokens with route grants and their prefix matching, the --token recovery token, and the migration of legacy tokens to machine accounts.
---

# Access tokens and routes

Ingot accepts three kinds of credentials for the HTTP API and for Maven paths.

| Credential | How it is sent | Created by |
| --- | --- | --- |
| Token secret `ingot_<id>_<random>` | `Authorization: Bearer <secret>`, or Basic with the token name | Identity API: `POST /api/identity/me/tokens` and `POST /api/identity/principals/{name}/tokens` |
| Legacy token secret, without a prefix | HTTP Basic with the token name and the secret | Legacy API `PUT /api/tokens/{name}`, console `token-generate`, and the migration |
| Browser session | Cookies set by a login | A login, see [Login providers](login-providers.md) |

The `--token` option of the server creates one more kind of token on every start, see [Recovery token](#recovery-token).

## How credentials are sent

- `Authorization: Bearer ingot_...` authenticates with a token secret. The token id in the secret selects the row, and the whole secret is checked against the stored hash. A Bearer value that is not a token secret of this format fails and counts as a failed attempt.
- `Authorization: Basic <base64 of name:secret>` authenticates with the token name and its secret. The `xBasic` scheme is accepted in the same way. Browsers use it to avoid the built-in password prompt.
- Without an `Authorization` header, a browser session cookie authenticates the request as its user. Without either, the request is anonymous.

A token secret of the identity format also works over Basic, with its token name. Legacy secrets work over Basic only.

A token that is past its `expiresAt` is refused. A disabled principal authenticates to nothing.

## Token secret format

A token secret of the identity format has this shape:

```
ingot_<tokenId>_<random>
```

- `ingot_` is the prefix.
- `<tokenId>` is the decimal id of the token row. It is not a secret.
- `<random>` is 32 random bytes, URL-safe base64 without padding, so 43 characters.

The whole secret stays below 72 bytes, the input limit of bcrypt, which hashes it. Only the hash is stored. A secret is shown once, in the response that creates it or rotates it.

Names of tokens and principals must not be empty after trimming, may be at most 255 characters long, and may not contain `:`.

## Identity tokens

### Creating a token

Each user creates its own tokens, and an instance admin can create a token for any principal.

| Method and path | Who | Purpose |
| --- | --- | --- |
| `POST /api/identity/me/tokens` | The signed-in user | Creates an own token. The body takes `name`, `description`, `expiresAt`, `scopes` and `nodes`. |
| `GET /api/identity/me/tokens` | The signed-in user | Lists own tokens, without secrets. |
| `POST /api/identity/me/tokens/{name}/rotate` | The signed-in user | Replaces the secret. The old secret stops working at once. The node list stays. |
| `DELETE /api/identity/me/tokens/{name}` | The signed-in user | Deletes an own token. |
| `POST /api/identity/principals/{name}/tokens` | Instance admin with the `tokens` area | Creates a token for a principal. The body is the same, and it may also carry a `secret` chosen by the caller. |
| `GET /api/identity/principals/{name}/tokens` | Instance admin with the `tokens` area | Lists the tokens of a principal. |
| `DELETE /api/identity/principals/{name}/tokens/{tokenName}` | Instance admin with the `tokens` area | Deletes a token of a principal. |

There is no endpoint that changes the name, the scopes or the nodes of an identity token after it is created. To change the narrowing, delete the token and create a new one.

### When a change takes effect

Ingot keeps a successful authentication in a cache for up to one minute. On the replica that changes a token, the cached entries of that token are dropped at once. Another replica of the same cluster does not get that signal, so it can keep accepting the old secret for up to one minute after the rotation or deletion. An expired token is refused at once on every replica, because each request checks the expiry.

### Machine accounts

A machine account is a principal of kind `MACHINE`, for a CI job or a bot. It is created with `POST /api/identity/principals` and a body with `"kind": "MACHINE"`. It owns a private team named `machine:<name>` from the start. Its tokens work like the tokens of a user.

```
POST /api/identity/principals
{ "kind": "MACHINE", "name": "ci-deploy", "displayName": "CI deploy" }
```

Then create a token for it with `POST /api/identity/principals/ci-deploy/tokens`, and give the token the nodes it needs.

### Example: a token that deploys one group

```
POST /api/identity/me/tokens
{
  "name": "ci-deploy",
  "description": "Deploys the mycelium artifacts from CI",
  "expiresAt": "2027-01-01T00:00:00Z",
  "nodes": [
    { "node": "ingot.artifact.net.onelitefeather.read", "allowed": true },
    { "node": "ingot.artifact.net.onelitefeather.deploy", "allowed": true }
  ]
}
```

The token can only do what both the owner and the token allow. See [Artifact permission nodes](artifact-permission-nodes.md) for the artifact forms.

## Scopes and nodes narrow a token

A token can carry a list of scopes, a list of nodes, or both. A token without either is not narrowed at all. Both lists must not be empty when they are given.

Scopes are the older coarse form. Each scope becomes one node when the token is created.

| Scope | Node |
| --- | --- |
| `READ` | `ingot.repository.*.read` |
| `WRITE` | `ingot.repository.*.deploy` |
| `DELETE` | `ingot.repository.*.delete` |
| `MANAGE_REPOSITORY` | `ingot.repository.*.manage` |
| `INSTANCE_ADMIN` | `ingot.admin.*` |

Scope names are not case sensitive on input.

Rules for narrowing:

- A token with a node list is narrowed to exactly those nodes. A request is allowed only when the owner and the token both allow it. A node the token does not list is denied, even when the owner may use it.
- A token can only remove rights. A node that the owner lacks gives nothing.
- When `scopes` and `nodes` are both given, the nodes narrow the token. The scopes are stored with the token and do not narrow it.
- Narrowing applies to an instance admin too. Only a token narrowing limits an admin token. See [Permission nodes](permission-nodes.md).

## Legacy tokens

Legacy tokens are the tokens of the legacy API and the legacy console commands. Their secrets have no prefix, so they work over Basic only. They have a name, a secret, optional permissions, optional routes, an optional expiry and a type, `PERSISTENT` or `TEMPORARY`. A persistent legacy token is stored as a machine principal in the identity tables, with the same rights as before. A temporary one is kept in memory.

### Permissions and routes

- The permission `m` makes the token a manager. For the identity model this is the instance admin status, with the node `ingot.admin.*` on the token's private team.
- A route is a path prefix from the repository root, such as `/releases/net`, with one of these permissions:
  - `r` lets the token read below the path (`route:read`).
  - `w` lets the token write and delete below the path (`route:write`).

Routes are matched as prefixes, and that is the intended behaviour. Ingot keeps the upstream matching so that existing tokens keep their rights. Matching is case insensitive and does not stop at a segment boundary. The route `/releases` also covers `/releases-snapshots`. End the route with `/` to match whole segments only: `/releases/` does not match `/releases-snapshots`.

A route whose path is the name of a repository, such as `/releases`, grants the whole repository for the purposes of the repository checks. A route with a deeper path only covers requests below it.

### Legacy API

These endpoints need the instance admin status, except the read of one token. The owner of a token may read that token too.

| Method and path | Purpose |
| --- | --- |
| `GET /api/tokens` | Lists all legacy tokens with their permissions. |
| `GET /api/tokens/{name}` | Reads one token. The owner may read its own. |
| `PUT /api/tokens/{name}` | Creates a token, or replaces it, with a body that carries `permissions`, `routes`, `expiresAt` and `description`. |
| `PATCH /api/tokens/{name}` | Changes the description, the permissions, the routes or the expiry. The secret stays. |
| `POST /api/tokens/{name}/secret` | Regenerates the secret. The old one stops working. |
| `DELETE /api/tokens/{name}` | Deletes the token. |

### Console commands

| Command | Purpose |
| --- | --- |
| `tokens` | Lists all tokens. |
| `token-generate <name> [m]` | Creates a token. The option `--secret` sets the secret, and `--silent` hides it. |
| `token-modify <name> <permissions>` | Replaces the permissions. |
| `token-rename <name> <new name>` | Renames a token. |
| `token-revoke <name>` | Deletes a token. |
| `token-regenerate <name>` | Replaces the secret. Accepts `--secret` and `--silent` as above. |
| `route-add <access_token> <path> <permissions>` | Adds a route. A route with the same path is replaced. A path without a leading `/` gets one. |
| `route-remove <access_token> <path>` | Removes the read and write routes of that path. |
| `token-export` | Exports the tokens to a file in the working directory. |
| `token-import` | Imports tokens from a file in the working directory. |

## Recovery token

`--token name:secret` (short form `-t`) starts the server with a temporary token. The token has the manager permission, so it is an instance admin. The server creates it again on every start while the option is set. It is kept in memory and is not a principal of the identity model.

Use it to get back into an instance whose last usable admin has been lost. The startup warning that reports a removed last admin names this option as the way back in.

{% hint style="warning" %}
A token from `--token` is an instance admin on every start while the option is present. Remove the option once the access is restored.
{% endhint %}

## Migration of legacy tokens

At the first start of Ingot 1.2 or later, the rows of the legacy token table are copied once into machine principals. The legacy tables are only read.

- Each legacy token becomes a machine principal with the same name, the same secret hash and the same expiry.
- A legacy `m` permission becomes the node `ingot.admin.*` on the private team of the new principal.
- Each legacy route becomes a legacy grant on that private team, with the read or write rights of the route.
- A ledger records each row that was copied, so a row is never copied twice. A row written later by an older version is copied at the next start.
- When two legacy names differ only in case, the second principal is named `<name>-<tokenId>`. Its token keeps its exact name, so logins are unchanged.

The full explanation is in [Token migration](../explanation/token-migration.md).
