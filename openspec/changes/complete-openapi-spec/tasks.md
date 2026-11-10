# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `api`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (any
time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, and
assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. Integration
tests run as `:reposilite-backend:integration --tests '<pattern>'`, and unit tests as
`:reposilite-backend:test --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose complete-openapi-spec`: this change and nothing else. Verify:
  `openspec validate complete-openapi-spec --strict` passes and the dash grep from the proposal prints nothing.

## 2. Completeness test

- [ ] 2.1 `test(api)`: write the failing completeness test with the exclusion list and a reason per entry. Verify: it fails and
  names `GET /api/maven/details/{repository}` and any further route it finds. Record the full list in the pull request.
- [ ] 2.2 `test(api)`: write a failing test that the document declares both security schemes. Verify: it fails before 3.2.

## 3. Annotations

- [ ] 3.1 `fix(api)`: annotate the routes reported by 2.1 with the status codes and bodies the handlers already return. Verify:
  the test of 2.1 passes and the existing endpoint tests pass unchanged.
- [ ] 3.2 `fix(api)`: declare the security schemes in `configureOpenApi` and reference them from the annotations of routes that
  need a login. Verify: the test of 2.2 passes.

## 4. Access to the document

- [ ] 4.1 `test(api)`: write an integration test that requests `/openapi` without credentials and with a token, and asserts the
  status the server returns. Verify: it runs, and its result is the statement used in 4.2.
- [ ] 4.2 `docs(api)`: make `docs/gitbook/reference/web-routes.md` and `docs/gitbook/reference/http-api.md` agree on that
  statement, remove the "not verified" hints about `/openapi`, and replace the "some endpoints have no annotation" sentence with a
  pointer to the exclusion list. If the document is public, say how to restrict it at the edge.

## 5. Pull request

- [ ] 5.1 Open the pull request with the title `fix(api): complete the served openapi document`. The description follows
  `.github/pull_request_template.md`, carries no session link, and states that there is no breaking change.
