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
 * Tests of the pure permission node logic of the fake backend. Vitest cannot load a CommonJS test
 * file (it refuses require("vitest")), so these use the test runner that ships with Node:
 *   node --test fake-api/nodes.test.cjs
 * Every test builds its own data and nothing reads a clock, so the order does not matter.
 */

const { describe, it } = require("node:test")
const assert = require("node:assert/strict")
const nodes = require("./nodes.cjs")

const allow = (node) => ({ node, allowed: true })
const deny = (node) => ({ node, allowed: false })
const user = (entries = [], extra = {}) => ({ name: "alice", nodes: entries, instanceRole: false, ...extra })
const team = (name, entries = [], parents = [], extra = {}) => ({ name, nodes: entries, parents, instanceRole: false, ...extra })

const check = (node, { entries, memberTeams = [], teams, token = null, ...userExtra } = {}) =>
  nodes.evaluate({ node, user: user(entries, userExtra), memberTeams, teams: teams ?? memberTeams, token })

const READ = "ingot.repository.private.read"

describe("grammar", () => {
  for (const valid of ["*", "ingot.admin.settings", "ingot.admin.*", "ingot.repository.releases.deploy", "ingot.repository.*.read", "ingot.repository.my-repo_2.*", "ingot.repository.*.*"]) {
    it(`accepts ${valid}`, () => {
      assert.deepEqual(nodes.validateNode(valid), [])
    })
  }

  it("rejects a partial wildcard such as ingot.re*", () => {
    assert.equal(nodes.validateNode("ingot.re*").length, 1)
  })

  it("rejects an unknown admin area", () => {
    assert.match(nodes.validateNode("ingot.admin.billing")[0], /Unknown admin area 'billing'/)
  })

  it("rejects an unknown repository action with the hint for the shape", () => {
    assert.match(nodes.validateNode("ingot.repository.releases.write")[0], /Use ingot.repository.<repository>.<read\|deploy\|delete\|manage>/)
  })

  it("rejects a repository node without an action", () => {
    assert.deepEqual(nodes.validateNode("ingot.repository.releases"), ["Use ingot.repository.<repository>.<read|deploy|delete|manage>"])
  })

  it("rejects a node outside the ingot namespace", () => {
    assert.equal(nodes.validateNode("other.admin.settings").length, 1)
  })

  it("rejects an empty segment", () => {
    assert.equal(nodes.validateNode("ingot..settings").length, 1)
  })

  it("rejects a blank node", () => {
    assert.deepEqual(nodes.validateNode("  "), ["Node is required"])
  })

  it("reports each invalid entry with its node", () => {
    const { problems } = nodes.validateEntries([allow("ingot.re*"), allow("ingot.admin.users")])
    assert.deepEqual(problems.map(problem => problem.node), ["ingot.re*"])
  })

  it("reports a missing allowed flag", () => {
    const { problems } = nodes.validateEntries([{ node: "ingot.admin.users" }])
    assert.equal(problems[0].message, "allowed must be true or false")
  })

  it("reports a node that is listed twice", () => {
    const { problems } = nodes.validateEntries([allow("ingot.admin.users"), deny("ingot.admin.users")])
    assert.equal(problems[0].message, "Node is listed twice")
  })

  it("returns the cleaned entries of a valid list", () => {
    const { entries, problems } = nodes.validateEntries([allow("ingot.admin.users")])
    assert.deepEqual([entries, problems], [[allow("ingot.admin.users")], []])
  })
})

describe("kind", () => {
  it("calls a literal repository node REPOSITORY", () => assert.equal(nodes.kindOf("ingot.repository.releases.read"), "REPOSITORY"))
  it("calls an admin area STATIC", () => assert.equal(nodes.kindOf("ingot.admin.audit"), "STATIC"))
  it("calls the admin wildcard WILDCARD", () => assert.equal(nodes.kindOf("ingot.admin.*"), "WILDCARD"))
  it("calls a repository wildcard WILDCARD", () => assert.equal(nodes.kindOf("ingot.repository.*.read"), "WILDCARD"))
  it("calls a lone star WILDCARD", () => assert.equal(nodes.kindOf("*"), "WILDCARD"))
})

describe("matching", () => {
  it("lets a lone star match every node", () => {
    assert.equal(nodes.matches("*", READ), true)
  })

  it("lets a trailing star match one segment", () => {
    assert.equal(nodes.matches("ingot.admin.*", "ingot.admin.users"), true)
  })

  it("does not let a trailing star match a node with more segments", () => {
    assert.equal(nodes.matches("ingot.admin.*", "ingot.repository.private.read"), false)
  })

  it("does not match a different literal", () => {
    assert.equal(nodes.matches("ingot.repository.releases.read", READ), false)
  })

  it("counts the literal segments as specificity", () => {
    assert.deepEqual(["*", "ingot.admin.*", "ingot.repository.private.*", READ].map(nodes.specificity), [0, 2, 3, 4])
  })
})

describe("evaluation", () => {
  it("lets a user node beat a team node", () => {
    const result = check(READ, { entries: [allow(READ)], memberTeams: [team("dev", [deny(READ)])] })
    assert.deepEqual([result.allowed, result.decision.source, result.decision.subject], [true, "USER", "alice"])
  })

  it("explains a user win over a team with the contract phrase", () => {
    const result = check(READ, { entries: [allow(READ)], memberTeams: [team("dev", [deny(READ)])] })
    assert.equal(result.trace[0].reason, "User node beats team node")
  })

  it("marks the team match below a user decision as not consulted", () => {
    const result = check(READ, { entries: [allow(READ)], memberTeams: [team("dev", [deny(READ)])] })
    assert.equal(result.trace[1].reason, "Not consulted: a higher layer already decided")
  })

  it("lets a team node beat an inherited team node", () => {
    const parent = team("parent", [deny(READ)])
    const child = team("child", [allow(READ)], ["parent"])
    const result = check(READ, { memberTeams: [child], teams: [child, parent] })
    assert.deepEqual([result.allowed, result.decision.source, result.decision.subject], [true, "TEAM", "child"])
  })

  it("explains a team win over an inherited team with the contract phrase", () => {
    const parent = team("parent", [deny(READ)])
    const child = team("child", [allow(READ)], ["parent"])
    const result = check(READ, { memberTeams: [child], teams: [child, parent] })
    assert.equal(result.trace[0].reason, "Team node beats inherited team node")
  })

  it("consults the nearest inherited team first", () => {
    const far = team("far", [deny(READ)])
    const near = team("near", [allow(READ)], ["far"])
    const child = team("child", [], ["near"])
    const result = check(READ, { memberTeams: [child], teams: [child, near, far] })
    assert.deepEqual([result.allowed, result.decision.subject, result.decision.depth], [true, "near", 1])
  })

  it("reports the distance of an inherited decision", () => {
    const far = team("far", [allow(READ)])
    const near = team("near", [], ["far"])
    const child = team("child", [], ["near"])
    const result = check(READ, { memberTeams: [child], teams: [child, near, far] })
    assert.deepEqual([result.decision.source, result.decision.depth], ["INHERITED_TEAM", 2])
  })

  it("lets a specific node beat a wildcard in the same layer", () => {
    const result = check(READ, { entries: [deny("ingot.repository.*.*"), allow(READ)] })
    assert.equal(result.allowed, true)
  })

  it("explains a specific win with the contract phrase", () => {
    const result = check(READ, { entries: [deny("ingot.repository.*.*"), allow(READ)] })
    assert.equal(result.trace.find(entry => entry.node === READ).reason, "Specific beats wildcard")
  })

  it("lets a deny win when equally specific nodes disagree", () => {
    const result = check(READ, { entries: [allow("ingot.repository.private.*"), deny("ingot.repository.*.read")] })
    assert.equal(result.allowed, false)
  })

  it("explains a tie with the contract phrase", () => {
    const result = check(READ, { entries: [allow("ingot.repository.private.*"), deny("ingot.repository.*.read")] })
    assert.equal(result.trace.find(entry => entry.matched).reason, "Deny wins on tie")
  })

  it("lets a deny of one team win a tie against an allow of another team", () => {
    const result = check(READ, { memberTeams: [team("a", [allow(READ)]), team("b", [deny(READ)])] })
    assert.deepEqual([result.allowed, result.decision.subject], [false, "b"])
  })

  it("denies by default when no node matches", () => {
    const result = check(READ, { entries: [allow("ingot.admin.users")] })
    assert.deepEqual(result.decision, { source: "DEFAULT", subject: null, node: null, allowed: false, depth: null })
  })

  it("explains the default with the contract phrase", () => {
    assert.equal(check(READ).trace[0].reason, "No node matches, denied by default")
  })

  it("allows everything for a legacy instance admin without nodes", () => {
    const result = check(READ, { instanceRole: true })
    assert.deepEqual([result.allowed, result.decision.source], [true, "LEGACY"])
  })

  it("allows everything for a member of a legacy admin team", () => {
    const result = check(READ, { memberTeams: [team("root", [], [], { instanceRole: true })] })
    assert.deepEqual([result.decision.source, result.decision.subject], ["LEGACY", "root"])
  })

  it("lets a team node beat the legacy grant", () => {
    const result = check(READ, { instanceRole: true, memberTeams: [team("dev", [deny(READ)])] })
    assert.deepEqual([result.allowed, result.trace[0].reason], [false, "Team node beats legacy grant"])
  })

  it("lists matches of lower layers in the trace", () => {
    const result = check(READ, { entries: [allow(READ)], memberTeams: [team("dev", [deny(READ)])], instanceRole: true })
    assert.deepEqual(result.trace.map(entry => entry.source), ["USER", "TEAM", "LEGACY"])
  })

  it("does not count a team reachable at distance zero as inherited", () => {
    const parent = team("parent", [deny(READ)])
    const child = team("child", [], ["parent"])
    const result = check(READ, { memberTeams: [child, parent], teams: [child, parent] })
    assert.equal(result.decision.source, "TEAM")
  })

  it("terminates when the stored parents contain a cycle", () => {
    const a = team("a", [], ["b"])
    const b = team("b", [allow(READ)], ["a"])
    assert.equal(check(READ, { memberTeams: [a], teams: [a, b] }).allowed, true)
  })
})

describe("token narrowing", () => {
  const principalAllows = { entries: [allow("ingot.repository.*.*")] }

  it("does not narrow with an empty token node list", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [] } })
    assert.equal(result.allowed, true)
  })

  it("denies when the token has nodes but none match", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [allow("ingot.repository.releases.read")] } })
    assert.deepEqual([result.allowed, result.decision.source, result.decision.subject], [false, "TOKEN", "t"])
  })

  it("explains a token without a match with the contract phrase", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [allow("ingot.repository.releases.read")] } })
    assert.equal(result.trace[result.trace.length - 1].reason, "Token node list does not include it")
  })

  it("allows when the token lists the node", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [allow(READ)] } })
    assert.deepEqual([result.allowed, result.decision.source], [true, "USER"])
  })

  it("explains a token that lists the node with the contract phrase", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [allow(READ)] } })
    assert.equal(result.trace[result.trace.length - 1].reason, "Token node allows it")
  })

  it("denies when the token denies the node explicitly", () => {
    const result = check(READ, { ...principalAllows, token: { name: "t", nodes: [deny(READ)] } })
    assert.deepEqual([result.allowed, result.decision.source], [false, "TOKEN"])
  })

  it("keeps the principal decision when the principal denies and the token allows", () => {
    const result = check(READ, { entries: [deny(READ)], token: { name: "t", nodes: [allow(READ)] } })
    assert.deepEqual([result.allowed, result.decision.source], [false, "USER"])
  })

  it("never lets a token widen what the principal may do", () => {
    const result = check(READ, { token: { name: "t", nodes: [allow("*")] } })
    assert.equal(result.allowed, false)
  })
})

describe("team parents", () => {
  const graph = (...pairs) => pairs.map(([name, ...parents]) => ({ name, parents }))

  it("accepts a parent that creates no cycle", () => {
    assert.equal(nodes.checkParents(graph(["ops"], ["developers"]), "ops", ["developers"]), null)
  })

  it("detects a cycle and names the path", () => {
    const problem = nodes.checkParents(graph(["ops", "developers"], ["developers"]), "developers", ["ops"])
    assert.deepEqual(problem, {
      status: 409,
      message: "Adding ops as a parent of developers would create a cycle: developers -> ops -> developers",
    })
  })

  it("detects a team that would become its own parent", () => {
    assert.match(nodes.checkParents(graph(["ops"]), "ops", ["ops"]).message, /would create a cycle: ops -> ops$/)
  })

  it("detects a cycle through several levels", () => {
    const problem = nodes.checkParents(graph(["a", "b"], ["b", "c"], ["c"]), "c", ["a"])
    assert.match(problem.message, /c -> a -> b -> c$/)
  })

  // A chain t0 -> t1 -> ... where each team has the next one as parent.
  const chain = (length) => Array.from({ length }, (_, index) => ({ name: `t${index}`, parents: index < length - 1 ? [`t${index + 1}`] : [] }))

  it("allows an inheritance depth of 5", () => {
    const teams = chain(6)
    teams[0] = { name: "t0", parents: [] }
    assert.equal(nodes.checkParents(teams, "t0", ["t1"]), null)
  })

  it("rejects an inheritance depth of 6", () => {
    const teams = chain(7)
    teams[0] = { name: "t0", parents: [] }
    assert.deepEqual(nodes.checkParents(teams, "t0", ["t1"]), { status: 409, message: "Inheritance deeper than 5 levels is not allowed" })
  })

  it("counts the depth of descendants when a parent is added above them", () => {
    const teams = [...chain(7).slice(1), { name: "top", parents: [] }]
    assert.equal(nodes.checkParents(teams, "t6", ["top"])?.status, 409)
  })
})

describe("admin detection", () => {
  it("holds every area with the admin wildcard", () => {
    const context = { user: user([allow("ingot.admin.*")]), memberTeams: [], teams: [] }
    assert.equal(nodes.holdsEveryArea(context), true)
  })

  it("does not hold every area when one is denied", () => {
    const context = { user: user([allow("ingot.admin.*"), deny("ingot.admin.audit")]), memberTeams: [], teams: [] }
    assert.equal(nodes.holdsEveryArea(context), false)
  })
})

describe("catalog", () => {
  const catalog = nodes.buildCatalog(["releases", "private"])

  it("starts with the three wildcards", () => {
    assert.deepEqual(catalog.slice(0, 3).map(entry => entry.node), ["*", "ingot.admin.*", "ingot.repository.*.*"])
  })

  it("lists every admin area", () => {
    assert.equal(catalog.filter(entry => entry.kind === "STATIC").length, nodes.AREAS.length)
  })

  it("lists the four actions of each repository", () => {
    assert.equal(catalog.filter(entry => entry.kind === "REPOSITORY").length, 8)
  })

  it("filters by prefix", () => {
    const filtered = nodes.buildCatalog(["releases"], "ingot.repository.releases")
    assert.deepEqual(filtered.map(entry => entry.node), ["read", "deploy", "delete", "manage"].map(action => `ingot.repository.releases.${action}`))
  })

  it("lists only valid nodes", () => {
    assert.deepEqual(catalog.filter(entry => nodes.validateNode(entry.node).length > 0), [])
  })

  it("is the same on every call", () => {
    assert.deepEqual(nodes.buildCatalog(["releases", "private"]), catalog)
  })
})

describe("templates", () => {
  it("lists the five templates in order", () => {
    assert.deepEqual(nodes.templates().map(template => template.id), ["read", "deploy", "maintain", "admin", "instance-admin"])
  })

  it("keeps the repository placeholder for the client", () => {
    assert.deepEqual(nodes.templates()[1].nodes, ["ingot.repository.{repository}.read", "ingot.repository.{repository}.deploy"])
  })

  it("hands out a copy that a caller cannot use to change the templates", () => {
    nodes.templates()[0].nodes.push("x")
    assert.equal(nodes.templates()[0].nodes.length, 1)
  })
})
