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
 *
 * Pure: no imports, no state.
 */

const AREAS = ['settings', 'users', 'teams', 'tokens', 'maintenance', 'audit', 'console', 'dashboard']
const ACTIONS = ['read', 'deploy', 'delete', 'manage']

const SEGMENT = /^(\*|[A-Za-z0-9_-]+)$/
const SEGMENT_HINT = "Segments may only hold letters, digits, '_' and '-', and a '*' must be a whole segment"
const REPOSITORY_HINT = `Use ingot.repository.<repository>.<${ACTIONS.join('|')}>`
const ADMIN_HINT = `Use ingot.admin.<${AREAS.join('|')}|*>`
const SHAPE_HINT = 'Use *, ingot.admin.<area> or ingot.repository.<repository>.<action>'

/** The messages that make a text an invalid node. An empty list means the text is valid. */
const messagesOf = (text) => {
  if (typeof text !== 'string' || text.trim() === '') return ['Node is required']
  if (text !== text.trim()) return ['Node must not start or end with a space']
  const segments = text.split('.')
  if (segments.some(segment => !SEGMENT.test(segment))) return [SEGMENT_HINT]
  if (text === '*') return []
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

/** STATIC (an admin area), REPOSITORY (a literal repository) or WILDCARD. Only for a valid text. */
const kindOf = (text) => {
  const segments = text.split('.')
  if (text === '*') return 'WILDCARD'
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

export { ACTIONS, AREAS, isRepositorySegment, parseNode }
