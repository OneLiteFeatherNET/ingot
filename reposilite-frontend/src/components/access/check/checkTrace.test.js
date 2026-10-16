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

// @vitest-environment jsdom

import { describe, it, expect } from 'vitest'
import { decisionSummary, kindLabel, problemMessages, sourceLabel, traceSteps } from './checkTrace'

const TRACE = [
  { source: 'USER', subject: 'alice', node: 'ingot.repository.private.read', allowed: true, matched: true, reason: 'User node beats team node' },
  { source: 'TEAM', subject: 'developers', node: 'ingot.repository.private.read', allowed: false, matched: false, reason: 'Not consulted: a higher layer already decided' },
  { source: 'DEFAULT', subject: null, node: null, allowed: false, matched: true, reason: 'No node matches, denied by default' }
]

describe('sourceLabel', () => {
  it.each([
    ['USER', 'User'],
    ['TEAM', 'Team'],
    ['INHERITED_TEAM', 'Inherited team'],
    ['LEGACY', 'Legacy'],
    ['TOKEN', 'Token'],
    ['DEFAULT', 'Default']
  ])('names %s as %s', (code, label) => {
    expect(sourceLabel(code), `label of ${code}`).toBe(label)
  })

  it('shows an unknown source code as it came', () => {
    expect(sourceLabel('FEDERATED')).toBe('FEDERATED')
  })
})

describe('traceSteps', () => {
  it('keeps the order of the trace and numbers the steps from 1', () => {
    const steps = traceSteps(TRACE)

    expect(steps.map(step => [step.position, step.source])).toEqual([[1, 'USER'], [2, 'TEAM'], [3, 'DEFAULT']])
  })

  it('does not mute a step that decided', () => {
    const [deciding] = traceSteps(TRACE)

    expect(deciding.muted, 'the deciding step is not muted').toBe(false)
  })

  it('mutes a step that did not decide', () => {
    const [, notConsulted] = traceSteps(TRACE)

    expect(notConsulted.muted, 'the not consulted step is muted').toBe(true)
  })

  it('carries the reason of the server verbatim', () => {
    expect(traceSteps(TRACE).map(step => step.reason)).toEqual([
      'User node beats team node',
      'Not consulted: a higher layer already decided',
      'No node matches, denied by default'
    ])
  })

  it('names the source of each step readably', () => {
    expect(traceSteps(TRACE).map(step => step.sourceLabel)).toEqual(['User', 'Team', 'Default'])
  })

  it('leaves the subject and node of the default step empty', () => {
    const [, , fallback] = traceSteps(TRACE)

    expect([fallback.subject, fallback.node]).toEqual([null, null])
  })

  it('reads a missing trace as no steps', () => {
    expect(traceSteps(undefined)).toEqual([])
  })
})

describe('decisionSummary', () => {
  it('reads the source, subject, node and depth of the decision', () => {
    expect(decisionSummary({ source: 'INHERITED_TEAM', subject: 'developers', node: 'ingot.admin.users', allowed: true, depth: 2 }))
      .toEqual({
        sourceLabel: 'Inherited team',
        subject: 'developers',
        node: 'ingot.admin.users',
        depth: 2,
        decidingNode: null,
        decidingKind: null,
        explanation: null
      })
  })

  it('leaves the subject, node and depth of a default decision empty', () => {
    expect(decisionSummary({ source: 'DEFAULT', subject: null, node: null, allowed: false, depth: null }))
      .toEqual({
        sourceLabel: 'Default',
        subject: null,
        node: null,
        depth: null,
        decidingNode: null,
        decidingKind: null,
        explanation: null
      })
  })
})

describe('problemMessages', () => {
  it('lists the messages of the problems', () => {
    const error = { response: { data: { message: '2 permission nodes are invalid', problems: [{ message: 'one' }, { message: 'two' }] } } }

    expect(problemMessages(error)).toEqual(['one', 'two'])
  })

  it('falls back to the message when no problem is listed', () => {
    expect(problemMessages({ response: { data: { message: 'Bad node' } } })).toEqual(['Bad node'])
  })

  it('finds nothing for an error without an answer', () => {
    expect(problemMessages(new Error('offline'))).toEqual([])
  })
})

describe('decision facts of an artifact decision', () => {
  const DECISION = {
    source: 'USER',
    subject: 'alice',
    node: 'ingot.artifact.net.onelitefeather:mycelium-bom.deploy',
    allowed: false,
    depth: 0,
    decidingNode: 'ingot.artifact.net.onelitefeather:internal.deploy',
    decidingKind: 'ARTIFACT',
    explanation: "Denied by the artifact node 'ingot.artifact.net.onelitefeather:internal.deploy' of alice (USER)"
  }

  it('reads the deciding node, its kind and the explanation of the decision', () => {
    const summary = decisionSummary(DECISION)

    expect(summary.decidingNode).toBe('ingot.artifact.net.onelitefeather:internal.deploy')
    expect(summary.decidingKind).toBe('ARTIFACT')
    expect(summary.explanation).toBe("Denied by the artifact node 'ingot.artifact.net.onelitefeather:internal.deploy' of alice (USER)")
  })

  it('leaves the deciding fields empty when the decision names none', () => {
    const summary = decisionSummary({ source: 'DEFAULT', subject: null, node: null, allowed: false, depth: null })

    expect([summary.decidingNode, summary.decidingKind, summary.explanation]).toEqual([null, null, null])
  })

  it.each([
    ['ARTIFACT', 'Artifact node'],
    ['REPOSITORY', 'Repository node'],
    ['ADMIN', 'Admin node'],
    ['EVERYTHING', 'Everything node'],
    ['GRANT', 'Role grant']
  ])('names the kind %s as %s', (kind, label) => {
    expect(kindLabel(kind)).toBe(label)
  })

  it('shows no kind as None', () => {
    expect(kindLabel(null)).toBe('None')
  })
})
