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
 * The rules of the permission check that need no component: the readable name of a source, the
 * steps of the derivation trace in the order the server gave them, the decision summary and the
 * problem messages of a failed request. Pure.
 */

const SOURCE_LABELS = {
  USER: 'User',
  TEAM: 'Team',
  INHERITED_TEAM: 'Inherited team',
  LEGACY: 'Legacy',
  TOKEN: 'Token',
  DEFAULT: 'Default'
}

/** The readable name of a source code. An unknown code is shown as it came. */
const sourceLabel = (source) => SOURCE_LABELS[source] ?? String(source ?? 'Unknown')

/**
 * The trace as the stepper shows it: one step per entry, in trace order, numbered from 1. A step
 * that did not decide (`matched` is false) is muted, whether it lost inside its layer or was never
 * consulted. The reason is the text of the server, unchanged.
 */
const traceSteps = (trace) =>
  (trace ?? []).map((entry, index) => ({
    position: index + 1,
    source: entry.source,
    sourceLabel: sourceLabel(entry.source),
    subject: entry.subject ?? null,
    node: entry.node ?? null,
    allowed: entry.allowed === true,
    matched: entry.matched === true,
    muted: entry.matched !== true,
    reason: entry.reason ?? ''
  }))

const KIND_LABELS = {
  ARTIFACT: 'Artifact node',
  REPOSITORY: 'Repository node',
  ADMIN: 'Admin node',
  EVERYTHING: 'Everything node',
  GRANT: 'Role grant',
  INSTANCE_ADMIN: 'Instance administrator'
}

/** The readable name of the kind of the deciding rule. No kind (no node matched) reads as None. */
const kindLabel = (kind) => (kind ? (KIND_LABELS[kind] ?? String(kind)) : 'None')

/**
 * The facts of the decision for the summary: source, subject, node and depth, and the deciding node, its kind and the
 * one-sentence explanation of the server. Empty ones are null.
 */
const decisionSummary = (decision) => ({
  sourceLabel: sourceLabel(decision?.source),
  subject: decision?.subject ?? null,
  node: decision?.node ?? null,
  depth: decision?.depth ?? null,
  decidingNode: decision?.decidingNode ?? null,
  decidingKind: decision?.decidingKind ?? null,
  explanation: decision?.explanation ?? null
})

/** The messages of a 400 answer: its problems, or its message when it lists none. */
const problemMessages = (error) => {
  const data = error?.response?.data
  const listed = (data?.problems ?? []).map(problem => problem.message).filter(Boolean)
  if (listed.length > 0) return listed
  return data?.message ? [data.message] : []
}

export { SOURCE_LABELS, decisionSummary, kindLabel, problemMessages, sourceLabel, traceSteps }
