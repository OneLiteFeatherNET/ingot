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
const user = (entries = [], extra = {}) => ({ name: "alice", nodes: entries, ...extra })
const team = (name, entries = [], parents = [], extra = {}) => ({ name, nodes: entries, parents, ...extra })

const check = (node, { entries, memberTeams = [], teams, token = null, ...userExtra } = {}) =>
  nodes.evaluate({ node, user: user(entries, userExtra), memberTeams, teams: teams ?? memberTeams, token })

const READ = "ingot.repository.private.read"

describe("grammar", () => {
  for (const valid of [
    "*", "ingot.admin.settings", "ingot.admin.*", "ingot.repository.releases.deploy", "ingot.repository.*.read",
    "ingot.repository.my-repo_2.*", "ingot.repository.*.*", "ingot.repository.my.repo.read",
    "ingot.artifact.net.onelitefeather.read", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy",
    "ingot.artifact.*.read", "ingot.artifact.net.onelitefeather.*", "ingot.artifact.net:*.delete",
    "ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.read",
  ]) {
    it(`accepts ${valid}`, () => {
      assert.equal(nodes.problemOf(valid), null)
    })
  }

  it("reads a repository with dots by splitting at the last dot", () => {
    assert.equal(nodes.matches("ingot.repository.my.repo.read", "ingot.repository.my.repo.read"), true)
  })

  it("lets a repository wildcard match a repository with dots", () => {
    assert.equal(nodes.matches("ingot.repository.*.read", "ingot.repository.my.repo.read"), true)
  })

  it("rejects an empty node", () => {
    assert.equal(nodes.problemOf(""), "The node is empty")
  })

  it("rejects a node that is too long and shows its first 40 characters", () => {
    const long = `ingot.admin.${"a".repeat(300)}`
    assert.equal(nodes.problemOf(long), `The node '${long.slice(0, 40)}...' is longer than 255 characters`)
  })

  it("accepts a node of exactly the maximum length", () => {
    const prefix = "ingot.repository."
    const node = `${prefix}${"a".repeat(255 - prefix.length - ".read".length)}.read`
    assert.deepEqual([node.length, nodes.problemOf(node)], [255, null])
  })

  it("rejects whitespace", () => {
    assert.equal(nodes.problemOf("ingot.admin.users "), "The node 'ingot.admin.users ' contains whitespace or control characters")
  })

  it("rejects a control character", () => {
    assert.equal(nodes.problemOf("ingot.admin.\u0007users"), "The node 'ingot.admin.\u0007users' contains whitespace or control characters")
  })

  it("rejects a leading minus and says how a denial is stored", () => {
    assert.equal(nodes.problemOf("-ingot.admin.users"), "The node '-ingot.admin.users' starts with '-', negation is stored as allowed false and not in the name")
  })

  it("rejects a node outside the known prefixes", () => {
    assert.equal(nodes.problemOf("other.admin.settings"), "The node 'other.admin.settings' is unknown, nodes start with 'ingot.repository.', 'ingot.artifact.' or 'ingot.admin.', or are '*'")
  })

  it("rejects a repository node without a repository and an action", () => {
    assert.equal(nodes.problemOf("ingot.repository.releases"), "The repository node 'ingot.repository.releases' needs a repository and an action, for example ingot.repository.releases.read")
  })

  it("rejects an unknown repository action", () => {
    assert.equal(nodes.problemOf("ingot.repository.releases.write"), "The action 'write' of 'ingot.repository.releases.write' is unknown, supported: read, deploy, delete, manage or *")
  })

  it("rejects a partial wildcard in the repository", () => {
    assert.equal(nodes.problemOf("ingot.repository.rel*.read"), "The repository 'rel*' of 'ingot.repository.rel*.read' is invalid, a wildcard must be the whole repository segment")
  })

  it("rejects an empty part in the repository", () => {
    assert.match(nodes.problemOf("ingot.repository.a..b.read"), /^The repository 'a\.\.b' of /)
  })

  it("rejects an unknown admin area", () => {
    assert.equal(nodes.problemOf("ingot.admin.billing"), "The admin area 'billing' of 'ingot.admin.billing' is unknown, supported: settings, users, teams, tokens, maintenance, audit, console, dashboard or *")
  })

  it("checks the length before the whitespace", () => {
    assert.match(nodes.problemOf(` ${"a".repeat(300)}`), /is longer than 255 characters$/)
  })

  it("checks the whitespace before the leading minus", () => {
    assert.match(nodes.problemOf("- x"), /contains whitespace or control characters$/)
  })
})

describe("node lists", () => {
  it("lists every invalid node in one message, in list order", () => {
    const { message } = nodes.validateEntries([allow("ingot.re*"), allow("ingot.admin.users"), allow("-x")])
    assert.equal(message, "Invalid permission nodes: The node 'ingot.re*' is unknown, nodes start with 'ingot.repository.', 'ingot.artifact.' or 'ingot.admin.', or are '*'; The node '-x' starts with '-', negation is stored as allowed false and not in the name")
  })

  it("lists a node that is listed twice, sorted and comma separated", () => {
    const { message } = nodes.validateEntries([allow("ingot.admin.users"), allow("ingot.admin.audit"), deny("ingot.admin.users"), deny("ingot.admin.audit")])
    assert.equal(message, "Nodes listed twice: ingot.admin.audit, ingot.admin.users")
  })

  it("checks invalid nodes before duplicates", () => {
    const { message } = nodes.validateEntries([allow("x"), allow("x")])
    assert.match(message, /^Invalid permission nodes: /)
  })

  it("names the row of every invalid node as a problem, in list order", () => {
    const { problems } = nodes.validateEntries([allow("ingot.re*"), allow("ingot.admin.users"), allow("-x")])
    assert.deepEqual(problems, [
      { node: "ingot.re*", message: nodes.problemOf("ingot.re*") },
      { node: "-x", message: nodes.problemOf("-x") },
    ])
  })

  it("names the row of a node listed twice, once per node", () => {
    const { message, problems } = nodes.validateEntries([allow("*"), deny("*"), allow("ingot.admin.users"), deny("ingot.admin.users")])
    assert.equal(message, "Nodes listed twice: *, ingot.admin.users")
    assert.deepEqual(problems, [
      { node: "*", message: "Node '*' is listed twice" },
      { node: "ingot.admin.users", message: "Node 'ingot.admin.users' is listed twice" },
    ])
  })

  it("sends no problems for a valid list", () => {
    const result = nodes.validateEntries([allow("ingot.admin.users")])
    assert.equal(result.problems, undefined)
  })

  it("cannot read an entry without the allowed flag", () => {
    assert.equal(nodes.validateEntries([{ node: "ingot.admin.users" }]).message, "Failed to read body")
  })

  it("cannot read a list that is no list", () => {
    assert.equal(nodes.validateEntries("ingot.admin.users").message, "Failed to read body")
  })

  it("returns the cleaned entries of a valid list", () => {
    const { entries, message } = nodes.validateEntries([{ ...allow("ingot.admin.users"), extra: 1 }])
    assert.deepEqual([entries, message], [[allow("ingot.admin.users")], undefined])
  })
})

describe("matching", () => {
  it("lets a lone star match every node", () => {
    assert.equal(nodes.matches("*", READ), true)
  })

  it("lets an area wildcard match an area", () => {
    assert.equal(nodes.matches("ingot.admin.*", "ingot.admin.users"), true)
  })

  it("does not let an area wildcard match a repository node", () => {
    assert.equal(nodes.matches("ingot.admin.*", "ingot.repository.private.read"), false)
  })

  it("does not match a different literal", () => {
    assert.equal(nodes.matches("ingot.repository.releases.read", READ), false)
  })

  it("does not let a repository wildcard match an admin node", () => {
    assert.equal(nodes.matches("ingot.repository.*.*", "ingot.admin.users"), false)
  })

  it("counts a repository with dots as one literal part", () => {
    assert.equal(nodes.specificity("ingot.repository.my.repo.read"), 4)
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
    assert.deepEqual(result.decision, { source: "DEFAULT", subject: null, node: READ, allowed: false, depth: 0 })
  })

  it("explains the default with the contract phrase", () => {
    assert.equal(check(READ).trace[0].reason, "No node matches, denied by default")
  })

  it("does not allow anything for a subject without nodes, whatever the old role flag says", () => {
    const result = check(READ, { instanceRole: true })
    assert.deepEqual([result.allowed, result.decision.source], [false, "DEFAULT"])
  })

  it("allows everything for the star node", () => {
    const result = check(READ, { entries: [allow("*")] })
    assert.deepEqual([result.allowed, result.decision.source, result.decision.node], [true, "USER", "*"])
  })

  it("lists matches of lower layers in the trace", () => {
    const parent = team("parent", [allow("*")])
    const child = team("child", [deny(READ)], ["parent"])
    const result = check(READ, { entries: [allow(READ)], memberTeams: [child], teams: [child, parent] })
    assert.deepEqual(result.trace.map(entry => entry.source), ["USER", "TEAM", "INHERITED_TEAM"])
  })

  it("gives every trace entry the depth of its layer", () => {
    const parent = team("parent", [allow("*")])
    const child = team("child", [deny(READ)], ["parent"])
    const result = check(READ, { entries: [allow(READ)], memberTeams: [child], teams: [child, parent] })
    assert.deepEqual(result.trace.map(entry => entry.depth), [0, 0, 1])
  })

  it("carries exactly the fields of the contract in a trace entry", () => {
    const result = check(READ, { entries: [allow(READ)] })
    assert.deepEqual(Object.keys(result.trace[0]).sort(), ["allowed", "depth", "matched", "node", "reason", "source", "subject"])
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
  // Ids follow the order of the pairs, as the ids of created teams do.
  const graph = (...pairs) => pairs.map(([name, ...parents], index) => ({ id: index + 1, name, parents }))
  const cycle = (name) => `Inheritance of '${name}' would form a cycle: a team cannot inherit from itself, directly or through its parents`

  it("accepts a parent that creates no cycle", () => {
    assert.equal(nodes.checkParents(graph(["ops"], ["developers"]), "ops", ["developers"]), null)
  })

  it("detects a cycle and names the first team that meets itself", () => {
    const problem = nodes.checkParents(graph(["ops", "developers"], ["developers"]), "developers", ["ops"])
    assert.deepEqual(problem, { status: 409, message: cycle("ops") })
  })

  it("detects a team that would become its own parent", () => {
    assert.deepEqual(nodes.checkParents(graph(["ops"]), "ops", ["ops"]), { status: 409, message: cycle("ops") })
  })

  it("detects a cycle through several levels", () => {
    const problem = nodes.checkParents(graph(["a", "b"], ["b", "c"], ["c"]), "c", ["a"])
    assert.equal(problem.status, 409)
    assert.match(problem.message, /would form a cycle/)
  })

  // A chain t0 -> t1 -> ... where each team has the next one as parent.
  const chain = (length) => Array.from({ length }, (_, index) => ({ id: index + 1, name: `t${index}`, parents: index < length - 1 ? [`t${index + 1}`] : [] }))

  it("allows an inheritance depth of 8", () => {
    const teams = chain(9)
    teams[0] = { ...teams[0], parents: [] }
    assert.equal(nodes.checkParents(teams, "t0", ["t1"]), null)
  })

  it("rejects an inheritance depth of 9 and names the team and the depth", () => {
    const teams = chain(10)
    teams[0] = { ...teams[0], parents: [] }
    assert.deepEqual(nodes.checkParents(teams, "t0", ["t1"]), {
      status: 409,
      message: "Inheritance of 't0' would be 9 levels deep, the limit is 8 levels",
    })
  })

  it("counts the depth of descendants when a parent is added above them", () => {
    const teams = [...chain(11).slice(1), { id: 99, name: "top", parents: [] }]
    assert.equal(nodes.checkParents(teams, "t10", ["top"])?.status, 409)
  })
})

describe("admin detection", () => {
  it("is an instance admin with the admin node", () => {
    const context = { user: user([allow("ingot.admin.*")]), memberTeams: [], teams: [] }
    assert.equal(nodes.isInstanceAdmin(context), true)
  })

  it("is an instance admin through the star node", () => {
    const context = { user: user([allow("*")]), memberTeams: [], teams: [] }
    assert.equal(nodes.isInstanceAdmin(context), true)
  })

  it("is no instance admin with every area allowed one by one", () => {
    const context = { user: user(nodes.AREAS.map(area => allow(`ingot.admin.${area}`))), memberTeams: [], teams: [] }
    assert.equal(nodes.isInstanceAdmin(context), false)
  })

  it("is an instance admin through a team", () => {
    const context = { user: user(), memberTeams: [team("root", [allow("ingot.admin.*")])], teams: [team("root", [allow("ingot.admin.*")])] }
    assert.equal(nodes.isInstanceAdmin(context), true)
  })

  it("is no instance admin when a token without the admin node narrows the owner", () => {
    const context = { user: user([allow("ingot.admin.*")]), memberTeams: [], teams: [], token: { name: "t", nodes: [allow("ingot.repository.*.read")] } }
    assert.equal(nodes.isInstanceAdmin(context), false)
  })

  it("is no instance admin when the admin node is denied directly", () => {
    const context = { user: user([deny("ingot.admin.*")]), memberTeams: [team("root", [allow("ingot.admin.*")])], teams: [] }
    assert.equal(nodes.isInstanceAdmin(context), false)
  })
})

describe("effective nodes", () => {
  it("lists the allowed rule patterns and does not expand the catalog", () => {
    const context = { user: user([allow("ingot.admin.*")]), memberTeams: [], teams: [] }
    assert.deepEqual(nodes.effectiveNodes(context), ["ingot.admin.*"])
  })

  it("leaves out a pattern that is denied", () => {
    const context = { user: user([allow(READ), deny("ingot.repository.releases.read")]), memberTeams: [], teams: [] }
    assert.deepEqual(nodes.effectiveNodes(context), [READ])
  })

  it("lists each pattern once and sorted, from the user, the teams and the inherited teams", () => {
    const parent = team("parent", [allow("ingot.admin.audit"), allow(READ)])
    const child = team("child", [allow("ingot.admin.users")], ["parent"])
    const context = { user: user([allow(READ)]), memberTeams: [child], teams: [child, parent] }
    assert.deepEqual(nodes.effectiveNodes(context), ["ingot.admin.audit", "ingot.admin.users", READ])
  })

  it("leaves out a pattern the token does not allow", () => {
    const context = { user: user([allow(READ), allow("ingot.admin.users")]), memberTeams: [], teams: [], token: { name: "t", nodes: [allow(READ)] } }
    assert.deepEqual(nodes.effectiveNodes(context), [READ])
  })

  it("is empty without any node", () => {
    assert.deepEqual(nodes.effectiveNodes({ user: user(), memberTeams: [], teams: [] }), [])
  })
})

describe("catalog", () => {
  const catalog = nodes.buildCatalog(["releases", "private"])

  it("lists the areas, the two wildcards, each sorted repository, the action wildcards and the artifact wildcards in that order", () => {
    const expected = [
      ...nodes.AREAS.map(area => `ingot.admin.${area}`),
      "ingot.admin.*",
      "*",
      ...["private", "releases"].flatMap(repository => [...nodes.ACTIONS.map(action => `ingot.repository.${repository}.${action}`), `ingot.repository.${repository}.*`]),
      ...nodes.ACTIONS.map(action => `ingot.repository.*.${action}`),
      ...["read", "deploy", "delete"].map(action => `ingot.artifact.*.${action}`),
      "ingot.artifact.*.*",
    ]
    assert.deepEqual(catalog.map(entry => entry.node), expected)
  })

  it("describes the entries as the backend does", () => {
    const byNode = Object.fromEntries(catalog.map(entry => [entry.node, entry]))
    assert.deepEqual(
      [byNode["ingot.admin.audit"], byNode["ingot.admin.*"], byNode["*"], byNode["ingot.repository.private.deploy"], byNode["ingot.repository.private.*"], byNode["ingot.repository.*.manage"]],
      [
        { node: "ingot.admin.audit", description: "Administer audit", kind: "STATIC" },
        { node: "ingot.admin.*", description: "Every administrative area, as the instance administrator", kind: "WILDCARD" },
        { node: "*", description: "Everything: every repository action and every administrative area", kind: "WILDCARD" },
        { node: "ingot.repository.private.deploy", description: "deploy on repository private", kind: "REPOSITORY" },
        { node: "ingot.repository.private.*", description: "Every action on repository private", kind: "REPOSITORY" },
        { node: "ingot.repository.*.manage", description: "manage on every repository", kind: "WILDCARD" },
      ],
    )
  })

  it("has no entry for every action on every repository", () => {
    assert.equal(catalog.some(entry => entry.node === "ingot.repository.*.*"), false)
  })

  it("filters by prefix with startsWith", () => {
    const filtered = nodes.buildCatalog(["releases"], "ingot.repository.releases")
    assert.deepEqual(filtered.map(entry => entry.node), [...["read", "deploy", "delete", "manage"].map(action => `ingot.repository.releases.${action}`), "ingot.repository.releases.*"])
  })

  it("keeps everything for an empty prefix", () => {
    assert.deepEqual(nodes.buildCatalog(["releases", "private"], ""), catalog)
  })

  it("lists only valid nodes", () => {
    assert.deepEqual(catalog.filter(entry => nodes.problemOf(entry.node) !== null), [])
  })

  it("is the same on every call", () => {
    assert.deepEqual(nodes.buildCatalog(["releases", "private"]), catalog)
  })
})

describe("templates", () => {
  it("lists the eight templates in the order of the backend", () => {
    assert.deepEqual(nodes.templates().map(template => template.id), [
      "read", "deploy", "maintain", "admin", "instance-admin", "artifact-read", "artifact-deploy", "repository-artifact-deploy",
    ])
  })

  it("labels the templates as the backend does", () => {
    assert.deepEqual(nodes.templates().map(template => template.label), [
      "Read", "Deploy", "Maintain", "Admin", "Instance admin",
      "Read an artifact", "Deploy an artifact", "Deploy an artifact in one repository",
    ])
  })

  it("keeps the groupId and artifactId placeholders of the artifact templates", () => {
    const byId = Object.fromEntries(nodes.templates().map(template => [template.id, template]))
    assert.deepEqual(byId["artifact-read"].nodes, ["ingot.artifact.{groupId}:{artifactId}.read"])
    assert.deepEqual(byId["artifact-deploy"].nodes, ["ingot.artifact.{groupId}:{artifactId}.read", "ingot.artifact.{groupId}:{artifactId}.deploy"])
    assert.deepEqual(byId["repository-artifact-deploy"].nodes, [
      "ingot.repository.{repository}.artifact.{groupId}:{artifactId}.read",
      "ingot.repository.{repository}.artifact.{groupId}:{artifactId}.deploy",
    ])
  })

  it("keeps the repository placeholder for the client", () => {
    assert.deepEqual(nodes.templates()[1].nodes, ["ingot.repository.{repository}.read", "ingot.repository.{repository}.deploy"])
  })

  it("hands out a copy that a caller cannot use to change the templates", () => {
    nodes.templates()[0].nodes.push("x")
    assert.equal(nodes.templates()[0].nodes.length, 1)
  })
})

describe("artifact nodes", () => {
  const ARTIFACT = "ingot.artifact.net.onelitefeather:mycelium-bom.read"

  it("reads a qualified node into its repository, groupId, artifactId and action", () => {
    assert.equal(nodes.problemOf("ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy"), null)
  })

  it("names the artifact a request is about in its problem, and the form that is refused", () => {
    assert.match(nodes.problemOf("ingot.artifact.net.onelitefeather.manage"), /The action 'manage' of .* is unknown for an artifact node/)
    assert.match(nodes.problemOf("ingot.artifact.*:mycelium-bom.read"), /ambiguous/)
    assert.match(nodes.problemOf("ingot.artifact.net.*.read"), /wildcard must be the whole groupId/)
    assert.match(nodes.problemOf("ingot.artifact.net:a:b.read"), /only one ':'/)
    assert.match(nodes.problemOf("ingot.artifact.net:.read"), /artifactId of .* is empty/)
    assert.match(nodes.problemOf("ingot.repository.*.artifact.net.read"), /repository wildcard of .* is not allowed/)
    assert.match(nodes.problemOf("ingot.repository.artifact.net.read"), /repository name before .artifact. is missing/)
    assert.match(nodes.problemOf("ingot.artifact.net"), /needs a groupId and an action/)
  })

  it("matches an artifact request inside the group and not a sibling group", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.read", ARTIFACT), true)
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.read", "ingot.artifact.net.onelitefeatherX.read"), false)
  })

  it("matches a subgroup of the covered group", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.read", "ingot.artifact.net.onelitefeather.tools:cli.read"), true)
  })

  it("covers one artifact only when it names an artifactId", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather:mycelium-bom.deploy", "ingot.artifact.net.onelitefeather:mycelium-api.deploy"), false)
  })

  it("does not cover a group-only request with an artifactId pattern", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather:mycelium-bom.read", "ingot.artifact.net.onelitefeather.read"), false)
  })

  it("covers every repository with the unqualified form and only its repository with the qualified form", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.read", "ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.read"), true)
    assert.equal(nodes.matches("ingot.repository.releases.artifact.net.onelitefeather.read", "ingot.artifact.net.onelitefeather:mycelium-bom.read"), false)
    assert.equal(nodes.matches("ingot.repository.releases.artifact.net.onelitefeather.read", "ingot.repository.snapshots.artifact.net.onelitefeather:mycelium-bom.read"), false)
  })

  it("does not let an artifact node match a repository request, nor a repository node match an artifact request", () => {
    assert.equal(nodes.matches("ingot.artifact.*.read", "ingot.repository.releases.read"), false)
    assert.equal(nodes.matches("ingot.repository.releases.read", ARTIFACT), false)
  })

  it("matches the action or every action with the star", () => {
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.*", "ingot.artifact.net.onelitefeather:mycelium-bom.delete"), true)
    assert.equal(nodes.matches("ingot.artifact.net.onelitefeather.deploy", ARTIFACT), false)
  })

  it("ranks an artifactId above a group, a longer group above a shorter one and a qualified node above an unqualified one", () => {
    const ranks = [
      "ingot.artifact.net.onelitefeather.read",
      "ingot.artifact.net.onelitefeather.*",
      "ingot.artifact.net.onelitefeather:mycelium-bom.read",
      "ingot.artifact.net.onelitefeather:mycelium-bom.*",
      "ingot.repository.releases.artifact.net.onelitefeather.read",
    ].map(nodes.specificity)
    assert.deepEqual(ranks, [...ranks].sort((left, right) => left - right).map((_, index) => ranks[index]))
    assert.equal(nodes.specificity("ingot.artifact.net.onelitefeather.read") > nodes.specificity("ingot.artifact.net.read"), true)
    assert.equal(nodes.specificity("ingot.artifact.net.onelitefeather.read") > nodes.specificity("ingot.artifact.net.onelitefeather.*"), true)
    assert.equal(nodes.specificity("ingot.artifact.net.onelitefeather:mycelium-bom.*") > nodes.specificity("ingot.artifact.net.onelitefeather.read"), true)
    assert.equal(nodes.specificity("ingot.repository.releases.artifact.net.read") > nodes.specificity("ingot.artifact.net.onelitefeather.read"), true)
  })

  it("lets a deny of a more specific artifact node win over the group allowance", () => {
    const result = nodes.evaluate({
      node: "ingot.artifact.net.onelitefeather:internal.deploy",
      user: { name: "alice", nodes: [{ node: "ingot.artifact.net.onelitefeather.deploy", allowed: true }, { node: "ingot.artifact.net.onelitefeather:internal.deploy", allowed: false }] },
    })
    assert.equal(result.allowed, false)
    assert.equal(result.decision.node, "ingot.artifact.net.onelitefeather:internal.deploy")
  })

  it("allows an artifact the owner may deploy and denies a sibling", () => {
    const user = { name: "alice", nodes: [{ node: "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", allowed: true }] }
    assert.equal(nodes.evaluate({ node: "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", user }).allowed, true)
    assert.equal(nodes.evaluate({ node: "ingot.artifact.net.onelitefeather:mycelium-api.deploy", user }).allowed, false)
  })

  it("lists the artifact nodes of the catalog as valid nodes", () => {
    const catalogNodes = nodes.buildCatalog(["releases"]).filter(entry => entry.kind === "ARTIFACT").map(entry => entry.node)
    assert.deepEqual(catalogNodes, ["ingot.artifact.*.read", "ingot.artifact.*.deploy", "ingot.artifact.*.delete", "ingot.artifact.*.*"])
  })
})
