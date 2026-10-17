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

// The longest chain of parent steps above a team, as in the backend (TeamHierarchy.MAX_DEPTH).
const MAX_TEAM_DEPTH = 8

// The longest node, as in the backend (NodePattern.MAX_LENGTH).
const MAX_NODE_LENGTH = 255

const AREAS = ["settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard"]
const ACTIONS = ["read", "deploy", "delete", "manage"]

// An artifact node has no manage action, as in the backend (NodePattern.ARTIFACT_ACTIONS).
const ARTIFACT_ACTIONS = ["read", "deploy", "delete"]
const ARTIFACT_TIER = 10000
const ARTIFACT_PREFIX = "ingot.artifact."
const ARTIFACT_HINT = "Use ingot.artifact.<groupId>[:<artifactId>].<read|deploy|delete|*>"
const QUALIFIED_ARTIFACT_HINT = "Use ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<read|deploy|delete|*>"
const NAME_PART = /^[A-Za-z0-9_-]+$/

// The node that makes a subject an instance admin (the backend stores the instance role as this node).
const INSTANCE_ADMIN_NODE = "ingot.admin.*"

const SOURCE = {
  USER: "USER", TEAM: "TEAM", INHERITED_TEAM: "INHERITED_TEAM", DEFAULT: "DEFAULT", TOKEN: "TOKEN",
}

const REPOSITORY_PREFIX = "ingot.repository."
const ADMIN_PREFIX = "ingot.admin."

// ---------------------------------------------------------------- grammar

// Whitespace as Kotlin sees it (Character.isWhitespace or isSpaceChar), and the ISO control characters.
const WHITESPACE_OR_CONTROL = /[\p{Z}\u0000-\u001f\u007f-\u009f]/u

/**
 * Parses an artifact node (`<groupId>[:<artifactId>].<action>` after the prefix, with the repository of the qualified
 * form or null). Returns { problem } for an invalid node, otherwise the ARTIFACT parts.
 */
const parseArtifact = (text, repository, body) => {
  const qualifiedHint = repository === null ? ARTIFACT_HINT : QUALIFIED_ARTIFACT_HINT
  if (repository === "*") {
    return { problem: `The repository wildcard of '${text}' is not allowed for an artifact node, use 'ingot.artifact.<groupId>.<action>' for every repository` }
  }
  const split = body.lastIndexOf(".")
  if (split <= 0) {
    return { problem: `The artifact node '${text}' needs a groupId and an action, for example ingot.artifact.net.example.deploy. ${qualifiedHint}` }
  }
  const coordinate = body.slice(0, split)
  const action = body.slice(split + 1)
  if (action !== "*" && !ARTIFACT_ACTIONS.includes(action)) {
    return { problem: `The action '${action}' of '${text}' is unknown for an artifact node, supported: ${ARTIFACT_ACTIONS.join(", ")} or *` }
  }
  const parts = coordinate.split(":")
  if (parts.length > 2) return { problem: `The coordinate '${coordinate}' of '${text}' can contain only one ':' between the groupId and the artifactId` }
  const groupId = parts[0]
  const rawArtifactId = parts.length === 2 ? parts[1] : null
  if (groupId === "") return { problem: `The groupId of '${text}' is empty` }
  if (rawArtifactId === "") return { problem: `The artifactId of '${text}' is empty` }
  const artifactId = rawArtifactId === "*" ? null : rawArtifactId
  if (groupId === "*" && artifactId !== null) {
    return { problem: `The groupId * with the artifactId '${artifactId}' in '${text}' is ambiguous, name the groupId of the artifact` }
  }
  for (const [label, value] of [["groupId", groupId], ["artifactId", artifactId]]) {
    if (value === null || value === "*") continue
    if (value.includes("*")) return { problem: `The ${label} '${value}' of '${text}' is invalid, a wildcard must be the whole ${label}` }
    const names = value.split(".")
    if (names.some(name => name === "")) return { problem: `The ${label} '${value}' of '${text}' has an empty segment` }
    const bad = names.find(name => !NAME_PART.test(name))
    if (bad !== undefined) {
      return { problem: `The ${label} '${value}' of '${text}' has an invalid character in '${bad}', allowed are letters, digits, '_', '-' and '.'` }
    }
  }
  return { kind: "ARTIFACT", repository, action, area: null, groupId, artifactId }
}

/**
 * Parses a node as NodePattern.parse does, in the same order of checks and with the same messages. Returns
 * { problem } for an invalid node, otherwise { kind, repository, action, area } where kind is EVERYTHING,
 * REPOSITORY, ARTIFACT or ADMIN and the parts that do not belong to the kind are null.
 */
const parseNode = (text) => {
  if (text.length === 0) return { problem: "The node is empty" }
  if (text.length > MAX_NODE_LENGTH) return { problem: `The node '${text.slice(0, 40)}...' is longer than ${MAX_NODE_LENGTH} characters` }
  if (WHITESPACE_OR_CONTROL.test(text)) return { problem: `The node '${text}' contains whitespace or control characters` }
  if (text.startsWith("-")) {
    return { problem: `The node '${text}' starts with '-', negation is stored as allowed false and not in the name` }
  }
  if (text === "*") return { kind: "EVERYTHING", repository: null, action: null, area: null }
  if (text.startsWith(ARTIFACT_PREFIX)) return parseArtifact(text, null, text.slice(ARTIFACT_PREFIX.length))
  if (text.startsWith(REPOSITORY_PREFIX)) {
    const rest = text.slice(REPOSITORY_PREFIX.length)
    const segments = rest.split(".")
    const artifactAt = segments.indexOf("artifact")
    if (artifactAt === 0) {
      return { problem: `The repository name before .artifact. is missing. ${QUALIFIED_ARTIFACT_HINT}` }
    }
    if (artifactAt > 0) {
      return parseArtifact(text, segments.slice(0, artifactAt).join("."), segments.slice(artifactAt + 1).join("."))
    }
    const split = rest.lastIndexOf(".")
    if (split <= 0) {
      return { problem: `The repository node '${text}' needs a repository and an action, for example ingot.repository.releases.read` }
    }
    const repository = rest.slice(0, split)
    const action = rest.slice(split + 1)
    if (action !== "*" && !ACTIONS.includes(action)) {
      return { problem: `The action '${action}' of '${text}' is unknown, supported: ${ACTIONS.join(", ")} or *` }
    }
    if (repository !== "*" && (repository.includes("*") || repository.split(".").some(part => part === ""))) {
      return { problem: `The repository '${repository}' of '${text}' is invalid, a wildcard must be the whole repository segment` }
    }
    return { kind: "REPOSITORY", repository, action, area: null }
  }
  if (text.startsWith(ADMIN_PREFIX)) {
    const area = text.slice(ADMIN_PREFIX.length)
    if (area !== "*" && !AREAS.includes(area)) {
      return { problem: `The admin area '${area}' of '${text}' is unknown, supported: ${AREAS.join(", ")} or *` }
    }
    return { kind: "ADMIN", repository: null, action: null, area }
  }
  return { problem: `The node '${text}' is unknown, nodes start with 'ingot.repository.', 'ingot.artifact.' or 'ingot.admin.', or are '*'` }
}

/** The reason a node is not valid, or null when it is valid (NodePattern.problemOf). */
const problemOf = (text) => parseNode(text).problem ?? null

/**
 * Checks a list of { node, allowed } entries the way the backend does (IdentityFacade.parseNodes): every invalid node
 * is reported in one message, then a node listed twice. Returns { entries } with the cleaned entries, or { message }
 * with the text of the 400, and { problems: [{ node, message }] } with one entry per row the problem belongs to.
 * An entry that is not { node: text, allowed: boolean } cannot be read at all.
 */
const validateEntries = (entries) => {
  if (!Array.isArray(entries)) return { message: "Failed to read body" }
  const unreadable = entries.some(entry =>
    !entry || typeof entry !== "object" || typeof entry.node !== "string" || typeof entry.allowed !== "boolean")
  if (unreadable) return { message: "Failed to read body" }
  const problems = entries
    .map(entry => ({ node: entry.node, message: problemOf(entry.node) }))
    .filter(problem => problem.message !== null)
  if (problems.length > 0) {
    return { message: `Invalid permission nodes: ${problems.map(problem => problem.message).join("; ")}`, problems }
  }
  const counts = new Map()
  entries.forEach(entry => counts.set(entry.node, (counts.get(entry.node) ?? 0) + 1))
  const duplicates = [...counts].filter(([, count]) => count > 1).map(([node]) => node).sort()
  if (duplicates.length > 0) {
    return {
      message: `Nodes listed twice: ${duplicates.join(", ")}`,
      problems: duplicates.map(node => ({ node, message: `Node '${node}' is listed twice` })),
    }
  }
  return { entries: entries.map(entry => ({ node: entry.node, allowed: entry.allowed })) }
}

// ---------------------------------------------------------------- matching

// The path segments an artifact node covers: its groupId parts, and its artifactId when it has one.
const pathOf = (artifact) => {
  if (artifact.groupId === "*") return []
  return [...artifact.groupId.split("."), ...(artifact.artifactId ? [artifact.artifactId] : [])]
}

/**
 * An artifact pattern covers an artifact request when the action agrees, the repository agrees (an unqualified pattern
 * covers every repository, a qualified one only its own) and the path of the pattern is a prefix of the request's path,
 * compared by whole segments, so `net.example` does not cover `net.examples`.
 */
const artifactCovers = (own, request) => {
  if (own.repository !== null && own.repository !== request.repository) return false
  if (own.action !== "*" && own.action !== request.action) return false
  const ownPath = pathOf(own)
  const requestPath = coveredPath(request)
  return ownPath.length <= requestPath.length && ownPath.every((segment, index) => segment === requestPath[index])
}

/**
 * The request of a repository path, as NodePattern.repositoryPath builds it: the repository action, with the segments of the
 * path inside the repository. A path with a parent segment has no segments, so no artifact node covers it.
 */
const pathRequest = ({ repository, path, action }) => {
  const segments = String(path ?? "").split("/").filter(part => part !== "")
  return {
    kind: "REPOSITORY", repository, action, area: null, text: `ingot.repository.${repository}.${action}`,
    pathSegments: segments.includes("..") ? null : segments,
  }
}

// A requested node is the text of a node, or a request object (a parsed node, or a path request) with its text.
const requestOf = (node) => (typeof node === "string" ? { ...parseNode(node), text: node } : node)

// The path a request covers: an artifact node covers its group and artifactId, a repository path its segments.
const coveredPath = (request) => (request.kind === "ARTIFACT" ? pathOf(request) : request.pathSegments)

/**
 * A pattern covers a requested node as NodePattern.matches does: '*' covers everything, a repository pattern covers a
 * repository node of the same repository (or any, for '*') and action, an admin pattern an admin node of the same area,
 * an artifact pattern an artifact node or a repository path it covers (see artifactCovers). A requested node is concrete,
 * so a '*' in it is a literal. A node that does not parse matches nothing.
 */
const matches = (pattern, node) => {
  const own = parseNode(pattern)
  const request = requestOf(node)
  if (own.problem || request.problem) return false
  if (own.kind === "EVERYTHING") return true
  if (own.kind === "ARTIFACT") {
    const coverable = request.kind === "ARTIFACT" || (request.kind === "REPOSITORY" && request.pathSegments != null)
    return coverable && artifactCovers(own, request)
  }
  if (own.kind === "REPOSITORY") {
    return request.kind === "REPOSITORY" &&
      (own.repository === "*" || own.repository === request.repository) &&
      (own.action === "*" || own.action === request.action)
  }
  return request.kind === "ADMIN" && (own.area === "*" || own.area === request.area)
}

/**
 * The literal parts of a pattern (NodePattern.specificity): 0 for '*', 2 plus one per literal part otherwise. An artifact
 * node is in its own tier above them, ordered by qualified, artifactId, the groupId segments and a literal action, as
 * the backend orders them (NodeSpecificity).
 */
const specificity = (pattern) => {
  const own = parseNode(pattern)
  if (own.problem) return 0
  if (own.kind === "EVERYTHING") return 0
  if (own.kind === "ARTIFACT") {
    const groupSegments = own.groupId === "*" ? 0 : own.groupId.split(".").length
    return ARTIFACT_TIER + (own.repository !== null ? 1000 : 0) + (own.artifactId ? 100 : 0) +
      groupSegments * 10 + (own.action !== "*" ? 1 : 0)
  }
  if (own.kind === "REPOSITORY") return 2 + (own.repository !== "*" ? 1 : 0) + (own.action !== "*" ? 1 : 0)
  return 2 + (own.area !== "*" ? 1 : 0)
}

// ---------------------------------------------------------------- evaluation

const HIGHER_LABEL = { USER: "User node", TEAM: "Team node", INHERITED_TEAM: "Inherited team node" }
const LOWER_LABEL = { TEAM: "team node", INHERITED_TEAM: "inherited team node" }

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

/** Evaluates the matches of the user, team and inherited team layers for a node. */
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
  return decideLayers(layers, node)
}

/** The first layer with a candidate decides; every candidate of every layer gets a trace entry. */
const decideLayers = (layers, requested) => {
  const deciding = layers.findIndex(layer => layer.candidates.length > 0)
  const trace = []
  if (deciding < 0) {
    return {
      decision: defaultDecision(requested.text),
      trace: [{ source: SOURCE.DEFAULT, subject: null, node: null, allowed: false, matched: true, reason: REASON.default, depth: 0, kind: null }],
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
  const kind = kindOf(winner.node)
  return {
    decision: {
      source: winner.source, subject: winner.subject, node: winner.node, allowed: result.verdict, depth: winner.depth,
      decidingNode: winner.node, decidingKind: kind,
      explanation: decisionExplanation(result.verdict, winner.node, kind, winner.source, winner.subject),
    },
    trace,
  }
}

const entryOf = (candidate, matched, reason) =>
  ({
    source: candidate.source, subject: candidate.subject, node: candidate.node, allowed: candidate.allowed, matched, reason,
    depth: candidate.depth, kind: kindOf(candidate.node),
  })

// The kind of a node as the backend names it in a decision and a trace entry, or null for no node.
const kindOf = (node) => {
  if (node === null || node === undefined) return null
  const parsed = parseNode(node)
  return parsed.problem ? null : parsed.kind
}

const KIND_PHRASE = {
  ARTIFACT: "artifact node", REPOSITORY: "repository node", ADMIN: "admin node", EVERYTHING: "everything node",
  INSTANCE_ADMIN: "instance administrator",
}

// The denial of a request no node matches, with the same fields as a decided one. The explanation is the fake's own wording.
const defaultDecision = (requested) => ({
  source: SOURCE.DEFAULT, subject: null, node: requested, allowed: false, depth: 0,
  decidingNode: null, decidingKind: null,
  explanation: `Denied: no node matches '${requested}', so the default denial applies.`,
})

// The one sentence that says which node decided, in the words of the backend's explanation.
const decisionExplanation = (allowed, node, kind, source, subject) =>
  `${allowed ? "Allowed" : "Denied"} by the ${KIND_PHRASE[kind] ?? "node"} '${node}' of ${subject} (${source})`

/**
 * Evaluates a node for a principal.
 *   user:        { name, nodes }
 *   memberTeams: the teams the principal belongs to ({ name, nodes, parents })
 *   teams:       every team, to follow the parents
 *   token:       null, or { name, nodes }. A token without nodes does not narrow.
 * Returns { allowed, decision, trace }.
 */
const evaluateRules = ({ node, user, memberTeams = [], teams = [], token = null }) => {
  const requested = requestOf(node)
  const principal = evaluatePrincipal({ node: requested, user, memberTeams, teams })
  if (!token || !token.nodes || token.nodes.length === 0) {
    return { allowed: principal.decision.allowed, decision: principal.decision, trace: principal.trace }
  }
  const candidates = candidatesOf(SOURCE.TOKEN, token.name, token.nodes, requested, 0)
  if (!principal.decision.allowed) {
    const skipped = candidates.map(candidate => entryOf(candidate, false, REASON.notConsulted))
    return { allowed: false, decision: principal.decision, trace: [...principal.trace, ...skipped] }
  }
  const result = decideLayer(candidates)
  if (result === null) {
    const decision = {
      source: SOURCE.TOKEN, subject: token.name, node: requested.text, allowed: false, depth: 0,
      decidingNode: null, decidingKind: null,
      explanation: `Denied: token '${token.name}' lists no node for '${requested.text}'.`,
    }
    const entry = { source: SOURCE.TOKEN, subject: token.name, node: null, allowed: false, matched: true, reason: REASON.tokenDenied, depth: 0, kind: null }
    return { allowed: false, decision, trace: [...principal.trace, entry] }
  }
  const tokenReason = result.verdict ? REASON.tokenAllowed : REASON.tokenDeniedExplicit
  const winnerReason = result.tie ? REASON.tie : result.outranked ? REASON.specific : tokenReason
  const entries = candidates.map(candidate => {
    const isDeciding = result.deciding.includes(candidate)
    if (isDeciding) return entryOf(candidate, true, winnerReason)
    return entryOf(candidate, false, candidate.specificity === result.top ? REASON.tie : REASON.specific)
  })
  const winnerKind = kindOf(result.winner.node)
  const decision = result.verdict
    ? principal.decision
    : {
      source: SOURCE.TOKEN, subject: token.name, node: requested.text, allowed: false, depth: 0,
      decidingNode: result.winner.node, decidingKind: winnerKind,
      explanation: decisionExplanation(false, result.winner.node, winnerKind, SOURCE.TOKEN, token.name),
    }
  return { allowed: result.verdict, decision, trace: [...principal.trace, ...entries] }
}

/**
 * Decides a request the way the backend's PermissionEvaluator.allows does: a repository request (a node or a path) is
 * decided by its rules first, and an instance admin is allowed it when its token allows it. The shortcut is named as the
 * deciding kind INSTANCE_ADMIN, with the admin rule as the deciding node.
 */
const evaluate = (context) => {
  const requested = requestOf(context.node)
  const decided = evaluateRules({ ...context, node: requested })
  if (decided.allowed || requested.kind !== "REPOSITORY") {
    return decided
  }
  if (!isInstanceAdmin(context)) return decided
  // The token narrows first: a token that does not allow the request denies it.
  if (context.token && context.token.nodes.length > 0) {
    const narrowed = decideLayer(candidatesOf(SOURCE.TOKEN, context.token.name, context.token.nodes, requested, 0))
    if (narrowed === null || !narrowed.verdict) return decided
  }
  const admin = evaluateRules({ ...context, node: INSTANCE_ADMIN_NODE, token: null }).decision
  const decision = {
    source: admin.source, subject: admin.subject, node: requested.text, allowed: true, depth: admin.depth,
    decidingNode: admin.decidingNode, decidingKind: "INSTANCE_ADMIN",
    explanation: decisionExplanation(true, admin.decidingNode, "INSTANCE_ADMIN", admin.source, admin.subject),
  }
  return { allowed: true, decision, trace: decided.trace }
}

/** Whether the evaluation allows the instance admin node (the instance role, directly or through teams). The rules decide it. */
const isInstanceAdmin = (context) => evaluateRules({ ...context, node: INSTANCE_ADMIN_NODE }).allowed

/**
 * The node patterns the subject is allowed, as PermissionEvaluator.effectiveNodes: every distinct rule pattern of the
 * subject's own nodes, its teams and the teams they inherit, and the node list of the token when it narrows, each one
 * decided as if it were requested, sorted. The catalog is not expanded.
 */
const effectiveNodes = (context) => {
  const { user, memberTeams = [], teams = [], token = null } = context
  const narrowing = token && token.nodes && token.nodes.length > 0 ? token : null
  const patterns = [
    ...(user.nodes ?? []),
    ...memberTeams.flatMap(team => team.nodes ?? []),
    ...ancestorsOf(memberTeams, teams).flatMap(entry => entry.team.nodes ?? []),
    ...(narrowing ? narrowing.nodes : []),
  ].map(entry => entry.node)
  return [...new Set(patterns)]
    .filter(pattern => evaluateRules({ ...context, node: pattern }).allowed)
    .sort()
}

// ---------------------------------------------------------------- team parents

/**
 * The first problem of the parent graph, as TeamHierarchy.problemOf finds it: the teams are visited by id, each one
 * walks up its parents (also by id), a team met again on the walk is a cycle, and a team whose chain is longer than
 * MAX_TEAM_DEPTH is too deep. The graph is a list of { id, name, parents } with the parents as names.
 */
const hierarchyProblem = (graph) => {
  const idOf = (name) => graph.find(team => same(team.name, name))?.id
  const parentIds = (team) => (team.parents ?? []).map(idOf).filter(id => id !== undefined)
  const byId = new Map(graph.map(team => [team.id, team]))
  const depths = new Map()
  const onPath = new Set()
  const depthOf = (id) => {
    if (depths.has(id)) {
      const known = depths.get(id)
      return known > MAX_TEAM_DEPTH ? { tooDeep: id, depth: known } : null
    }
    if (onPath.has(id)) return { cycle: id }
    onPath.add(id)
    let depth = 0
    for (const parent of [...parentIds(byId.get(id))].sort((left, right) => left - right)) {
      const problem = depthOf(parent)
      if (problem) return problem
      depth = Math.max(depth, depths.get(parent) + 1)
    }
    onPath.delete(id)
    depths.set(id, depth)
    return depth > MAX_TEAM_DEPTH ? { tooDeep: id, depth } : null
  }
  for (const id of [...byId.keys()].sort((left, right) => left - right)) {
    const problem = depthOf(id)
    if (problem) return problem
  }
  return null
}

/**
 * Checks new parents for a team against the graph of all teams ({ id, name, parents }). Returns null when they are
 * fine, or { status: 409, message } for a cycle or for a chain deeper than MAX_TEAM_DEPTH, with the backend's text.
 */
const checkParents = (graph, teamName, newParents) => {
  const changed = graph.map(team => same(team.name, teamName) ? { ...team, parents: newParents } : team)
  const problem = hierarchyProblem(changed)
  if (!problem) return null
  const nameOf = (id) => changed.find(team => team.id === id)?.name ?? `team #${id}`
  if (problem.cycle !== undefined) {
    return {
      status: 409,
      message: `Inheritance of '${nameOf(problem.cycle)}' would form a cycle: a team cannot inherit from itself, directly or through its parents`,
    }
  }
  return {
    status: 409,
    message: `Inheritance of '${nameOf(problem.tooDeep)}' would be ${problem.depth} levels deep, the limit is ${MAX_TEAM_DEPTH} levels`,
  }
}

// ---------------------------------------------------------------- catalog and templates

/**
 * Every node a person can pick, as IdentityFacade.nodeCatalog lists it: the admin areas, the two wildcards, then each
 * repository (sorted) with its actions and its action wildcard, then the action of every repository. Kept when the node
 * starts with the prefix; an empty or absent prefix keeps all.
 */
const buildCatalog = (repositories, prefix = "") => {
  const entries = [
    ...AREAS.map(area => ({ node: `ingot.admin.${area}`, description: `Administer ${area}`, kind: "STATIC" })),
    { node: "ingot.admin.*", description: "Every administrative area, as the instance administrator", kind: "WILDCARD" },
    { node: "*", description: "Everything: every repository action and every administrative area", kind: "WILDCARD" },
    ...[...repositories].sort().flatMap(repository => [
      ...ACTIONS.map(action => ({
        node: `ingot.repository.${repository}.${action}`, description: `${action} on repository ${repository}`, kind: "REPOSITORY",
      })),
      { node: `ingot.repository.${repository}.*`, description: `Every action on repository ${repository}`, kind: "REPOSITORY" },
    ]),
    ...ACTIONS.map(action => ({ node: `ingot.repository.*.${action}`, description: `${action} on every repository`, kind: "WILDCARD" })),
    ...ARTIFACT_ACTIONS.map(action => ({ node: `ingot.artifact.*.${action}`, description: `${action} on every artifact in every repository`, kind: "ARTIFACT" })),
    { node: "ingot.artifact.*.*", description: "Every artifact action on every artifact in every repository", kind: "ARTIFACT" },
  ]
  return entries.filter(entry => !prefix || entry.node.startsWith(prefix))
}

const repositoryNode = (action) => `ingot.repository.{repository}.${action}`

const TEMPLATES = [
  { id: "read", label: "Read", nodes: [repositoryNode("read")] },
  { id: "deploy", label: "Deploy", nodes: [repositoryNode("read"), repositoryNode("deploy")] },
  { id: "maintain", label: "Maintain", nodes: [repositoryNode("read"), repositoryNode("deploy"), repositoryNode("delete")] },
  { id: "admin", label: "Admin", nodes: [repositoryNode("read"), repositoryNode("deploy"), repositoryNode("delete"), repositoryNode("manage")] },
  { id: "instance-admin", label: "Instance admin", nodes: ["ingot.admin.*"] },
  { id: "artifact-read", label: "Read an artifact", nodes: ["ingot.artifact.{groupId}:{artifactId}.read"] },
  {
    id: "artifact-deploy",
    label: "Deploy an artifact",
    nodes: ["ingot.artifact.{groupId}:{artifactId}.read", "ingot.artifact.{groupId}:{artifactId}.deploy"],
  },
  {
    id: "repository-artifact-deploy",
    label: "Deploy an artifact in one repository",
    nodes: ["ingot.repository.{repository}.artifact.{groupId}:{artifactId}.read", "ingot.repository.{repository}.artifact.{groupId}:{artifactId}.deploy"],
  },
]

/** A copy, so that a caller cannot change the templates. */
const templates = () => TEMPLATES.map(template => ({ ...template, nodes: [...template.nodes] }))

module.exports = {
  MAX_TEAM_DEPTH, MAX_NODE_LENGTH, AREAS, ACTIONS, SOURCE, INSTANCE_ADMIN_NODE,
  problemOf, validateEntries, matches, specificity, pathRequest, parseNode,
  evaluate, isInstanceAdmin, effectiveNodes, ancestorsOf, defaultDecision,
  checkParents, buildCatalog, templates,
}
