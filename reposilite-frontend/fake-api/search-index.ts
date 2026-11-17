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
 * The index administration of the fake backend (spec section 6.3): GET /api/maven/search/index, the rebuild,
 * verify and cancel endpoints. The shapes are the wire format of the admin status: Instants as epoch seconds
 * with a fraction, the run counters of the backend's RunCounters, and the last verify report of the verify
 * state key. The runs advance on a scheduler the caller passes in, and the instants come from a clock the caller
 * passes in, so a test drives the runs by hand and nothing reads the real time.
 *
 * The index state is the one of the search (`ready`, `building`, `missing`, `degraded`); `disabled` answers 404
 * on every route, as with INGOT_LOCAL_SEARCHENABLED=false (section 6.4).
 */

const NOT_FOUND = { status: 404, body: { status: 404, message: "Not found" } }
const RUN_HISTORY = 20
const LEASE_MS = 120000
const STEP_MS = 1500
const ROWS_PER_STEP = 500
const FAKE_ROWS = 1834
const FAKE_LAST_REBUILD_SECONDS = Date.parse("2026-10-20T03:30:12Z") / 1000

const STATES = {
  ready: { state: "READY", lagSeconds: 0, drift: 0.001, missing: 2, changed: 0 },
  building: { state: "BUILDING", lagSeconds: 0, drift: 0.001, missing: 2, changed: 0 },
  missing: { state: "MISSING", lagSeconds: 0, drift: 0.001, missing: 2, changed: 0 },
  degraded: { state: "DEGRADED", lagSeconds: 420, drift: 0.07, missing: 90, changed: 50 },
}

const zeroCounters = () => ({ processed: 0, upserted: 0, deleted: 0, unparsed: 0, skippedTooLong: 0, errors: 0 })

// An Instant on the wire: epoch seconds with a fraction, as Jackson writes java.time.Instant.
const seconds = (ms) => (ms === null || ms === undefined ? null : ms / 1000)

const badRequest = (message) => ({ status: 400, body: { status: 400, message } })
const conflict = (message) => ({ status: 409, body: { status: 409, message } })

const createIndexAdmin = ({ repositories, state = () => "ready", now, schedule }) => {
  const indexState = STATES[state()] ?? STATES.ready
  const disabled = state() === "disabled"
  let sequence = 0
  let runs = []
  let activeRun = null
  let lastFullRebuild = FAKE_LAST_REBUILD_SECONDS * 1000
  let lastVerify = null

  const names = () => [...repositories()].sort()

  const wire = (run) => ({
    id: run.id,
    kind: run.kind,
    trigger: run.trigger,
    status: run.status,
    startedAt: seconds(run.startedAt),
    finishedAt: seconds(run.finishedAt),
    leaseOwner: run.status === "RUNNING" ? run.leaseOwner : null,
    leaseUntil: run.status === "RUNNING" ? seconds(run.leaseUntil) : null,
    repositories: run.repositories,
    cursorRepository: run.cursorRepository,
    cursorPath: run.cursorPath,
    counters: { ...run.counters },
    lastError: run.lastError,
  })

  const finish = (run, status, at) => {
    run.status = status
    run.finishedAt = at
    run.leaseUntil = null
    if (activeRun === run) activeRun = null
  }

  // One step of a rebuild: a repository is first entered (its cursor path is set), then marked done. A verify
  // run needs one step and then publishes its report.
  const step = (run) => {
    if (run.status !== "RUNNING") return
    if (run.kind === "VERIFY") {
      lastVerify = {
        runId: run.id,
        at: seconds(now()),
        sampled: 2000,
        missing: indexState.missing,
        changed: indexState.changed,
        reorigined: 0,
        removedRepositoryRows: 0,
        drift: indexState.drift,
      }
      run.counters = { ...run.counters, processed: 2000, deleted: indexState.missing, upserted: indexState.changed }
      finish(run, "COMPLETED", now())
      return
    }
    const targets = run.targets
    const index = Math.floor(run.stepCount / 2)
    if (index >= targets.length) {
      if (run.repositories === null) lastFullRebuild = now()
      finish(run, "COMPLETED", now())
      return
    }
    const repository = targets[index]
    run.cursorRepository = repository
    run.cursorPath = run.stepCount % 2 === 0 ? `${repository}/org/example/1.0/example-1.0.jar` : "//done"
    if (run.stepCount % 2 === 0) run.counters = { ...run.counters, processed: run.counters.processed + ROWS_PER_STEP, upserted: run.counters.upserted + ROWS_PER_STEP - 20, unparsed: run.counters.unparsed + 20 }
    run.stepCount += 1
    schedule(() => step(run), STEP_MS)
  }

  const startRun = ({ kind, trigger, repositoryNames = null }) => {
    const at = now()
    const run = {
      id: ++sequence,
      kind,
      trigger,
      status: "RUNNING",
      startedAt: at,
      finishedAt: null,
      leaseOwner: `fake-ingot#${sequence}`,
      leaseUntil: at + LEASE_MS,
      repositories: repositoryNames,
      targets: kind === "REBUILD" ? (repositoryNames ?? names()) : [],
      cursorRepository: null,
      cursorPath: null,
      counters: zeroCounters(),
      lastError: null,
      stepCount: 0,
    }
    activeRun = run
    runs = [run, ...runs].slice(0, RUN_HISTORY)
    schedule(() => step(run), STEP_MS)
    return run
  }

  if (!disabled && state() === "building") startRun({ kind: "REBUILD", trigger: "STARTUP" })

  const status = () => {
    if (disabled) return NOT_FOUND
    return {
      status: 200,
      body: {
        state: indexState.state,
        lagSeconds: indexState.lagSeconds,
        lastFullRebuild: seconds(lastFullRebuild),
        rows: FAKE_ROWS,
        outbox: { pending: state() === "building" ? 12 : 0, deadLetters: indexState.state === "DEGRADED" ? 2 : 0, droppedEnqueues: 0 },
        repositories: names(),
        engine: "database",
        activeRun: activeRun ? wire(activeRun) : null,
        runs: runs.map(wire),
        lastVerify,
      },
    }
  }

  const startRebuild = (body) => {
    if (disabled) return NOT_FOUND
    if (activeRun) return conflict("A rebuild or verify run is already running")
    const requested = body?.repositories
    if (requested !== undefined) {
      if (!Array.isArray(requested) || requested.length === 0) return badRequest("Invalid parameter 'repositories': at least one repository is required")
      const configured = new Set(names())
      const unknown = requested.find(name => !configured.has(name))
      if (unknown !== undefined) return badRequest(`Invalid parameter 'repositories': unknown repository '${unknown}'`)
    }
    const run = startRun({ kind: "REBUILD", trigger: "MANUAL", repositoryNames: requested ? [...new Set(requested)].sort() : null })
    return { status: 202, body: { runId: run.id } }
  }

  const startVerify = () => {
    if (disabled) return NOT_FOUND
    if (activeRun) return conflict("A rebuild or verify run is already running")
    const run = startRun({ kind: "VERIFY", trigger: "MANUAL" })
    return { status: 202, body: { runId: run.id } }
  }

  const cancel = (id) => {
    if (disabled) return NOT_FOUND
    const run = runs.find(entry => entry.id === Number(id))
    if (!run) return NOT_FOUND
    if (run.status !== "RUNNING") return conflict("The run has already finished")
    finish(run, "CANCELLED", now())
    return { status: 204, body: null }
  }

  return { status, startRebuild, startVerify, cancel }
}

module.exports = { createIndexAdmin }
