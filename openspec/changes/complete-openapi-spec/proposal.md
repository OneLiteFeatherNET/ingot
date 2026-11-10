# Proposal

## Why

A team that wants to write a client, a script or a Terraform module against Ingot opens `/openapi` and expects every REST
endpoint to be there. It is not. The document is assembled from `@OpenApi` annotations, and an endpoint without an annotation
is simply absent: no error, no warning. The HTTP API reference says as much ("some endpoints have no OpenAPI annotation, so this
page is the complete list"), which means the machine-readable document cannot be trusted and nobody can tell what is missing
without reading the code.

Two further things make it worse. A new endpoint can be added without an annotation and nothing notices. And the two reference
pages disagree on whether `/openapi` needs a token: `web-routes.md` says "None added by Ingot", `http-api.md` says it was not
checked. An operator who exposes the path through a gateway, or a client author who wants to fetch it, cannot rely on either.

The fix is to annotate what is missing, to add a test that fails when a registered route has no annotation and is not on a short
written exclusion list, and to settle the access question by checking it against a running server and writing the answer down.

## What Changes

- **Annotate the missing REST routes.** Verified on this worktree (see the design): `GET /api/maven/details/{repository}`
  has no annotation of its own, so the document lists the repository-list and the file-path forms but not the middle one.
  Any further REST route the test below finds is annotated in the same way. The annotations describe what the routes do today;
  no handler, status code or response shape changes.
- **Completeness test.** A fast unit test enumerates every `ReposiliteRoute` registered by the backend and fails when a route
  has neither a matching `@OpenApi` entry nor an entry on an explicit exclusion list kept next to the test. Each exclusion carries
  a reason. The list starts with the dashboard assets, the `/javadoc` viewer pages, the `HEAD` form of Maven reads, the plugin
  metrics path and the internal `before` handlers, which are not part of the REST API.
- **Describe security.** The document declares the two credential forms the API accepts (HTTP Basic with a token, and the session
  bearer) as security schemes, and the endpoints that need a login reference them, so generated clients know to send them.
  This is documentation of existing behaviour only.
- **Settle `/openapi` access.** The behaviour is checked in an integration test against the in-process server, with and without
  credentials, and `docs/gitbook/reference/web-routes.md` and `docs/gitbook/reference/http-api.md` are made to agree and to drop
  the "not verified" hints. If the check shows the document is public, the docs say so and say how to restrict it at the edge.
  Access is not changed in this change.
- **Documentation.** `http-api.md` no longer says that some endpoints have no annotation and instead names the exclusion list;
  `web-routes.md` states the verified access and the `/swagger` caveat.

### Out of scope

- The compatibility gate and the release artifact. Those are `add-openapi-diff-gate`.
- New endpoints (component and asset API, repository CRUD, tasks API). Each of those annotates its own routes, and the
  completeness test now enforces it.
- Any change to who may read `/openapi`. A switch to protect it would be a separate, opt-in change.
- Documenting WebSocket and server-sent-event routes beyond what the annotations already say. They keep their current entries.
- Response schema rework or renamed fields.
- Legal and business model questions.

## Capabilities

### New Capabilities

- `openapi-document`: what the served OpenAPI document must contain, how completeness is enforced, and what the documentation
  says about access.

## Impact

- **Type and scope.** The change ships under `fix(api)`: the served document was incomplete. Test, fix and docs commits all use
  the scope `api`. The pull request title, squash-merged onto main, is:

  `fix(api): complete the served openapi document`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. The
  document gains entries and security schemes; no route, status or body changes. The title carries no `!` and no commit carries
  a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** The content of `/openapi` grows. Nothing else.
- **Opt-in.** Not applicable; no new setting.
- **Dependencies on other changes.** None required. `add-openapi-diff-gate` is better merged after this change, so that its
  first snapshot is the complete document, but it does not need it to build.
- **Code.** `reposilite-backend` (annotations in the endpoint classes, one test), `docs/gitbook/reference/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate complete-openapi-spec --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/complete-openapi-spec` prints nothing.
