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
 * The rules of the wizard as pure functions of the page state and the job list (design D4 to D6 and D8).
 * Nothing here reads the page, the router or the server, so every rule is tested on its own.
 *
 * The page state is `{ connectionTested, discovered, mapping }`: whether a connection test passed, whether a
 * discovery finished, and the mapping `{ repositories: [{ nexusName, targetName, included }], conflictPolicy }`.
 * The jobs are the job views of the server. A job is finished when it is COMPLETED, FAILED or CANCELLED.
 */

const STEPS = ['connect', 'discover', 'mapping', 'dry-run', 'sync', 'verify', 'cut-over']

const STEP_LABELS = {
  connect: 'Connect',
  discover: 'Discover',
  mapping: 'Mapping',
  'dry-run': 'Dry run',
  sync: 'Sync',
  verify: 'Verify',
  'cut-over': 'Cut-over',
}

const FINISHED_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED']

const isFinished = (job) => FINISHED_STATUSES.includes(job.status)

// The newest job of a kind. Ids grow with each job, so the highest id is the newest.
const newestOf = (jobs, kind) => jobs
  .filter(job => job.kind === kind)
  .sort((a, b) => b.id - a.id)[0]

const hasFinished = (jobs, kind) => jobs.some(job => job.kind === kind && isFinished(job))

const hasAny = (jobs, kind) => jobs.some(job => job.kind === kind)

const includedOf = (mapping) => (mapping?.repositories ?? []).filter(repository => repository.included)

const trimmed = (value) => String(value ?? '').trim()

// Included rows whose target name is used by another included row.
const duplicateTargets = (mapping) => {
  const counts = new Map()
  includedOf(mapping).forEach(repository => {
    const name = trimmed(repository.targetName)
    if (name) counts.set(name, (counts.get(name) ?? 0) + 1)
  })
  return new Set([...counts.entries()].filter(([, count]) => count > 1).map(([name]) => name))
}

/*
 * Which step is open and why a closed one is closed. Sync, Verify and Cut-over depend on the jobs, so they
 * open after a reload without any page state. The other steps depend on what the page holds.
 */
const stepStates = (state, jobs) => {
  const included = includedOf(state.mapping)
  const duplicates = duplicateTargets(state.mapping)
  const dryRunReady = state.discovered && included.length > 0 && duplicates.size === 0
  let dryRunReason = 'Discover the repositories first.'
  if (state.discovered && included.length === 0) dryRunReason = 'Include at least one repository.'
  if (state.discovered && duplicates.size > 0) dryRunReason = 'Two included repositories share a target name.'

  return {
    connect: { open: true, reason: null },
    discover: { open: state.connectionTested === true, reason: 'Test the connection to Nexus first.' },
    mapping: { open: state.discovered === true, reason: 'Discover the repositories first.' },
    'dry-run': { open: dryRunReady, reason: dryRunReason },
    sync: { open: hasFinished(jobs, 'DRY_RUN') || hasAny(jobs, 'IMPORT'), reason: 'Finish a dry run first.' },
    verify: { open: hasAny(jobs, 'IMPORT'), reason: 'Start the import first.' },
    'cut-over': { open: hasFinished(jobs, 'VERIFY'), reason: 'Finish the verify job first.' },
  }
}

// The step the jobs of a reload open, or null when they open none (the page then starts at Connect).
const stepFromJobs = (jobs) => {
  const newestImport = newestOf(jobs, 'IMPORT')
  const newestVerify = newestOf(jobs, 'VERIFY')
  if (newestImport && !isFinished(newestImport)) return 'sync'
  if (newestVerify && !isFinished(newestVerify)) return 'verify'
  if (newestVerify && isFinished(newestVerify)) return 'cut-over'
  if (newestImport && isFinished(newestImport)) return 'verify'
  return null
}

/*
 * Where an address for a step goes. An open step stays. A closed one goes to the step the jobs open when
 * there is one, otherwise to the last open step before the first closed one, which is the step the visitor
 * should finish next. `reason` says why the requested step is closed.
 */
const stepTarget = (requested, state, jobs) => {
  const states = stepStates(state, jobs)
  if (states[requested]?.open) return { step: requested, redirected: false, fromJobs: false }
  const reason = states[requested]?.reason ?? null
  const fromJobs = stepFromJobs(jobs)
  if (fromJobs) return { step: fromJobs, redirected: true, reason, fromJobs: true }
  const firstClosed = STEPS.findIndex(step => !states[step].open)
  return { step: STEPS[Math.max(0, firstClosed - 1)], redirected: true, reason, fromJobs: false }
}

// What the first closed step needs, in words: the next thing the visitor has to finish. Null when every step is open.
const nextClosedReason = (state, jobs) => {
  const states = stepStates(state, jobs)
  const first = STEPS.find(step => !states[step].open)
  return first ? states[first].reason : null
}

/*
 * A text that changes with every choice the operator makes for the import: which repositories are included,
 * their target names and the conflict policy. The order of the rows does not matter.
 */
const planFingerprint = (plan) => JSON.stringify({
  policy: plan.conflictPolicy,
  repositories: includedOf(plan)
    .map(repository => [repository.nexusName, trimmed(repository.targetName)])
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)),
})

const acceptanceIsCurrent = (acceptance, plan, dryRunJobId) =>
  acceptance !== null
  && acceptance !== undefined
  && acceptance.dryRunJobId === dryRunJobId
  && acceptance.fingerprint === planFingerprint(plan)

// Warnings of the included rows: an empty target name, or a name that another included row also uses.
const mappingWarnings = (repositories) => {
  const duplicates = duplicateTargets({ repositories })
  return repositories
    .filter(repository => repository.included)
    .flatMap(repository => {
      const name = trimmed(repository.targetName)
      if (!name) return [{ nexusName: repository.nexusName, kind: 'empty' }]
      if (duplicates.has(name)) return [{ nexusName: repository.nexusName, kind: 'duplicate' }]
      return []
    })
}

/*
 * Why Sync cannot start. `ok` is true only when the list of reasons is empty: a finished dry run, its blockers
 * (each one named), an acceptance for this dry run and this mapping, and at least one included repository.
 */
const syncBlockers = ({ dryRun, acceptance, rows, conflictPolicy = 'refuse' }) => {
  const plan = { repositories: rows, conflictPolicy }
  const reasons = []
  const dryRunDone = dryRun !== null && dryRun !== undefined && dryRun.kind === 'DRY_RUN' && isFinished(dryRun)
  if (!dryRunDone) {
    reasons.push('Run a dry run first.')
  } else {
    reasons.push(...(dryRun.summary?.blockers ?? []))
    if (!acceptanceIsCurrent(acceptance, plan, dryRun.id)) {
      const stale = acceptance !== null && acceptance !== undefined && acceptance.dryRunJobId === dryRun.id
      reasons.push(stale
        ? 'The mapping changed after the loss report was accepted. Accept the report again.'
        : 'Accept the loss report for this mapping first.')
    }
  }
  if (includedOf(plan).length === 0) reasons.push('Include at least one repository.')
  return { ok: reasons.length === 0, reasons }
}

export {
  STEPS,
  STEP_LABELS,
  isFinished,
  stepStates,
  stepTarget,
  nextClosedReason,
  stepFromJobs,
  planFingerprint,
  acceptanceIsCurrent,
  mappingWarnings,
  syncBlockers,
}
