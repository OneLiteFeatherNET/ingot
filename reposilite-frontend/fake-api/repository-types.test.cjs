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

// Run it with: node --test fake-api/repository-types.test.cjs
const { describe, it } = require("node:test")
const assert = require("node:assert/strict")
const { effectiveType, typeViolations, rejectionOf } = require("./repository-types.cjs")

const hosted = { id: "releases" }
const external = { reference: "https://example.com/maven" }

describe("effectiveType", () => {
  it("reports a declared type as explicit", () => {
    assert.deepEqual(effectiveType({ id: "all", type: "GROUP", proxied: [] }, []), { type: "group", typeExplicit: true })
  })

  it("derives hosted, group, proxy and mixed for untyped repositories", () => {
    const repositories = [hosted, { id: "central", proxied: [external] }]
    assert.equal(effectiveType(hosted, repositories).type, "hosted")
    assert.equal(effectiveType({ id: "all", proxied: [{ reference: "releases" }] }, repositories).type, "group")
    assert.equal(effectiveType({ id: "central", proxied: [external] }, repositories).type, "proxy")
    assert.equal(effectiveType({ id: "mix", proxied: [{ reference: "releases" }, external] }, repositories).type, "mixed")
  })

  it("keeps the production group with its empty entry an untyped group", () => {
    const repositories = [hosted, { id: "onelitefeather-snapshots" }]
    const group = { id: "onelitefeather", proxied: [{ reference: "releases" }, { reference: "" }, { reference: "onelitefeather-snapshots" }] }
    assert.deepEqual(effectiveType(group, [...repositories, group]), { type: "group", typeExplicit: false })
  })
})

describe("typeViolations", () => {
  it("accepts a valid set of typed repositories", () => {
    const settings = { repositories: [
      { id: "releases", type: "HOSTED" },
      { id: "central", type: "PROXY", proxied: [external] },
      { id: "all", type: "GROUP", proxied: [{ reference: "releases" }, { reference: "central" }] },
    ] }
    assert.deepEqual(typeViolations(settings), [])
    assert.equal(rejectionOf(settings), null)
  })

  it("names the repository and the rule for a hosted repository that proxies", () => {
    const violations = typeViolations({ repositories: [{ id: "releases", type: "HOSTED", proxied: [external] }] })
    assert.equal(violations.length, 1)
    assert.match(violations[0], /'releases'.*HOSTED/)
  })

  it("names a local repository used by a proxy", () => {
    const violations = typeViolations({ repositories: [{ id: "releases" }, { id: "central", type: "PROXY", proxied: [{ reference: "releases" }] }] })
    assert.match(violations.join("\n"), /'central'.*'releases'.*GROUP/)
  })

  it("names an unknown repository and an external URL used by a group", () => {
    const violations = typeViolations({ repositories: [{ id: "all", type: "GROUP", proxied: [{ reference: "ghost" }, external] }] })
    assert.match(violations.join("\n"), /'all'.*'ghost'/)
    assert.match(violations.join("\n"), /'all'.*'https:\/\/example.com\/maven'/)
  })

  it("rejects a member of a group that stores", () => {
    const violations = typeViolations({ repositories: [hosted, { id: "all", type: "GROUP", proxied: [{ reference: "releases", store: true }] }] })
    assert.match(violations.join("\n"), /stores nothing/)
  })

  it("names the path of a cycle through typed groups", () => {
    const violations = typeViolations({ repositories: [
      { id: "a", type: "GROUP", proxied: [{ reference: "b" }] },
      { id: "b", type: "GROUP", proxied: [{ reference: "a" }] },
    ] })
    assert.ok(violations.some(message => message.includes("a -> b -> a")), violations.join("\n"))
  })

  it("does not report a cycle between untyped repositories", () => {
    const settings = { repositories: [{ id: "a", proxied: [{ reference: "b" }] }, { id: "b", proxied: [{ reference: "a" }] }] }
    assert.deepEqual(typeViolations(settings), [])
  })

  it("formats a rejection the way the backend does", () => {
    const message = rejectionOf({ repositories: [{ id: "releases", type: "HOSTED", proxied: [external] }] })
    assert.ok(message.startsWith("Invalid maven settings: repository 'releases': type HOSTED"), message)
  })
})
