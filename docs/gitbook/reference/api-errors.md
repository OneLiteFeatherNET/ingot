# API errors

Every error answer under `/api` is a JSON object that follows RFC 9457 (Problem Details) and keeps the members that Reposilite clients have always read.

| Member | Meaning |
| --- | --- |
| `status` | The HTTP status code. |
| `message` | A human readable text. Existing clients read this member. |
| `type` | A URI that identifies the kind of problem. It is `about:blank` when the status code says it all. |
| `title` | The reason phrase of the status code. |
| `detail` | The same text as `message`. |
| `instance` | The path of the request. |
| `problems` | Only when a list was refused: one entry per problem with its `node` and `message`. |

```json
{
  "status": 404,
  "message": "No such thing",
  "type": "about:blank",
  "title": "Not Found",
  "detail": "No such thing",
  "instance": "/api/does-not-exist"
}
```

## Content type

The content type is `application/json`, as before. A client that asks for `application/problem+json` in the `Accept` header, and ranks it at least as high as `application/json`, gets that content type with the same body. A wildcard such as `*/*` keeps `application/json`.

## Checksum file refused

A deploy of a checksum file (`.md5`, `.sha1`, `.sha256` or `.sha512`) answers `400` when the repository has the `reject` [upload checksum policy](repository-settings.md#upload-checksum-policy) and the file does not match the artifact stored before it. The message names the algorithm, and `type` is `urn:ingot:problem:upload-checksum-mismatch`, which tells this refusal apart from other `400` answers. Nothing is stored for the checksum file, and the artifact stays as it is.

```json
{
  "status": 400,
  "message": "SHA1 checksum file lib-1.0.0.jar.sha1 does not match the stored artifact, so it was refused",
  "type": "urn:ingot:problem:upload-checksum-mismatch",
  "title": "Bad Request",
  "detail": "SHA1 checksum file lib-1.0.0.jar.sha1 does not match the stored artifact, so it was refused",
  "instance": "/releases/com/example/lib/1.0.0/lib-1.0.0.jar.sha1"
}
```

The same check under `warn` stores the file and answers as usual. See [Verify uploaded checksums](../how-to/verify-uploaded-checksums.md).

## Which answers use this shape

* Errors that Ingot and its plugins report through `ErrorResponse`.
* Errors that Javalin raises itself, such as a request it refuses.
* A request to a path under `/api` that no route handles. It answers 404 with this body instead of the HTML page. Paths outside `/api`, including dashboard routes, keep the HTML page.
* An unhandled server error. It answers 500 with a generic message. The exception text and the stack trace are never part of the body; they are kept in the failure list of the console.
* A write refused because the instance is read-only. It answers `503` with a `Retry-After` header of 30 seconds. See [Freeze writes for maintenance](../how-to/freeze-writes-for-maintenance.md).
* A deploy refused by the version policy of its repository. It answers `400`, and the message names the repository, the policy and the version. See [Version policy and read-only](repository-settings.md#version-policy-and-read-only).
* A deploy, delete or POM generation refused because its repository is read-only. It answers `405`, and the message names the repository. The instance-wide read-only mode answers first, with `503`.

Status codes are not changed by this format.
