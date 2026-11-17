/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Tests of the artifact search of the fake backend. They use the test runner that ships with Node:
 *   node --test fake-api/search.test.cjs
 * Every test builds its own search over its own files and nothing reads a clock or the environment.
 */

const { describe, it } = require("node:test")
const assert = require("node:assert/strict")
const { createSearch, sampleFiles } = require("./search.cjs")

const file = (overrides = {}) => ({
  repository: "releases",
  origin: "HOSTED",
  sourceHost: null,
  groupId: "org.example",
  artifactId: "core",
  version: "1.0.0",
  classifier: "",
  extension: "jar",
  size: 1000,
  lastModified: "2026-01-01T00:00:00Z",
  sha1: "a".repeat(40),
  ...overrides
})

const search = (files, options = {}) => createSearch({ files, publicRepositories: ["releases", "snapshots", "central-proxy"], ...options })
const ask = (files, query = {}, who = { signedIn: true }, options = {}) => search(files, options).search(query, who)
const items = (files, query, who) => ask(files, query, who).body.items

describe("access", () => {
  it("shows an anonymous caller only the public repositories", () => {
    const files = [file(), file({ repository: "private", artifactId: "secret" })]

    assert.deepEqual(items(files, {}, { signedIn: false }).map(item => item.artifactId), ["core"])
  })

  it("shows a signed in caller every repository", () => {
    const files = [file(), file({ repository: "private", artifactId: "secret" })]

    assert.deepEqual(items(files, {}).map(item => item.artifactId), ["core", "secret"])
  })

  it("never names a repository the caller cannot see in the facets", () => {
    const files = [file(), file({ repository: "private", artifactId: "secret" })]

    const facet = ask(files, {}, { signedIn: false }).body.facets.repository

    assert.deepEqual(facet.map(bucket => bucket.value), ["releases"])
  })
})

describe("grouping", () => {
  it("makes one row per version with the repositories that hold it", () => {
    const files = [
      file(), file({ extension: "pom" }),
      file({ repository: "central-proxy", origin: "MIRROR", sourceHost: "repo.maven.apache.org" })
    ]

    const [row] = items(files)

    assert.equal(items(files).length, 1)
    assert.deepEqual(row.repositories, [{ name: "releases", origin: "HOSTED" }, { name: "central-proxy", origin: "MIRROR" }])
    assert.equal(row.fileCount, 3)
  })

  it("counts only the files that match the filters", () => {
    const files = [file(), file({ extension: "pom" })]

    const [row] = items(files, { extension: "pom" })

    assert.equal(row.fileCount, 1)
  })

  it("links the first repository of a version to the browser, with each segment encoded", () => {
    const files = [file({ groupId: "org.example", artifactId: "my lib", version: "1.0.0" })]

    const [row] = items(files)

    assert.equal(row.browseLink, "/browse/releases/org/example/my%20lib/1.0.0")
  })

  it("marks a snapshot by its version", () => {
    const files = [file({ version: "2.0.0-SNAPSHOT" }), file({ version: "1.0.0" })]

    assert.deepEqual(items(files, { sort: "version", order: "desc" }).map(item => item.snapshot), [true, false])
  })

  it("names the latest release of the artifact, ignoring the filters and preferring releases to snapshots", () => {
    const files = [file({ version: "1.0.0" }), file({ version: "1.10.0" }), file({ version: "1.9.0" }), file({ version: "2.0.0-SNAPSHOT" })]

    const rows = items(files, { version: "1.9.0" })

    assert.equal(rows[0].latestVersion, "1.10.0")
  })

  it("names the latest snapshot for an artifact that has only snapshots", () => {
    const files = [file({ version: "1.0.0-SNAPSHOT" }), file({ version: "1.1.0-SNAPSHOT" })]

    assert.equal(items(files)[0].latestVersion, "1.1.0-SNAPSHOT")
  })
})

describe("filters", () => {
  const files = [
    file({ groupId: "org.apache.commons", artifactId: "commons-lang3", version: "3.14.0" }),
    file({ groupId: "org.apache.commons", artifactId: "commons-io", version: "2.16.1", repository: "snapshots" }),
    file({ groupId: "com.google", artifactId: "guava", version: "33.0.0" })
  ]
  const names = (query) => items(files, query).map(item => item.artifactId)

  it("matches a text token by group prefix, artifact prefix or exact version", () => {
    assert.deepEqual(names({ q: "org.apache" }), ["commons-io", "commons-lang3"])
    assert.deepEqual(names({ q: "guava" }), ["guava"])
    assert.deepEqual(names({ q: "3.14.0" }), ["commons-lang3"])
  })

  it("does not match the middle of a word", () => {
    assert.deepEqual(names({ q: "lang3" }), [])
  })

  it("requires every token", () => {
    assert.deepEqual(names({ q: "commons 2.16.1" }), ["commons-io"])
  })

  it("reads group:artifact as a coordinates query", () => {
    assert.deepEqual(names({ q: "org.apache.commons:commons-l" }), ["commons-lang3"])
    assert.deepEqual(names({ q: ":guava" }), ["guava"])
  })

  it("filters by the prefix of the group and of the artifact, without regard to case", () => {
    assert.deepEqual(names({ groupId: "COM.GOOGLE" }), ["guava"])
    assert.deepEqual(names({ artifactId: "commons-i" }), ["commons-io"])
  })

  it("filters by an exact version or by the prefix of a version", () => {
    assert.deepEqual(names({ version: "33.0.0" }), ["guava"])
    assert.deepEqual(names({ versionPrefix: "3" }), ["guava", "commons-lang3"], "ordered by group, and com.google comes first")
  })

  it("rejects an exact version together with a version prefix", () => {
    const answer = ask(files, { version: "1", versionPrefix: "1" })

    assert.equal(answer.status, 400)
    assert.match(answer.body.message, /versionPrefix/)
  })

  it("combines repeated values of one parameter with OR", () => {
    assert.deepEqual(names({ repository: ["snapshots", "releases"] }), ["guava", "commons-io", "commons-lang3"])
    assert.deepEqual(names({ repository: "snapshots" }), ["commons-io"])
  })

  it("matches nothing for a repository that does not exist", () => {
    assert.deepEqual(names({ repository: "nowhere" }), [])
  })

  it("selects files without a classifier with a dash", () => {
    const two = [file(), file({ classifier: "sources" })]

    assert.equal(items(two, { classifier: "-" })[0].fileCount, 1)
    assert.equal(items(two, { classifier: "sources" })[0].fileCount, 1)
  })

  it("filters by release or snapshot", () => {
    const two = [file({ version: "1.0.0" }), file({ version: "1.1.0-SNAPSHOT" })]

    assert.deepEqual(items(two, { snapshot: "true" }).map(item => item.version), ["1.1.0-SNAPSHOT"])
    assert.deepEqual(items(two, { snapshot: "false" }).map(item => item.version), ["1.0.0"])
  })

  it("filters by origin", () => {
    const two = [file(), file({ repository: "central-proxy", origin: "MIRROR", artifactId: "cached" })]

    assert.deepEqual(items(two, { origin: "mirror" }).map(item => item.artifactId), ["cached"])
    assert.deepEqual(items(two, { origin: "hosted" }).map(item => item.artifactId), ["core"])
  })

  it("filters by modification: after is inclusive, before is exclusive", () => {
    const two = [file({ lastModified: "2026-01-01T00:00:00Z" }), file({ artifactId: "later", lastModified: "2026-06-01T00:00:00Z" })]

    assert.deepEqual(items(two, { modifiedAfter: "2026-06-01T00:00:00Z" }).map(item => item.artifactId), ["later"])
    assert.deepEqual(items(two, { modifiedBefore: "2026-06-01T00:00:00Z" }).map(item => item.artifactId), ["core"])
  })

  it("filters by size, both limits inclusive", () => {
    const two = [file({ size: 10 }), file({ artifactId: "big", size: 500 })]

    assert.deepEqual(items(two, { sizeMin: "11" }).map(item => item.artifactId), ["big"])
    assert.deepEqual(items(two, { sizeMax: "10" }).map(item => item.artifactId), ["core"])
  })

  it("reports a parameter that does not fit as a 400 that names it", () => {
    for (const query of [{ snapshot: "maybe" }, { origin: "cloud" }, { sort: "colour" }, { page: "-1" }, { size: "0" }, { modifiedAfter: "soon" }]) {
      const answer = ask(files, query)

      assert.equal(answer.status, 400, JSON.stringify(query))
      assert.match(answer.body.message, new RegExp(Object.keys(query)[0]))
    }
  })

  it("rejects a text longer than 200 characters", () => {
    assert.equal(ask(files, { q: "a".repeat(201) }).status, 400)
  })
})

describe("sort and paging", () => {
  const versions = ["1.9", "1.10", "1.2"].map((version, index) => file({ version, size: (index + 1) * 100, lastModified: `2026-0${index + 1}-01T00:00:00Z` }))

  it("sorts by version as text, as the real index does", () => {
    assert.deepEqual(items(versions, { sort: "version" }).map(item => item.version), ["1.10", "1.2", "1.9"])
  })

  it("sorts by size and by modification, and reverses the order", () => {
    assert.deepEqual(items(versions, { sort: "size", order: "desc" }).map(item => item.version), ["1.2", "1.10", "1.9"])
    assert.deepEqual(items(versions, { sort: "modified", order: "desc" }).map(item => item.version), ["1.2", "1.10", "1.9"])
  })

  it("pages the rows and reports the page, the size and the total", () => {
    const many = Array.from({ length: 45 }, (_, index) => file({ artifactId: `a${String(index).padStart(2, "0")}` }))

    const body = ask(many, { page: "2", size: "20" }).body

    assert.equal(body.items.length, 5)
    assert.deepEqual([body.page, body.size, body.total, body.totalCapped], [2, 20, 45, false])
  })

  it("caps the size at 100", () => {
    const many = Array.from({ length: 150 }, (_, index) => file({ artifactId: `a${index}` }))

    assert.equal(ask(many, { size: "500" }).body.size, 100)
  })

  it("answers a page beyond the result window with a 400 and the hint", () => {
    const answer = ask([file()], { page: "500", size: "20" })

    assert.equal(answer.status, 400)
    assert.equal(answer.body.message, "Result window is limited to 10000 entries, narrow the filters")
  })

  it("reports a capped total once the window is full", () => {
    const body = ask([file()], {}, { signedIn: true }, { windowCap: 0 }).body

    assert.equal(body.totalCapped, true)
  })
})

describe("facets", () => {
  const files = [
    file(), file({ extension: "pom" }), file({ classifier: "sources" }),
    file({ repository: "central-proxy", origin: "MIRROR", artifactId: "cached" })
  ]

  it("counts files per bucket, most files first", () => {
    const { extension } = ask(files).body.facets

    assert.deepEqual(extension, [{ value: "jar", count: 3 }, { value: "pom", count: 1 }])
  })

  it("counts files without a classifier as the bucket dash", () => {
    const { classifier } = ask(files).body.facets

    assert.deepEqual(classifier, [{ value: "-", count: 3 }, { value: "sources", count: 1 }])
  })

  it("spells the origin buckets as the rows spell them", () => {
    const { origin, snapshot } = ask(files).body.facets

    assert.deepEqual(origin, [{ value: "HOSTED", count: 3 }, { value: "MIRROR", count: 1 }])
    assert.deepEqual(snapshot, [{ value: "false", count: 4 }])
  })

  it("applies every filter but its own, so the alternatives stay visible", () => {
    const { extension, repository } = ask(files, { extension: "pom", repository: "central-proxy" }).body.facets

    assert.deepEqual(extension, [{ value: "jar", count: 1 }], "the extension facet ignores the extension filter")
    assert.deepEqual(repository, [{ value: "releases", count: 1 }], "the repository facet ignores the repository filter")
  })

  it("holds at most 20 buckets", () => {
    const many = Array.from({ length: 30 }, (_, index) => file({ extension: `e${String(index).padStart(2, "0")}` }))

    assert.equal(ask(many).body.facets.extension.length, 20)
  })

  it("leaves the facets out when facets=false", () => {
    assert.equal(ask(files, { facets: "false" }).body.facets, undefined)
  })
})

describe("index state", () => {
  const files = [file()]

  it("answers 404 when search is disabled, for the search and for the files", () => {
    const disabled = search(files, { state: "disabled" })

    assert.equal(disabled.search({}, { signedIn: true }).status, 404)
    assert.equal(disabled.files({ groupId: "g", artifactId: "a", version: "1" }, { signedIn: true }).status, 404)
  })

  it("reports a ready index with no lag", () => {
    const { indexStatus } = ask(files).body

    assert.equal(indexStatus.state, "READY")
    assert.equal(indexStatus.lagSeconds, 0)
    assert.equal(indexStatus.lastFullRebuild, "2026-10-20T03:30:12Z")
  })

  it("reports an index that is being built, with its progress, and a partial result", () => {
    const body = ask(files, {}, { signedIn: true }, { state: "building" }).body

    assert.deepEqual(
      [body.indexStatus.state, body.indexStatus.repositoriesDone, body.indexStatus.repositoriesTotal],
      ["BUILDING", 2, 5]
    )
    assert.ok(body.total <= files.length)
  })

  it("reports a missing index with nothing in it", () => {
    const body = ask(files, {}, { signedIn: true }, { state: "missing" }).body

    assert.equal(body.indexStatus.state, "MISSING")
    assert.deepEqual([body.items.length, body.total], [0, 0])
  })

  it("reports a degraded index with its lag", () => {
    const body = ask(files, {}, { signedIn: true }, { state: "degraded" }).body

    assert.equal(body.indexStatus.state, "DEGRADED")
    assert.ok(body.indexStatus.lagSeconds > 0)
  })
})

describe("files of a version", () => {
  const files = [
    file({ extension: "jar", classifier: "sources", size: 5 }),
    file({ extension: "jar", size: 50 }),
    file({ repository: "central-proxy", origin: "MIRROR", sourceHost: "repo.maven.apache.org", extension: "pom" }),
    file({ version: "2.0.0" })
  ]
  const coordinates = { groupId: "org.example", artifactId: "core", version: "1.0.0" }
  const filesOf = (query, who = { signedIn: true }) => search(files).files({ ...coordinates, ...query }, who)

  it("requires the three coordinates and names the one that is missing", () => {
    const answer = search(files).files({ groupId: "org.example", artifactId: "core" }, { signedIn: true })

    assert.equal(answer.status, 400)
    assert.match(answer.body.message, /version/)
  })

  it("lists the files of that version only, ordered by path", () => {
    const { body } = filesOf({})

    assert.equal(body.total, 3)
    assert.deepEqual(body.items.map(entry => entry.name), [...body.items.map(entry => entry.name)].sort())
    assert.ok(body.items.every(entry => entry.path.includes("/1.0.0/")))
  })

  it("matches the coordinates without regard to case", () => {
    assert.equal(filesOf({ groupId: "ORG.EXAMPLE" }).body.total, 3)
  })

  it("gives a file the fields of the contract", () => {
    const entry = filesOf({ repository: "central-proxy" }).body.items[0]

    assert.deepEqual(Object.keys(entry).sort(), [
      "browseLink", "classifier", "extension", "lastModified", "name", "origin", "path", "repository", "sha1", "size", "sourceHost"
    ])
    assert.equal(entry.origin, "MIRROR")
    assert.equal(entry.sourceHost, "repo.maven.apache.org")
    assert.equal(entry.browseLink, `/browse/central-proxy/${entry.path}`)
  })

  it("leaves the source host out for an anonymous caller", () => {
    const entry = filesOf({ repository: "central-proxy" }, { signedIn: false }).body.items[0]

    assert.equal(entry.sourceHost, null)
  })

  it("narrows the files by origin and by repository", () => {
    assert.equal(filesOf({ origin: "mirror" }).body.total, 1)
    assert.equal(filesOf({ repository: ["releases"] }).body.total, 2)
  })

  it("pages the files", () => {
    const body = filesOf({ page: "1", size: "2" }).body

    assert.deepEqual([body.items.length, body.page, body.size, body.total], [1, 1, 2, 3])
  })
})

describe("sample data", () => {
  it("has hosted and cached files, a snapshot and a private repository, for the dashboard in development", () => {
    const sample = sampleFiles()

    assert.ok(sample.some(entry => entry.origin === "MIRROR" && entry.sourceHost))
    assert.ok(sample.some(entry => entry.version.endsWith("-SNAPSHOT")))
    assert.ok(sample.some(entry => entry.repository === "private"))
    assert.ok(sample.length > 100, "enough rows to page through")
  })

  it("is the same on every call", () => {
    assert.deepEqual(sampleFiles(), sampleFiles())
  })
})
