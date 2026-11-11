# Nexus REST fixtures

Hand-written from the Sonatype documentation for Nexus Repository 3 (REST v1, base path `/service/rest/v1`).
Nothing here was observed on a live Nexus. Every field is therefore either **documented** (read in the Sonatype
doc page or in a Sonatype example quoted there, URL given) or **documented-unverified** (not found in the docs we
read, or only in a request example, or only in a community post). Fixtures use the documented shape; values are examples.

Per-field status is written next to each field in the "Fields" column. Doc URLs base:
`https://help.sonatype.com/repomanager3/integrations/rest-and-integration-api/`
(short names below: PAG = `pagination`, ASSET = `assets-api`, COMP = `components-api`, REPO = `repositories-api`, STAT = `status-api`).
PRIV = `https://help.sonatype.com/repomanager3/nexus-repository-administration/access-control/privileges`.

## Endpoints

| Endpoint | Request | Fields the MVP reads (status) | Permission needed | Fixture |
| --- | --- | --- | --- | --- |
| Status | `GET /service/rest/v1/status` | 200 = can serve reads, 503 = cannot (documented, STAT); empty body (documented-unverified, body not described) | not stated (documented-unverified) | `status.json` |
| Repositories list | `GET /service/rest/v1/repositories` | `name`, `format`, `type`, `url`, `attributes` (documented, REPO example); `size` also in example; `type` values hosted/proxy/group (documented-unverified, only `"group"` seen in an example) | browse permission on the repository (documented, REPO) | `repositories.json` |
| Hosted settings | `GET /service/rest/v1/repositories/maven/hosted/{name}` | path pattern `/v1/repositories/{format}/{type}/{name}` (documented, REPO); `online`, `storage.blobStoreName`, `storage.writePolicy`, `maven.versionPolicy`, `maven.layoutPolicy` (documented-unverified, appear only in a POST request example in REPO); `cleanup.policyNames` (documented-unverified) | not stated (documented-unverified) | `maven-hosted-releases.json`, `maven-hosted-snapshots.json` |
| Proxy settings | `GET /service/rest/v1/repositories/maven/proxy/{name}` | `proxy.remoteUrl` (documented-unverified as a top-level settings field; documented as `attributes.proxy.remoteUrl` in the list example of REPO); `proxy.contentMaxAge`, `proxy.metadataMaxAge`, `httpClient.authentication.type`, `httpClient.authentication.username` (documented-unverified, no docs read); password (not documented, expected absent) | not stated (documented-unverified) | `maven-proxy.json` |
| Group settings | `GET /service/rest/v1/repositories/maven/group/{name}` | `group.memberNames` in order (documented in the example of GET `.../maven/group/maven-public` in community post https://community.sonatype.com/t/nexus-3-rest-api-get-repository-info/5828, not in the reference) | not stated (documented-unverified) | `maven-group.json` |
| Assets list | `GET /service/rest/v1/assets?repository={name}&continuationToken={t}` | `items`, `continuationToken`, `repository` param (documented, ASSET); `path`, `id`, `repository`, `format`, `downloadUrl` (documented, ASSET example); `checksum.sha1`, `checksum.md5` (documented, ASSET example); `fileSize`, `lastModified`, `contentType` (documented in the asset object of COMP example and in ASSET get-one example, not in the list example) | not stated on ASSET page (documented-unverified). PRIV says the read action grants access to content "from the APIs" (documented). | `assets-page-1.json`, `assets-page-2.json` |
| Content download | `GET {downloadUrl}` | `downloadUrl` field (documented, ASSET); the GET returning bytes (documented-unverified, not read) | read action on the repository (documented, PRIV) | not a fixture (fake server serves bytes) |
| Pagination | `continuationToken` argument on the same query | non-null token = more items; null = last page (documented, PAG); default page size 50 for assets and components (documented, PAG, "as of 3.74.0... not configurable") but 100 for assets (documented, ASSET) and components (documented, COMP): **sources conflict**; token opaque (not documented); order stable under writes (not documented) | not stated | `assets-page-*.json` |
| Credential check (401/403) | `GET /service/rest/v1/repositories` with Basic auth | 401 and 403 bodies and meaning (documented-unverified, not in the pages read) | - | `error-401.json`, `error-403.json` |
| 429 throttling | any request | `Retry-After` header (documented-unverified) | - | `error-429-headers.json` |

Error bodies: no Sonatype page we read shows the 401, 403 or 429 body. `error-401.json` and `error-403.json` are `{}`
as a placeholder, not as a recorded empty body. The 429 `Retry-After` header is not documented; the fixture
describes the response the client should handle.

Version and edition from the `Server` header (`Nexus/3.70.1-02 (OSS)`) is documented-unverified: not in the pages read.

## Answers to the six facts

1. **Page size.** Not fixed in the docs. PAG says the default is 50 for assets and components and that it is
   "not configurable"; ASSET and COMP say 100 "as of release 3.74.0". The sources disagree, so the client follows
   `continuationToken` and never assumes a size (as design.md says).
2. **Token opaque, order stable.** Neither is stated in any page read. Treat the token as opaque and the order as
   unstable under writes (design.md keeps "Resume" for this reason).
3. **Checksums.** The documented examples (ASSET, COMP) show `checksum.sha1` and `checksum.md5` only. No Sonatype
   page we read lists `sha256` or `sha512` in the response. A community answer says Nexus computes md5, sha1, sha256
   and sha512 on upload but only writes md5 and sha1 files for components; release notes for 3.29.0 mention SHA-256 and
   SHA-512 for Maven (not read, lead only: https://help.sonatype.com/repomanager3/product-information/release-notes/2021-release-notes/nexus-repository-3.29.0---3.29.2-release-notes).
   So `sha256` in the fixture is omitted; the plugin computes sha256 locally.
4. **Permissions.** Not stated for the settings GETs. The administrator read permission in design.md is not in the
   docs we read. The asset listing privilege is not stated on the ASSET page. PRIV defines read as access to content
   through APIs and browse as UI and search; a browse-only credential does not download. Repositories list: browse
   permission (REPO). The REST API overview (https://help.sonatype.com/repomanager3/integrations/rest-and-integration-api) says the Swagger UI needs `nx-settings-read`; nothing about the settings GETs.
5. **Proxy password.** The docs do not document the `httpClient` block in the GET response at all. A single community
   thread shows the authentication password masked as asterisks in an example, which suggests masking rather than
   omission. Expected behaviour (no password in GET) is therefore documented-unverified. `maven-proxy.json` has no
   password field.
6. **maven-metadata.xml and sidecars as assets.** Not confirmed. The Maven layout defines checksums as md5 and sha1 and
   a community post says Nexus writes md5 and sha1 files for components. Whether `.sha1` and `.md5` sidecars and
   `maven-metadata.xml` are listed as assets of a maven2 hosted repository is documented-unverified. `assets-page-2.json`
   lists `maven-metadata.xml` as an example asset only.

## Differences from design.md

Every item below is for the section "The Nexus side" unless named otherwise.

1. Facts list, item 1: the sources say 50 (PAG) and also 100 for assets (ASSET). The design names only Pagination (50) and Components (100). Correct the page size sentence to "sources conflict: 50 or 100".
2. Facts list, item 2: "A token is opaque, and the order is not stable" is not documented. Reword it as an assumption.
3. Facts list, item 3: the documented checksum keys are `sha1` and `md5` only. Keep "maybe more", but say sha256 and sha512 are not in the docs read. "A sha256 is always computed locally" is consistent.
4. Facts list, item 4: "administrator read permission" for settings and "browse and read" for assets are not stated in the docs read. Mark as expected.
5. Endpoint table, Proxy settings row: `proxy.remoteUrl` is documented in the list example only under `attributes.proxy.remoteUrl`. Correct the field path or mark the settings path as unverified.
6. Endpoint table, Status row: 200 and 503 are documented; the empty body is not described. Only the status code is documented.
7. Endpoint table, Credential check row: the 401 and 403 statuses and the 429 behaviour are not in the pages read.
8. Endpoint table, Version row: the `Server` header format is not in the pages read.
9. Endpoint table, Hosted settings row: `storage.writePolicy` values `ALLOW`, `ALLOW_ONCE`, `DENY` come from the design, not from the docs. The docs show `allow_once` (lower case) in a POST request example only. Confirm the case on the real response.
10. Endpoint table, Hosted settings and Group settings rows: the maven-specific paths (`/maven/hosted/{name}`, `/maven/group/{name}`) are not on REPO; only the generic `/repositories/{format}/{type}/{name}` pattern is. The group example comes from a community post.
11. Facts list, item 5: the design presents "not readable" as a fact. The docs say nothing about the password in the GET response.
12. Facts list, item 6: sidecar and `maven-metadata.xml` listing is not confirmed by any page read.
13. Design "Credentials" or "Risks" are not affected. Note for later: CVE-2026-77122 (Nexus 3.38.0 to 3.95.x) lets a credential with read or browse on a group see member metadata it has no direct permission for (source: https://www.invicti.com/web-application-vulnerabilities/nexus-incorrect-authorization-vulnerability-cve-2026-77122). Not a design change, but it bears on group handling.
