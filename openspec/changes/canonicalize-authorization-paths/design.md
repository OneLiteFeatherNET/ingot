# Design

## D1. The canonical path

The canonicalizer is a pure function from the raw request path to either a canonical `/repository/location` string or a refusal. It runs in this order:

1. Refuse NUL and ISO control characters (400).
2. Percent-decode exactly once. A malformed escape is refused (400).
3. Refuse a decoded segment that is `.` or `..` (400). Refuse an encoded slash that arrives inside a segment in `enforce` mode (400), see proposal question 4.
4. Collapse repeated slashes and trim the leading and trailing slash.
5. Split the first segment as the repository and the rest as the location, then pass the location to `Location.of`, which stays the final gate for storage.

The canonical string is what the decision, the legacy route rule and the decision log (change `add-decision-log`) see. The raw string is kept for the comparison in `warn` mode and for the log.

## D2. Modes

| Mode | Decision used | Comparison | Refusals before authorization |
|---|---|---|---|
| `off` | raw (today) | none | none |
| `warn` (default) | raw (today) | canonical decision computed; a difference logs WARN and increments a counter | none |
| `enforce` | canonical | none | yes, 400 for malformed paths |

Reads and metadata already use the normalized location. They keep their decision in every mode, and in `enforce` the upload and read paths share the canonical string.

## D3. Why warn is the default

The drop-in rule says that pointing an existing installation at Ingot changes nothing. A decision change for encoded uploads is a visible change, so it is opt-in. The cost is that the upload check stays on the raw path until an operator switches. The proposal names this as question 1, and the breaking variant is the alternative.

## D4. Verification of the premise

Two facts decide how serious the gap is, and both are verify tasks before any behaviour change is merged:

- Whether Javalin decodes path parameters before `Location.ofRequest` receives `gav`. If it does, an encoded `..` reaches `Location.of` decoded, and storage never sees it. If it does not, the decoded form is not checked by the authorizer at all.
- Whether any encoded form can produce a storage path outside the authorized location. Expected answer: no, because `Location.of` refuses the dot segments and the illegal characters. The verify task records the answer either way, so the PR states the real impact.

## D5. Tests

- Unit tests for the canonicalizer as a table of inputs and outputs, one behaviour per test.
- Corpus rows from `record-decision-corpus` for raw and normalized forms, with the `changesIn` rows of this change updated in the same commit.
- A targeted in-process HTTP test for an upload with `%2e%2e` in each mode, on a temporary repository directory, with the clock injected where a timestamp is written.
