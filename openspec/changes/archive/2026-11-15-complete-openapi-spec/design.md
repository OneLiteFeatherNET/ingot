# Design

## How the document is produced today

`JavalinConfiguration.configureOpenApi` registers the Javalin `OpenApiPlugin` and sets only title, description and version
(`reposilite-backend/src/main/kotlin/com/reposilite/web/application/JavalinConfiguration.kt:212-222`, called at line 84). The
document is not derived from the registered routes. The kapt annotation processor
(`reposilite-backend/build.gradle.kts:91-93`, version 7.2.2) collects `@OpenApi` annotations at compile time and the plugin
serves the result at `/openapi`. The optional swagger plugin only adds the UI
(`reposilite-plugins/swagger-plugin/src/main/kotlin/com/reposilite/plugin/swagger/SwaggerPlugin.kt`).

So a route and its annotation are linked by a path string that nobody checks. There are 100 `@OpenApi` annotations in 24 files
and about 93 `ReposiliteRoute` definitions in 24 route classes (counted by script on this worktree).

## Endpoints missing from the document

Comparing the set of `ReposiliteRoute(path, method)` definitions with the set of `@OpenApi(path, methods)` entries, after
treating `<gav>` and `{gav}` as the same segment:

- `GET /api/maven/details/{repository}` is registered (`maven/infrastructure/MavenApiEndpoints.kt:106`) but only the sibling
  routes carry annotations (`:105` for the list, `:107` for the file path). This is the one REST route that is missing.
- `GET /javadoc/{repository}/{gav}` and `GET /javadoc/{repository}/{gav}/raw/{resource}`
  (`javadocs/infrastructure/JavadocEndpoints.kt:36,64`) serve HTML and files to a browser. They are not a REST API and go on the
  exclusion list.
- `HEAD /{repository}/{gav}` (`maven/infrastructure/MavenEndpoints.kt:87`) is registered together with `GET` and the annotation
  lists `GET` only. It goes on the exclusion list as the head form of an annotated read.
- The dashboard handlers (`frontend/infrastructure/FrontendHandler.kt`), the access-log and cache-bypass `before` handlers, the
  redirect plugin and the prometheus metrics path (`reposilite-plugins/prometheus-plugin/.../PrometheusEndpoints.kt:15`, a
  plugin with a configurable path) are not REST routes of the core and go on the exclusion list.
- Routes that the script reported as annotated but not found as `ReposiliteRoute` are the console WebSocket and SSE handlers
  and login, setup and identity endpoints that are annotated and registered through their own classes. They are present in the
  document and stay as they are.

The script is a first pass. The completeness test replaces it with a check that runs on every build, and its result is the
authoritative list; the annotation task adds whatever the test reports.

## Completeness test

The test builds the route list from the endpoint classes, normalises path parameters, and compares it with the annotation index
that kapt writes into the class output (`openapi-plugin/` in the compiled classes). It needs no server, so it runs in
milliseconds and is independent of other tests. The exclusion list is a plain constant in the test with a reason per entry; adding
a route without an annotation fails the build with a message naming the route and the list.

Alternative considered: fail at runtime when the server starts. Rejected: it would change startup behaviour for operators for a
documentation problem.

## Security schemes

The document today declares no security scheme, so generated clients send no credentials. The two forms the API accepts are in
`auth/infrastructure/` and `docs/gitbook/reference/access-tokens-and-routes.md`: HTTP Basic with a token name and secret, and a
session bearer from login. They are added to the definition configuration (`configureOpenApi`) and referenced from the
annotations of routes that need a login. This is additive documentation.

## The `/openapi` access question

The OpenAPI plugin registers its own handler. Whether Ingot's request pipeline (`before` handlers in `web/infrastructure/`,
the read-only and request-limit wrappers) sits in front of it is not visible from the code alone, which is why the two pages
disagree. The integration test requests `/openapi` without credentials and asserts the status that the server really returns, and
the docs are written from that result. Access is not changed here.

## Consequences

- The document gets larger by a handful of entries. The gate change snapshots it afterwards.
- A future endpoint cannot silently skip documentation. A contributor who has a good reason adds an exclusion with a reason.
