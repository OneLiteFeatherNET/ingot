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
 * Permission nodes for the fake backend (ADR-INGT-0003): the grammar, the matcher, the layered
 * evaluation with its trace, token narrowing, the team parent checks, the node catalog and the
 * templates. Pure functions on plain data, no Express and no clock, so the output only depends on
 * the input.
 */

const MAX_TEAM_DEPTH = 5

const AREAS = ["settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard"]
const ACTIONS = ["read", "deploy", "delete", "manage"]

const SOURCE = {
  USER: "USER", TEAM: "TEAM", INHERITED_TEAM: "INHERITED_TEAM", LEGACY: "LEGACY", DEFAULT: "DEFAULT", TOKEN: "TOKEN",
}

const SEGMENT = /^(\*|[A-Za-z0-9_-]+)$/
const WILDCARD_HINT = "Segments may only hold letters, digits, '_' and '-', and a '*' must be a whole segment"
const REPOSITORY_HINT = `Use ingot.repository.<repository>.<${ACTIONS.join("|")}>`
const ADMIN_HINT = `Use ingot.admin.<${AREAS.join("|")}|*>`
const SHAPE_HINT = "Use *, ingot.admin.<area> or ingot.repository.<repository>.<action>"

// ---------------------------------------------------------------- grammar

/** The problems of a node as a list of messages. An empty list means the node is valid. */
const validateNode = (node) => {
  if (typeof node !== "string" || node.trim() === "") return ["Node is required"]
  if (node !== node.trim()) return ["Node must not start or end with a space"]
  const segments = node.split(".")
  if (segments.some(segment => !SEGMENT.test(segment))) return [WILDCARD_HINT]
  if (node === "*") return []
  if (segments[0] !== "ingot") return [SHAPE_HINT]
  if (segments[1] === "admin") {
    if (segments.length !== 3) return [ADMIN_HINT]
    return segments[2] === "*" || AREAS.includes(segments[2]) ? [] : [`Unknown admin area '${segments[2]}'. ${ADMIN_HINT}`]
  }
  if (segments[1] === "repository") {
    if (segments.length !== 4) return [REPOSITORY_HINT]
    return segments[3] === "*" || ACTIONS.includes(segments[3]) ? [] : [`Unknown action '${segments[3]}'. ${REPOSITORY_HINT}`]
  }
  return [SHAPE_HINT]
}

/** WILDCARD, STATIC (admin area) or REPOSITORY (a literal repository). Only meaningful for a valid node. */
const kindOf = (node) => {
  const segments = node.split(".")
  if (node === "*" || segments.includes("*") && segments[1] === "admin") return "WILDCARD"
  if (segments[1] === "admin") return "STATIC"
  return segments[2] === "*" || segments[3] === "*" ? "WILDCARD" : "REPOSITORY"
}

/**
 * Checks a list of { node, allowed } entries. Returns the cleaned entries and the problems as
 * [{ node, message }]. A node that is listed twice is a problem, so that the list stays unambiguous.
 */
const validateEntries = (entries) => {
  if (!Array.isArray(entries)) return { entries: [], problems: [{ node: null, message: "Nodes must be a list of { node, allowed }" }] }
  const problems = []
  const seen = new Set()
  const cleaned = []
  for (const entry of entries) {
    const candidate = entry && typeof entry === "object" ? entry : {}
    const shown = typeof candidate.node === "string" ? candidate.node : null
    const nodeProblems = validateNode(candidate.node)
    nodeProblems.forEach(message => problems.push({ node: shown, message }))
    if (typeof candidate.allowed !== "boolean") problems.push({ node: shown, message: "allowed must be true or false" })
    if (nodeProblems.length === 0 && seen.has(candidate.node)) problems.push({ node: shown, message: "Node is listed twice" })
    if (nodeProblems.length === 0 && typeof candidate.allowed === "boolean" && !seen.has(candidate.node)) {
      cleaned.push({ node: candidate.node, allowed: candidate.allowed })
    }
    if (nodeProblems.length === 0) seen.add(candidate.node)
  }
  return { entries: cleaned, problems }
}

// ---------------------------------------------------------------- matching

/** A pattern matches a node when it is '*' alone, or has as many segments and each equals or is '*'. */
const matches = (pattern, node) => {
  if (pattern === "*") return true
  const patternSegments = pattern.split(".")
  const nodeSegments = node.split(".")
  return patternSegments.length === nodeSegments.length &&
    patternSegments.every((segment, index) => segment === "*" || segment === nodeSegments[index])
}

/** The number of literal segments of a pattern. '*' alone has none. */
const specificity = (pattern) => pattern === "*" ? 0 : pattern.split(".").filter(segment => segment !== "*").length

// ---------------------------------------------------------------- evaluation

const HIGHER_LABEL = { USER: "User node", TEAM: "Team node", INHERITED_TEAM: "Inherited team node", LEGACY: "Legacy grant" }
const LOWER_LABEL = { TEAM: "team node", INHERITED_TEAM: "inherited team node", LEGACY: "legacy grant" }

const REASON = {
  tie: "Deny wins on tie",
  specific: "Specific beats wildcard",
  only: "Only match",
  notConsulted: "Not consulted: a higher layer already decided",
  tokenDenied: "Token node list does not include it",
  tokenAllowed: "Token node allows it",
  tokenDeniedExplicit: "Token node denies it",
  default: "No node matches, denied by default",
}

const same = (left, right) => String(left).toLowerCase() === String(right).toLowerCase()

/** The parents of a team that exist, as team objects. */
const parentsOf = (team, teams) =>
  (team.parents ?? []).map(name => teams.find(other => same(other.name, name))).filter(Boolean)

/**
 * The ancestors of the given teams by distance (1 is one step up). A team that is itself in the
 * starting set, or was met nearer, is not listed again. Returns [{ team, distance }] ordered by
 * distance and then by the order of discovery.
 */
const ancestorsOf = (starting, teams) => {
  const seen = new Set(starting.map(team => team.name.toLowerCase()))
  const found = []
  let frontier = starting
  for (let distance = 1; frontier.length > 0; distance++) {
    const next = []
    for (const team of frontier) {
      for (const parent of parentsOf(team, teams)) {
        if (seen.has(parent.name.toLowerCase())) continue
        seen.add(parent.name.toLowerCase())
        found.push({ team: parent, distance })
        next.push(parent)
      }
    }
    frontier = next
  }
  return found
}

/** All entries of a list that match the node, as candidates of one layer. */
const candidatesOf = (source, subject, nodes, node, depth) =>
  (nodes ?? [])
    .filter(entry => matches(entry.node, node))
    .map(entry => ({ source, subject, node: entry.node, allowed: entry.allowed, depth, specificity: specificity(entry.node) }))

/**
 * Decides one layer. Returns null without candidates. Otherwise the winner (the first of the
 * deciding candidates), the verdict, the candidates that decided and the reason for each candidate.
 */
const decideLayer = (candidates) => {
  if (candidates.length === 0) return null
  const top = Math.max(...candidates.map(candidate => candidate.specificity))
  const best = candidates.filter(candidate => candidate.specificity === top)
  const tie = best.some(candidate => candidate.allowed) && best.some(candidate => !candidate.allowed)
  const verdict = tie ? false : best[0].allowed
  const deciding = best.filter(candidate => candidate.allowed === verdict)
  const outranked = candidates.some(candidate => candidate.specificity < top && candidate.allowed !== verdict)
  return { winner: deciding[0], verdict, deciding, tie, outranked, best, top }
}

/** Evaluates the matches of the user, team, inherited team and legacy layers for a node. */
const evaluatePrincipal = ({ node, user, memberTeams, teams }) => {
  const inherited = ancestorsOf(memberTeams, teams)
  const maxDistance = inherited.reduce((most, entry) => Math.max(most, entry.distance), 0)
  const layers = [
    { source: SOURCE.USER, candidates: candidatesOf(SOURCE.USER, user.name, user.nodes, node, 0) },
    { source: SOURCE.TEAM, candidates: memberTeams.flatMap(team => candidatesOf(SOURCE.TEAM, team.name, team.nodes, node, 0)) },
  ]
  for (let distance = 1; distance <= maxDistance; distance++) {
    layers.push({
      source: SOURCE.INHERITED_TEAM,
      candidates: inherited.filter(entry => entry.distance === distance)
        .flatMap(entry => candidatesOf(SOURCE.INHERITED_TEAM, entry.team.name, entry.team.nodes, node, distance)),
    })
  }
  const legacySubject = user.instanceRole ? user.name : memberTeams.find(team => team.instanceRole)?.name
  layers.push({
    source: SOURCE.LEGACY,
    candidates: legacySubject === undefined ? [] : [{ source: SOURCE.LEGACY, subject: legacySubject, node: "*", allowed: true, depth: 0, specificity: 0 }],
  })
  return decideLayers(layers)
}

/** The first layer with a candidate decides; every candidate of every layer gets a trace entry. */
const decideLayers = (layers) => {
  const deciding = layers.findIndex(layer => layer.candidates.length > 0)
  const trace = []
  if (deciding < 0) {
    return {
      decision: { source: SOURCE.DEFAULT, subject: null, node: null, allowed: false, depth: null },
      trace: [{ source: SOURCE.DEFAULT, subject: null, node: null, allowed: false, matched: true, reason: REASON.default }],
    }
  }
  const layer = layers[deciding]
  const result = decideLayer(layer.candidates)
  const lower = layers.slice(deciding + 1).filter(other => other.candidates.length > 0)
  const crossReason = () => {
    const other = lower.find(candidate => candidate.source !== layer.source)
    if (other) return `${HIGHER_LABEL[layer.source]} beats ${LOWER_LABEL[other.source]}`
    if (lower.length > 0) return "Nearer inherited team node beats farther inherited team node"
    return REASON.only
  }
  const winnerReason = result.tie ? REASON.tie : result.outranked ? REASON.specific : crossReason()
  for (const candidate of layer.candidates) {
    const isDeciding = result.deciding.includes(candidate)
    let reason
    if (isDeciding) reason = winnerReason
    else if (candidate.specificity === result.top) reason = REASON.tie
    else reason = REASON.specific
    trace.push(entryOf(candidate, isDeciding, reason))
  }
  for (const other of lower) {
    for (const candidate of other.candidates) trace.push(entryOf(candidate, false, REASON.notConsulted))
  }
  const winner = result.winner
  return {
    decision: { source: winner.source, subject: winner.subject, node: winner.node, allowed: result.verdict, depth: winner.depth },
    trace,
  }
}

const entryOf = (candidate, matched, reason) =>
  ({ source: candidate.source, subject: candidate.subject, node: candidate.node, allowed: candidate.allowed, matched, reason })

/**
 * Evaluates a node for a principal.
 *   user:        { name, nodes, instanceRole }
 *   memberTeams: the teams the principal belongs to ({ name, nodes, parents, instanceRole })
 *   teams:       every team, to follow the parents
 *   token:       null, or { name, nodes }. A token without nodes does not narrow.
 * Returns { allowed, decision, trace }.
 */
const evaluate = ({ node, user, memberTeams = [], teams = [], token = null }) => {
  const principal = evaluatePrincipal({ node, user, memberTeams, teams })
  if (!token || !token.nodes || token.nodes.length === 0) {
    return { allowed: principal.decision.allowed, decision: principal.decision, trace: principal.trace }
  }
  const candidates = candidatesOf(SOURCE.TOKEN, token.name, token.nodes, node, 0)
  if (!principal.decision.allowed) {
    const skipped = candidates.map(candidate => entryOf(candidate, false, REASON.notConsulted))
    return { allowed: false, decision: principal.decision, trace: [...principal.trace, ...skipped] }
  }
  const result = decideLayer(candidates)
  if (result === null) {
    const decision = { source: SOURCE.TOKEN, subject: token.name, node: null, allowed: false, depth: 0 }
    const entry = { source: SOURCE.TOKEN, subject: token.name, node: null, allowed: false, matched: true, reason: REASON.tokenDenied }
    return { allowed: false, decision, trace: [...principal.trace, entry] }
  }
  const tokenReason = result.verdict ? REASON.tokenAllowed : REASON.tokenDeniedExplicit
  const winnerReason = result.tie ? REASON.tie : result.outranked ? REASON.specific : tokenReason
  const entries = candidates.map(candidate => {
    const isDeciding = result.deciding.includes(candidate)
    if (isDeciding) return entryOf(candidate, true, winnerReason)
    return entryOf(candidate, false, candidate.specificity === result.top ? REASON.tie : REASON.specific)
  })
  const decision = result.verdict
    ? principal.decision
    : { source: SOURCE.TOKEN, subject: token.name, node: result.winner.node, allowed: false, depth: 0 }
  return { allowed: result.verdict, decision, trace: [...principal.trace, ...entries] }
}

/** True when every admin area is allowed in the evaluation. */
const holdsEveryArea = (context) =>
  AREAS.every(area => evaluate({ ...context, node: `ingot.admin.${area}`, token: null }).allowed)

/** The nodes of a list for which the evaluation allows. */
const allowedNodes = (nodes, context) => nodes.filter(node => evaluate({ ...context, node, token: null }).allowed)

// ---------------------------------------------------------------- team parents

/** The longest chain of parent steps above a team, following the given graph. */
const heightOf = (name, graph, visiting = new Set()) => {
  const key = name.toLowerCase()
  if (visiting.has(key)) return 0
  const team = graph.find(other => same(other.name, name))
  if (!team || (team.parents ?? []).length === 0) return 0
  visiting.add(key)
  const height = 1 + Math.max(...team.parents.map(parent => heightOf(parent, graph, visiting)))
  visiting.delete(key)
  return height
}

/** The path of team names from `from` upwards to `to` through the parents, or null. */
const pathUp = (from, to, graph, visited = new Set()) => {
  if (same(from, to)) return [to]
  if (visited.has(from.toLowerCase())) return null
  visited.add(from.toLowerCase())
  const team = graph.find(other => same(other.name, from))
  for (const parent of team?.parents ?? []) {
    const rest = pathUp(parent, to, graph, visited)
    if (rest) return [from, ...rest]
  }
  return null
}

/**
 * Checks new parents for a team against the graph of all teams ({ name, parents }). Returns null when
 * they are fine, or { status: 409, message } for a cycle or for a chain deeper than MAX_TEAM_DEPTH.
 */
const checkParents = (graph, teamName, newParents) => {
  for (const parent of newParents) {
    const path = pathUp(parent, teamName, graph)
    if (path) {
      return {
        status: 409,
        message: `Adding ${parent} as a parent of ${teamName} would create a cycle: ${[teamName, ...path].join(" -> ")}`,
      }
    }
  }
  const changed = graph.map(team => same(team.name, teamName) ? { ...team, parents: newParents } : team)
  const deepest = Math.max(0, ...changed.map(team => heightOf(team.name, changed)))
  if (deepest > MAX_TEAM_DEPTH) {
    return { status: 409, message: `Inheritance deeper than ${MAX_TEAM_DEPTH} levels is not allowed` }
  }
  return null
}

// ---------------------------------------------------------------- catalog and templates

const AREA_DESCRIPTIONS = {
  settings: "Change the instance settings",
  users: "Manage users and machine principals",
  teams: "Manage teams and their access",
  tokens: "Manage the access tokens of other principals",
  maintenance: "Run maintenance and cleanup",
  audit: "Read the audit log",
  console: "Use the console",
  dashboard: "Open the admin dashboard",
}

const ACTION_DESCRIPTIONS = {
  read: "Read artifacts from",
  deploy: "Deploy artifacts to",
  delete: "Delete artifacts from",
  manage: "Manage the settings of",
}

/** Every node a person can pick: the wildcards, the admin areas and the actions of each repository. */
const buildCatalog = (repositories, prefix = "") => {
  const entries = [
    { node: "*", description: "Everything", kind: "WILDCARD" },
    { node: "ingot.admin.*", description: "Every admin area", kind: "WILDCARD" },
    { node: "ingot.repository.*.*", description: "Every action on every repository", kind: "WILDCARD" },
    ...AREAS.map(area => ({ node: `ingot.admin.${area}`, description: AREA_DESCRIPTIONS[area], kind: "STATIC" })),
    ...[...repositories].sort().flatMap(repository =>
      ACTIONS.map(action => ({
        node: `ingot.repository.${repository}.${action}`,
        description: `${ACTION_DESCRIPTIONS[action]} ${repository}`,
        kind: "REPOSITORY",
      }))),
  ]
  return entries.filter(entry => entry.node.startsWith(prefix ?? ""))
}

const repositoryNode = (action) => `ingot.repository.{repository}.${action}`

const TEMPLATES = [
  { id: "read", label: "Read", nodes: [repositoryNode("read")] },
  { id: "deploy", label: "Deploy", nodes: [repositoryNode("read"), repositoryNode("deploy")] },
  { id: "maintain", label: "Maintain", nodes: [repositoryNode("read"), repositoryNode("deploy"), repositoryNode("delete")] },
  { id: "admin", label: "Repository admin", nodes: [repositoryNode("read"), repositoryNode("deploy"), repositoryNode("delete"), repositoryNode("manage")] },
  { id: "instance-admin", label: "Instance admin", nodes: ["ingot.admin.*"] },
]

/** A copy, so that a caller cannot change the templates. */
const templates = () => TEMPLATES.map(template => ({ ...template, nodes: [...template.nodes] }))

module.exports = {
  MAX_TEAM_DEPTH, AREAS, ACTIONS, SOURCE,
  validateNode, validateEntries, kindOf, matches, specificity,
  evaluate, holdsEveryArea, allowedNodes, ancestorsOf,
  checkParents, buildCatalog, templates,
}
