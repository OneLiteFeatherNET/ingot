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
 * The artifact search of the fake backend: GET /api/maven/search and GET /api/maven/search/files, with the
 * shapes of spec section 6 (docs/superpowers/specs/2026-10-16-artifact-search.md). The parameters, the free
 * text rules, the grouping, the facets and the paging follow sections 4.1 to 4.5. The answers are computed
 * from a list of file rows, so the dashboard can be developed against it without a database behind it.
 *
 * Nothing here reads the clock or the environment: the file rows and the index state are arguments, and
 * the instants in the sample data are fixed. The fake backend decides which state to simulate.
 *
 * The state simulates the index of spec section 4.8 and the switch of section 6.4:
 *   ready (default)  a complete index
 *   building         a partial index with `repositoriesDone` and `repositoriesTotal` in the status
 *   missing          an empty index
 *   degraded         a usable index that lags by seven minutes
 *   disabled         every search endpoint answers 404, as with INGOT_LOCAL_SEARCHENABLED=false
 */

const MAX_QUERY_TEXT = 200
const MAX_TOKENS = 5
const DEFAULT_SIZE = 20
const MAX_SIZE = 100
const DEFAULT_FILES_SIZE = 50
const MAX_FILES_SIZE = 200
const RESULT_WINDOW = 10000
const MAX_BUCKETS = 20

const NOT_FOUND = { status: 404, body: { status: 404, message: "Not found" } }
const bad = (parameter, message) => ({ status: 400, body: { status: 400, message: `Invalid parameter '${parameter}': ${message}` } })

const list = (value) => [value ?? []].flat().map(String).filter(entry => entry.trim() !== "")
const single = (value) => (Array.isArray(value) ? value[value.length - 1] : value)

const isSnapshot = (version) => version.toLowerCase().endsWith("-snapshot")

// Maven-like ordering: numeric segments compare as numbers, a qualifier sorts before the plain release.
const compareVersions = (left, right) => {
  const parts = (version) => version.toLowerCase().replace(/-snapshot$/, "").split(/[.-]/)
    .map(part => (/^\d+$/.test(part) ? Number(part) : part))
  const a = parts(left)
  const b = parts(right)
  for (let index = 0; index < Math.max(a.length, b.length); index++) {
    const x = a[index] ?? 0
    const y = b[index] ?? 0
    if (x === y) continue
    if (typeof x === "number" && typeof y === "number") return x - y
    return String(x) < String(y) ? -1 : 1
  }
  return 0
}

const compareText = (left, right) => (left < right ? -1 : left > right ? 1 : 0)

const encodePath = (path) => path.split("/").map(encodeURIComponent).join("/")

const pathOf = (entry) => {
  const classifier = entry.classifier ? `-${entry.classifier}` : ""
  return `${entry.groupId.split(".").join("/")}/${entry.artifactId}/${entry.version}/${entry.artifactId}-${entry.version}${classifier}.${entry.extension}`
}

const instantMs = (value) => Date.parse(value)

// The query, checked as the real endpoint checks it: a value that does not fit is a 400 that names the parameter.
const readQuery = (query) => {
  const text = single(query.q) ?? ""
  if (text.length > MAX_QUERY_TEXT) return { error: bad("q", `at most ${MAX_QUERY_TEXT} characters`) }
  const oneOf = (name, allowed, fallback) => {
    const value = single(query[name])
    if (value === undefined || value === "") return fallback
    return allowed.includes(value) ? value : null
  }
  const whole = (name, fallback, minimum) => {
    const value = single(query[name])
    if (value === undefined || value === "") return fallback
    return /^\d+$/.test(value) && Number(value) >= minimum ? Number(value) : null
  }
  const instant = (name) => {
    const value = single(query[name])
    if (value === undefined || value === "") return undefined
    return Number.isNaN(instantMs(value)) ? null : instantMs(value)
  }
  const snapshot = oneOf("snapshot", ["true", "false", "all"], "all")
  const origin = oneOf("origin", ["hosted", "mirror", "all"], "all")
  const sort = oneOf("sort", ["groupId", "artifactId", "version", "modified", "size"], "groupId")
  const order = oneOf("order", ["asc", "desc"], "asc")
  const page = whole("page", 0, 0)
  const size = whole("size", DEFAULT_SIZE, 1)
  const modifiedAfter = instant("modifiedAfter")
  const modifiedBefore = instant("modifiedBefore")
  const sizeMin = whole("sizeMin", undefined, 0)
  const sizeMax = whole("sizeMax", undefined, 0)
  const checks = { snapshot, origin, sort, order, page, size, modifiedAfter, modifiedBefore, sizeMin, sizeMax }
  for (const [name, value] of Object.entries(checks)) {
    if (value === null) return { error: bad(name, "the value is not allowed") }
  }
  const version = single(query.version)
  const versionPrefix = single(query.versionPrefix)
  if (version && versionPrefix) return { error: bad("versionPrefix", "cannot be combined with version") }
  return {
    params: {
      tokens: text.toLowerCase().split(/\s+/).filter(token => token !== "").slice(0, MAX_TOKENS),
      groupId: (single(query.groupId) ?? "").toLowerCase(),
      artifactId: (single(query.artifactId) ?? "").toLowerCase(),
      version: (version ?? "").toLowerCase(),
      versionPrefix: (versionPrefix ?? "").toLowerCase(),
      repository: list(query.repository),
      extension: list(query.extension).map(value => value.toLowerCase()),
      classifier: list(query.classifier),
      sha1: (single(query.sha1) ?? "").toLowerCase(),
      snapshot, origin, sort, order, page,
      size: Math.min(size, MAX_SIZE),
      modifiedAfter, modifiedBefore, sizeMin, sizeMax,
      facets: single(query.facets) !== "false"
    }
  }
}

// One token matches a version row when the group or the artifact starts with it or the version equals it.
// A token with a colon is a coordinates query, each part optional (spec section 4.2).
const tokenMatches = (token, entry) => {
  const group = entry.groupId.toLowerCase()
  const artifact = entry.artifactId.toLowerCase()
  const version = entry.version.toLowerCase()
  if (token.includes(":")) {
    const [g = "", a = "", v = ""] = token.split(":")
    return group.startsWith(g) && artifact.startsWith(a) && (v === "" || version === v)
  }
  return group.startsWith(token) || artifact.startsWith(token) || version === token
}

// Whether a file passes the filters. `skip` names a facet whose own filter is ignored (spec section 4.4).
const passes = (entry, p, skip = "") => {
  if (!p.tokens.every(token => tokenMatches(token, entry))) return false
  if (p.groupId && !entry.groupId.toLowerCase().startsWith(p.groupId)) return false
  if (p.artifactId && !entry.artifactId.toLowerCase().startsWith(p.artifactId)) return false
  if (p.version && entry.version.toLowerCase() !== p.version) return false
  if (p.versionPrefix && !entry.version.toLowerCase().startsWith(p.versionPrefix)) return false
  if (skip !== "repository" && p.repository.length > 0 && !p.repository.includes(entry.repository)) return false
  if (skip !== "extension" && p.extension.length > 0 && !p.extension.includes(entry.extension)) return false
  if (skip !== "classifier" && p.classifier.length > 0 && !p.classifier.includes(entry.classifier || "-")) return false
  if (skip !== "snapshot" && p.snapshot !== "all" && String(isSnapshot(entry.version)) !== p.snapshot) return false
  if (skip !== "origin" && p.origin !== "all" && entry.origin.toLowerCase() !== p.origin) return false
  if (p.sha1 && entry.sha1 !== p.sha1) return false
  if (p.modifiedAfter !== undefined && instantMs(entry.lastModified) < p.modifiedAfter) return false
  if (p.modifiedBefore !== undefined && instantMs(entry.lastModified) >= p.modifiedBefore) return false
  if (p.sizeMin !== undefined && entry.size < p.sizeMin) return false
  if (p.sizeMax !== undefined && entry.size > p.sizeMax) return false
  return true
}

const FACET_KEYS = {
  repository: entry => entry.repository,
  extension: entry => entry.extension,
  classifier: entry => entry.classifier || "-",
  origin: entry => entry.origin,
  snapshot: entry => String(isSnapshot(entry.version))
}

const facetsOf = (visible, p) => Object.fromEntries(Object.entries(FACET_KEYS).map(([name, keyOf]) => {
  const counts = new Map()
  for (const entry of visible.filter(candidate => passes(candidate, p, name))) {
    counts.set(keyOf(entry), (counts.get(keyOf(entry)) ?? 0) + 1)
  }
  const buckets = [...counts.entries()]
    .sort((left, right) => right[1] - left[1] || compareText(left[0], right[0]))
    .slice(0, MAX_BUCKETS)
    .map(([value, count]) => ({ value, count }))
  return [name, buckets]
}))

const groupKey = (entry) => [entry.groupId, entry.artifactId, entry.version].map(part => part.toLowerCase()).join("\u0000")

// The highest version of an artifact among what the caller may see, a release in preference to a snapshot.
const latestVersions = (visible) => {
  const byArtifact = new Map()
  for (const entry of visible) {
    const key = `${entry.groupId.toLowerCase()}\u0000${entry.artifactId.toLowerCase()}`
    if (!byArtifact.has(key)) byArtifact.set(key, new Set())
    byArtifact.get(key).add(entry.version)
  }
  return new Map([...byArtifact.entries()].map(([key, versions]) => {
    const all = [...versions].sort(compareVersions)
    const releases = all.filter(version => !isSnapshot(version))
    return [key, (releases.length > 0 ? releases : all).at(-1)]
  }))
}

const rowsOf = (matching, visible) => {
  const latest = latestVersions(visible)
  const groups = new Map()
  for (const entry of matching) {
    const key = groupKey(entry)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(entry)
  }
  return [...groups.values()].map(entries => {
    const [first] = entries
    const repositories = new Map()
    for (const entry of entries) repositories.set(`${entry.origin}:${entry.repository}`, { name: entry.repository, origin: entry.origin })
    const ordered = [...repositories.values()].sort((left, right) =>
      compareText(left.origin, right.origin) || compareText(left.name, right.name))
    const lastModified = entries.map(entry => entry.lastModified).sort().at(-1)
    return {
      groupId: first.groupId,
      artifactId: first.artifactId,
      version: first.version,
      latestVersion: latest.get(`${first.groupId.toLowerCase()}\u0000${first.artifactId.toLowerCase()}`) ?? null,
      snapshot: isSnapshot(first.version),
      repositories: ordered,
      fileCount: entries.length,
      lastModified,
      browseLink: `/browse/${encodeURIComponent(ordered[0].name)}/${encodePath(pathOf(first).split("/").slice(0, -1).join("/"))}`,
      totalSize: entries.reduce((sum, entry) => sum + entry.size, 0)
    }
  })
}

const SORTS = {
  groupId: (a, b) => compareText(a.groupId.toLowerCase(), b.groupId.toLowerCase()),
  artifactId: (a, b) => compareText(a.artifactId.toLowerCase(), b.artifactId.toLowerCase()),
  version: (a, b) => compareText(a.version.toLowerCase(), b.version.toLowerCase()),
  modified: (a, b) => instantMs(a.lastModified) - instantMs(b.lastModified),
  size: (a, b) => a.totalSize - b.totalSize
}

const sortRows = (rows, p) => {
  const direction = p.order === "desc" ? -1 : 1
  // The coordinates are the tie-breakers, so the order is total and paging is stable. The default sort
  // lists the newest version of an artifact first, as the real query does.
  const ties = (a, b) => SORTS.groupId(a, b) || SORTS.artifactId(a, b)
    || (p.sort === "groupId" && p.order === "asc" ? -SORTS.version(a, b) : SORTS.version(a, b))
  return [...rows].sort((a, b) => direction * SORTS[p.sort](a, b) || ties(a, b))
}

const STATUS = {
  ready: () => ({ state: "READY", lagSeconds: 0, lastFullRebuild: "2026-10-20T03:30:12Z" }),
  building: () => ({ state: "BUILDING", lagSeconds: 0, repositoriesDone: 2, repositoriesTotal: 5 }),
  missing: () => ({ state: "MISSING", lagSeconds: 0 }),
  degraded: () => ({ state: "DEGRADED", lagSeconds: 420, lastFullRebuild: "2026-10-20T03:30:12Z" })
}

/**
 * A search over `files`. A file row has: repository, origin ("HOSTED" or "MIRROR"), sourceHost (or null),
 * groupId, artifactId, version, classifier ("" for none), extension, size (bytes), lastModified (ISO
 * instant) and sha1. `publicRepositories` are the repositories an anonymous caller may browse.
 */
const createSearch = ({ files, publicRepositories = [], state = "ready", windowCap = RESULT_WINDOW }) => {
  const indexed = state === "missing" ? [] : state === "building" ? files.filter((_, index) => index % 2 === 0) : files
  const visibleTo = ({ signedIn }) => indexed.filter(entry => signedIn || publicRepositories.includes(entry.repository))

  const search = (query, who) => {
    if (state === "disabled") return NOT_FOUND
    const { params: p, error } = readQuery(query)
    if (error) return error
    if (p.page * p.size + p.size > RESULT_WINDOW) {
      return { status: 400, body: { status: 400, message: "Result window is limited to 10000 entries, narrow the filters" } }
    }
    const visible = visibleTo(who)
    const rows = sortRows(rowsOf(visible.filter(entry => passes(entry, p)), visible), p)
    const capped = rows.length > windowCap
    const body = {
      items: rows.slice(p.page * p.size, p.page * p.size + p.size).map(({ totalSize, ...row }) => row),
      page: p.page,
      size: p.size,
      total: capped ? windowCap + 1 : rows.length,
      totalCapped: capped,
      indexStatus: STATUS[state]()
    }
    if (p.facets) body.facets = facetsOf(visible, p)
    return { status: 200, body }
  }

  const filesOf = (query, who) => {
    if (state === "disabled") return NOT_FOUND
    for (const name of ["groupId", "artifactId", "version"]) {
      if (!single(query[name])) return bad(name, "required")
    }
    const origin = single(query.origin) ?? "all"
    if (!["hosted", "mirror", "all"].includes(origin)) return bad("origin", "the value is not allowed")
    const page = /^\d+$/.test(single(query.page) ?? "0") ? Number(single(query.page) ?? 0) : null
    const size = /^\d+$/.test(single(query.size) ?? String(DEFAULT_FILES_SIZE)) ? Number(single(query.size) ?? DEFAULT_FILES_SIZE) : null
    if (page === null) return bad("page", "the value is not allowed")
    if (size === null || size < 1) return bad("size", "the value is not allowed")
    const repositories = list(query.repository)
    const matching = visibleTo(who)
      .filter(entry => entry.groupId.toLowerCase() === single(query.groupId).toLowerCase()
        && entry.artifactId.toLowerCase() === single(query.artifactId).toLowerCase()
        && entry.version.toLowerCase() === single(query.version).toLowerCase())
      .filter(entry => origin === "all" || entry.origin.toLowerCase() === origin)
      .filter(entry => repositories.length === 0 || repositories.includes(entry.repository))
      .sort((a, b) => compareText(pathOf(a), pathOf(b)) || compareText(a.repository, b.repository))
    const pageSize = Math.min(size, MAX_FILES_SIZE)
    return {
      status: 200,
      body: {
        items: matching.slice(page * pageSize, page * pageSize + pageSize).map(entry => ({
          repository: entry.repository,
          path: pathOf(entry),
          name: pathOf(entry).split("/").at(-1),
          classifier: entry.classifier || null,
          extension: entry.extension,
          size: entry.size,
          lastModified: entry.lastModified,
          origin: entry.origin,
          sourceHost: entry.origin === "MIRROR" && who.signedIn ? entry.sourceHost : null,
          sha1: entry.sha1,
          browseLink: `/browse/${encodeURIComponent(entry.repository)}/${encodePath(pathOf(entry))}`
        })),
        page,
        size: pageSize,
        total: matching.length
      }
    }
  }

  return { search, files: filesOf }
}

// A small deterministic number from a text, so the sample sizes and dates do not change between runs.
const spread = (text) => [...text].reduce((sum, character) => (sum * 31 + character.charCodeAt(0)) % 1000003, 7)

const SAMPLE = [
  { g: "org.apache.commons", a: "commons-lang3", v: ["3.12.0", "3.13.0", "3.14.0", "3.17.0"], repos: [["releases"], ["mirror-cache", "repo.maven.apache.org"]], kinds: ["jar", "pom", "sources", "javadoc"] },
  { g: "org.apache.commons", a: "commons-io", v: ["2.15.1", "2.16.1", "2.18.0"], repos: [["mirror-cache", "repo.maven.apache.org"]], kinds: ["jar", "pom", "sources"] },
  { g: "org.apache.commons", a: "commons-text", v: ["1.11.0", "1.12.0"], repos: [["releases"]], kinds: ["jar", "pom"] },
  { g: "com.google.guava", a: "guava", v: ["32.1.3-jre", "33.0.0-jre", "33.4.0-jre"], repos: [["mirror-cache", "repo.maven.apache.org"], ["public"]], kinds: ["jar", "pom", "sources", "module"] },
  { g: "com.fasterxml.jackson.core", a: "jackson-databind", v: ["2.16.2", "2.17.2", "2.18.1"], repos: [["mirror-cache", "repo.maven.apache.org"]], kinds: ["jar", "pom", "module"] },
  { g: "org.slf4j", a: "slf4j-api", v: ["1.7.36", "2.0.13", "2.0.16"], repos: [["releases"], ["mirror-cache", "repo.maven.apache.org"]], kinds: ["jar", "pom", "sources"] },
  { g: "org.jetbrains.kotlin", a: "kotlin-stdlib", v: ["1.9.24", "2.0.21", "2.1.0"], repos: [["mirror-cache", "jitpack.io"]], kinds: ["jar", "pom", "module", "sources"] },
  { g: "net.onelitefeather", a: "ingot-api", v: ["1.2.0", "1.3.0", "1.4.0-SNAPSHOT", "1.5.0-SNAPSHOT"], repos: [["releases"], ["snapshots"]], kinds: ["jar", "pom", "sources", "javadoc"] },
  { g: "net.onelitefeather", a: "ingot-client", v: ["0.9.0", "1.0.0-SNAPSHOT"], repos: [["snapshots"], ["releases"]], kinds: ["jar", "pom"] },
  { g: "net.onelitefeather.internal", a: "billing-core", v: ["4.0.1", "4.1.0"], repos: [["private"]], kinds: ["jar", "pom"] },
  { g: "net.onelitefeather.legacy", a: "old-tool", v: ["0.1.0"], repos: [["archive"]], kinds: ["jar", "pom"] }
]

const KIND = {
  jar: { extension: "jar", classifier: "" },
  pom: { extension: "pom", classifier: "" },
  sources: { extension: "jar", classifier: "sources" },
  javadoc: { extension: "jar", classifier: "javadoc" },
  module: { extension: "module", classifier: "" }
}

const SIZE_OF = { jar: 400000, pom: 2500, sources: 160000, javadoc: 900000, module: 5000 }

/** Deterministic sample rows for the fake backend: hosted and cached copies, snapshots and hidden repositories. */
const sampleFiles = () => SAMPLE.flatMap(entry => entry.v.flatMap((version, versionIndex) =>
  entry.repos.flatMap(([repository, sourceHost]) => entry.kinds.map(kind => {
    const key = `${entry.g}:${entry.a}:${version}:${repository}:${kind}`
    const day = 1 + (spread(key) % 28)
    const month = 1 + ((versionIndex * 3 + spread(repository)) % 9)
    return {
      repository,
      origin: sourceHost ? "MIRROR" : "HOSTED",
      sourceHost: sourceHost ?? null,
      groupId: entry.g,
      artifactId: entry.a,
      version,
      ...KIND[kind],
      size: SIZE_OF[kind] + (spread(key) % 90000),
      lastModified: `2026-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}T0${versionIndex}:30:00Z`,
      sha1: spread(key).toString(16).padStart(8, "0").repeat(5)
    }
  }))))

module.exports = { createSearch, sampleFiles }
