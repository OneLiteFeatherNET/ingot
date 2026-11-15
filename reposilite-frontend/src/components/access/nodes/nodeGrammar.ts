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
 * The grammar of a permission node (ADR-INGT-0003), the same one the server enforces. A node is
 * dot separated segments. A segment is a literal of letters, digits, '_' and '-', or a whole
 * segment '*'. The valid shapes are:
 *
 *   *                                      everything (WILDCARD)
 *   ingot.admin.<area>                     area is one of AREAS or '*' (STATIC, WILDCARD for '*')
 *   ingot.repository.<repo>.<action>       repo is a literal or '*', action one of ACTIONS or '*'
 *                                          (REPOSITORY for a literal repository, else WILDCARD)
 *   ingot.artifact.<group>[:<artifact>].<action>
 *   ingot.repository.<repo>.artifact.<group>[:<artifact>].<action>
 *                                          artifact nodes (ARTIFACT): the action is one of
 *                                          ARTIFACT_ACTIONS or '*', a '*' is only a whole groupId, and
 *                                          a qualified node may not name '*' as its repository
 *
 * Pure: no imports, no state.
 */

const AREAS = ['settings', 'users', 'teams', 'tokens', 'maintenance', 'audit', 'console', 'dashboard']
const ACTIONS = ['read', 'deploy', 'delete', 'manage']
// An artifact node has no manage action: managing a repository is not scoped to a path.
const ARTIFACT_ACTIONS = ['read', 'deploy', 'delete']
const ARTIFACT_ACTION_TEXT = [...ARTIFACT_ACTIONS, '*'].join('|')
const ARTIFACT_HINT = `Use ingot.artifact.<groupId>[:<artifactId>].<${ARTIFACT_ACTION_TEXT}>`
const QUALIFIED_ARTIFACT_HINT = `Use ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<${ARTIFACT_ACTION_TEXT}>`
const NAME = /^[A-Za-z0-9_-]+$/
const NAME_HINT = "Use letters, digits, '_' and '-' in a name, with '.' between its parts"
const MAX_LENGTH = 255

const SEGMENT = /^(\*|[A-Za-z0-9_-]+)$/
const SEGMENT_HINT = "Segments may only hold letters, digits, '_' and '-', and a '*' must be a whole segment"
const REPOSITORY_HINT = `Use ingot.repository.<repository>.<${ACTIONS.join('|')}>`
const ADMIN_HINT = `Use ingot.admin.<${AREAS.join('|')}|*>`
const SHAPE_HINT = 'Use *, ingot.admin.<area> or ingot.repository.<repository>.<action>'

/**
 * The artifact part of a node: { repository, rest } where repository is null for the unqualified
 * form, and rest the segments after '.artifact.'. Null when the node is no artifact node. A
 * qualified node without a repository name (ingot.repository.artifact...) is reported by the caller.
 */
const artifactPartOf = (segments) => {
  if (segments[0] !== 'ingot') return null
  if (segments[1] === 'artifact') return { repository: null, rest: segments.slice(2) }
  if (segments[1] !== 'repository') return null
  const at = segments.indexOf('artifact', 2)
  if (at === -1) return null
  return { repository: segments.slice(2, at).join('.'), rest: segments.slice(at + 1), nameless: at === 2 }
}

/** The name of a groupId or artifactId that is not valid, as a message, or null when it is valid. */
const namePartProblem = (label, value, text) => {
  if (value.includes('*')) return `The ${label} '${value}' of '${text}' is invalid, a wildcard must be the whole ${label}`
  const parts = value.split('.')
  if (parts.some(part => part === '')) return `The ${label} '${value}' of '${text}' has an empty segment`
  const bad = parts.find(part => !NAME.test(part))
  return bad === undefined ? null : `The ${label} '${value}' of '${text}' has an invalid character in '${bad}'. ${NAME_HINT}`
}

/** The messages of an artifact node, the same checks the server makes. */
const artifactMessages = (text, artifact) => {
  const hint = artifact.repository === null ? ARTIFACT_HINT : QUALIFIED_ARTIFACT_HINT
  if (artifact.nameless) return [`The repository name before .artifact. is missing. ${QUALIFIED_ARTIFACT_HINT}`]
  if (text.length > MAX_LENGTH) return [`The node is longer than ${MAX_LENGTH} characters`]
  if (artifact.repository === '*') {
    return ['The wildcard repository is not allowed for an artifact node, use ingot.artifact.<groupId>[:<artifactId>].<action> for every repository']
  }
  if (artifact.repository !== null) {
    const bad = artifact.repository.split('.').find(part => part === '' || !NAME.test(part))
    if (bad !== undefined) return [`The repository '${artifact.repository}' of '${text}' is invalid. ${NAME_HINT}`]
  }
  const body = artifact.rest.join('.')
  const split = body.lastIndexOf('.')
  if (split <= 0) return [`The artifact node '${text}' needs a groupId and an action. ${hint}`]
  const coordinate = body.slice(0, split)
  const action = body.slice(split + 1)
  if (action !== '*' && !ARTIFACT_ACTIONS.includes(action)) return [`Unknown action '${action}'. ${hint}`]
  const parts = coordinate.split(':')
  if (parts.length > 2) return [`The coordinate '${coordinate}' of '${text}' may contain only one ':' between the groupId and the artifactId`]
  const [groupId, artifactPart] = parts
  if (groupId === '') return [`The groupId of '${text}' is empty. ${hint}`]
  if (artifactPart === '') return [`The artifactId of '${text}' is empty`]
  const artifactId = artifactPart === undefined || artifactPart === '*' ? null : artifactPart
  if (groupId === '*') {
    return artifactId === null
      ? []
      : [`The groupId * with the artifactId '${artifactId}' in '${text}' is ambiguous, name the groupId`]
  }
  const problem = namePartProblem('groupId', groupId, text) ?? (artifactId === null ? null : namePartProblem('artifactId', artifactId, text))
  return problem === null ? [] : [problem]
}

/** The messages that make a text an invalid node. An empty list means the text is valid. */
const messagesOf = (text) => {
  if (typeof text !== 'string' || text.trim() === '') return ['Node is required']
  if (text !== text.trim()) return ['Node must not start or end with a space']
  if (text === '*') return []
  const segments = text.split('.')
  const artifact = artifactPartOf(segments)
  if (artifact !== null) return /\s/.test(text) ? ['The node must not contain whitespace'] : artifactMessages(text, artifact)
  if (segments.some(segment => !SEGMENT.test(segment))) return [SEGMENT_HINT]
  if (segments[0] !== 'ingot') return [SHAPE_HINT]
  if (segments[1] === 'admin') {
    if (segments.length !== 3) return [ADMIN_HINT]
    const area = segments[2]
    return area === '*' || AREAS.includes(area) ? [] : [`Unknown admin area '${area}'. ${ADMIN_HINT}`]
  }
  if (segments[1] === 'repository') {
    if (segments.length !== 4) return [REPOSITORY_HINT]
    const action = segments[3]
    return action === '*' || ACTIONS.includes(action) ? [] : [`Unknown action '${action}'. ${REPOSITORY_HINT}`]
  }
  return [SHAPE_HINT]
}

/** STATIC (an admin area), REPOSITORY (a literal repository), ARTIFACT or WILDCARD. Only for a valid text. */
const kindOf = (text) => {
  const segments = text.split('.')
  if (text === '*') return 'WILDCARD'
  if (artifactPartOf(segments) !== null) return 'ARTIFACT'
  if (segments[1] === 'admin') return segments[2] === '*' ? 'WILDCARD' : 'STATIC'
  return segments[2] === '*' || segments[3] === '*' ? 'WILDCARD' : 'REPOSITORY'
}

/**
 * Checks a text against the grammar. Returns { valid, kind, problems } where problems is a list
 * of { node, message } (the shape the server answers a 400 with) and kind is null when invalid.
 */
const parseNode = (text) => {
  const messages = messagesOf(text)
  const node = typeof text === 'string' ? text : null
  if (messages.length > 0) {
    return { valid: false, kind: null, problems: messages.map(message => ({ node, message })) }
  }
  return { valid: true, kind: kindOf(text), problems: [] }
}

/** True when the text is a name the grammar allows for a repository segment (a literal or '*'). */
const isRepositorySegment = (text) => typeof text === 'string' && SEGMENT.test(text)

export { ACTIONS, ARTIFACT_ACTIONS, AREAS, artifactPartOf, isRepositorySegment, parseNode }
