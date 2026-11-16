# Design

## Decision

The maintainer decided that the canonical path is always enforced. There is no switch and no `warn` or `off` mode. The protection cannot be turned off, so no installation runs with an authorization decision that differs from the stored path. No released version is affected, because the rules that the raw path could miss (denials through permission nodes) arrived after the last release. The pull request title therefore has no `!`.

## D1. The canonical path

The canonicalizer is a pure function from the raw request path to either a canonical `/repository/location` string or a refusal. It runs in this order:

1. Refuse NUL and ISO control characters (400). Refuse an encoded slash (`%2F`) anywhere in the raw path (400).
2. Percent-decode exactly once, keeping a literal `+` as the router does for path parameters. A malformed escape is refused (400).
3. Refuse a decoded control character or backslash (400). Refuse a decoded segment that is `.` or `..` (400).
4. Collapse repeated slashes and trim the leading and trailing slash.
5. Split the first segment as the repository and the rest as the location, then pass the location to `Location.of`, which stays the final gate for storage.

The canonical string is what the decision and the legacy route rule see. The same repository and location are handed to the storage operation, so the path that is judged is the path that is written.

## D2. Where it applies

| Request | Decision |
|---|---|
| Upload (PUT, POST) | canonical path, refusals answered with 400 before authorization |
| Delete | canonical path, refusals answered with 400 before authorization |
| Read and metadata | the decoded location, unchanged |

Authentication still runs first for a well formed path, so a missing or invalid credential is answered as before. A path without escapes, dot segments or doubled slashes has a canonical form equal to its raw form and is judged and stored exactly as before.

## D3. Why there is no switch

The earlier draft proposed `INGOT_AUTHORIZATION_PATHS` with `warn` as the default to protect the drop-in rule. Verification showed that a deny rule could be judged against a different spelling of the path than the one stored. A default that keeps the raw decision would leave that open, and a switch would let it be reopened. The visible changes are small and only reach paths with escapes or doubled slashes: an escaped spelling of an allowed path is now accepted and stored at its decoded location instead of answered with 403, and ambiguous encodings are answered with 400.

## D4. Verification of the premise

- Javalin decodes path parameters once before the route receives `gav`, keeping `+`.
- An escaped spelling could reach storage at a location that a deny rule covers, because the check saw the raw spelling. Legacy allow-prefix routes could not be left through escapes, because `Location.of` refuses dot segments.

## D5. Tests

- Unit tests for the canonicalizer as a table of inputs and outputs, one behaviour per test.
- In-process HTTP tests that send raw URIs, for escaped characters, escaped repository names, doubled slashes, escaped dot segments, escaped slashes, backslashes and a `+` in a version, each on a fresh instance with a temporary repository directory.
