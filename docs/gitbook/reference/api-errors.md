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

## Which answers use this shape

* Errors that Ingot and its plugins report through `ErrorResponse`.
* Errors that Javalin raises itself, such as a request it refuses.
* A request to a path under `/api` that no route handles. It answers 404 with this body instead of the HTML page. Paths outside `/api`, including dashboard routes, keep the HTML page.
* An unhandled server error. It answers 500 with a generic message. The exception text and the stack trace are never part of the body; they are kept in the failure list of the console.

Status codes are not changed by this format.
