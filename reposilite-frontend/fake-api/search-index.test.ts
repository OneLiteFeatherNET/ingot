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
 * Tests of the index administration of the fake backend. They use the test runner that ships with Node:
 *   node --import tsx --test fake-api/search-index.test.ts
 * Every test builds its own index over a clock it moves by hand and a scheduler that runs when the test says so.
 */

import { describe, it } from "node:test"
import assert from "node:assert/strict"
import { createIndexAdmin } from "./search-index"
import type { Accepted, Message, StatusOk } from "./search-index"

const setup = ({ state = "ready", repositories = ["snapshots", "releases", "mirror"] }: { state?: string; repositories?: string[] } = {}) => {
  let clock = Date.parse("2026-10-21T08:00:00Z")
  const pending: (() => void)[] = []
  const admin = createIndexAdmin({
    repositories: () => repositories,
    state: () => state,
    now: () => clock,
    schedule: (fn) => pending.push(fn),
  })
  // Runs the next scheduled step, which has to exist.
  const runNext = () => {
    const task = pending.shift()
    if (task === undefined) throw new Error("no scheduled step is left")
    task()
  }
  // Runs the scheduled steps until none is left, advancing the clock by one step each time.
  const runAll = () => {
    while (pending.length) {
      clock += 1500
      runNext()
    }
  }
  // Runs exactly one scheduled step, advancing the clock by one step.
  const runOne = () => {
    clock += 1500
    runNext()
  }
  return { admin, runAll, runOne }
}

const ok = (answer: StatusOk | Message): StatusOk => {
  assert.ok(answer.status === 200, "the status answers 200")
  return answer
}
const accepted = (answer: Accepted | Message): Accepted => {
  assert.ok(answer.status === 202, "the request is accepted")
  return answer
}
const failed = (answer: Accepted | Message): Message => {
  assert.ok(answer.status === 400 || answer.status === 404 || answer.status === 409, "the answer is an error")
  return answer
}
const liveRun = (answer: StatusOk | Message) => {
  const run = ok(answer).body.activeRun
  assert.ok(run, "a run is live")
  return run
}

describe("index status", () => {
  it("reports the configured repositories in name order, with no run yet", () => {
    const { admin } = setup()

    const { status, body } = ok(admin.status())

    assert.equal(status, 200)
    assert.deepEqual(body.repositories, ["mirror", "releases", "snapshots"])
    assert.equal(body.activeRun, null)
    assert.deepEqual(body.runs, [])
  })

  it("sends the instants as epoch seconds with a fraction", () => {
    const { admin } = setup()

    const { body } = ok(admin.status())

    assert.equal(body.lastFullRebuild, Date.parse("2026-10-20T03:30:12Z") / 1000)
  })

  it("reports degraded with lag and drift in the last verify when the state is degraded", () => {
    const { admin, runAll } = setup({ state: "degraded" })
    admin.startVerify()
    runAll()

    const { body } = ok(admin.status())

    assert.equal(body.state, "DEGRADED")
    assert.equal(body.lagSeconds, 420)
    assert.ok(body.lastVerify, "a drift report is published")
    assert.ok(body.lastVerify.drift > 0.05, "the drift is above the threshold")
  })

  it("answers 404 on every route when search is disabled", () => {
    const { admin } = setup({ state: "disabled" })

    assert.equal(admin.status().status, 404)
    assert.equal(admin.startRebuild({}).status, 404)
    assert.equal(admin.startVerify().status, 404)
    assert.equal(admin.cancel(1).status, 404)
  })

  it("starts with a startup rebuild live while the state is building", () => {
    const { admin } = setup({ state: "building" })

    const { body } = ok(admin.status())

    assert.equal(body.state, "BUILDING")
    assert.equal(liveRun(admin.status()).trigger, "STARTUP")
  })
})

describe("starting a rebuild", () => {
  it("answers 202 with the id of the new run, which is live", () => {
    const { admin } = setup()

    const { status, body } = accepted(admin.startRebuild({}))

    assert.equal(status, 202)
    assert.equal(liveRun(admin.status()).id, body.runId)
    assert.equal(liveRun(admin.status()).status, "RUNNING")
  })

  it("answers 409 while a run is live", () => {
    const { admin } = setup()
    admin.startRebuild({})

    assert.equal(admin.startRebuild({}).status, 409)
    assert.equal(admin.startVerify().status, 409)
  })

  it("answers 400 for a repository that is not configured", () => {
    const { admin } = setup()

    const { status, body } = failed(admin.startRebuild({ repositories: ["nope"] }))

    assert.equal(status, 400)
    assert.match(body.message, /unknown repository 'nope'/)
  })

  it("walks the repositories by name, one after another, and completes", () => {
    const { admin, runOne } = setup()
    const before = ok(admin.status()).body.lastFullRebuild
    admin.startRebuild({})
    const visited: string[] = []
    for (let steps = 0; ok(admin.status()).body.activeRun && steps < 20; steps++) {
      const cursor = liveRun(admin.status()).cursorRepository
      if (cursor && visited.at(-1) !== cursor) visited.push(cursor)
      runOne()
    }

    const { body } = ok(admin.status())

    assert.deepEqual(visited, ["mirror", "releases", "snapshots"])
    assert.equal(body.activeRun, null)
    assert.equal(body.runs[0].status, "COMPLETED")
    assert.ok(body.lastFullRebuild > before, "a full rebuild moves the last full rebuild")
  })

  it("rebuilds one repository without moving the last full rebuild", () => {
    const { admin, runAll } = setup()
    const before = ok(admin.status()).body.lastFullRebuild
    admin.startRebuild({ repositories: ["releases"] })
    runAll()

    const { body } = ok(admin.status())

    assert.ok(body.runs[0].repositories, "the run names its repositories")
    assert.equal(body.runs[0].repositories[0], "releases")
    assert.equal(body.lastFullRebuild, before)
  })
})

describe("verify", () => {
  it("publishes its drift report when it completes", () => {
    const { admin, runAll } = setup()
    admin.startVerify()
    runAll()

    const { body } = ok(admin.status())

    assert.equal(body.runs[0].kind, "VERIFY")
    assert.ok(body.lastVerify, "a drift report is published")
    assert.equal(body.lastVerify.sampled, 2000)
    assert.equal(body.lastVerify.runId, body.runs[0].id)
  })
})

describe("cancel", () => {
  it("cancels a live run, which then answers as cancelled", () => {
    const { admin } = setup()
    const { body } = accepted(admin.startRebuild({}))

    assert.equal(admin.cancel(body.runId).status, 204)
    assert.equal(ok(admin.status()).body.activeRun, null)
    assert.equal(ok(admin.status()).body.runs[0].status, "CANCELLED")
  })

  it("answers 409 for a run that has already finished", () => {
    const { admin, runAll } = setup()
    const { body } = accepted(admin.startVerify())
    runAll()

    assert.equal(admin.cancel(body.runId).status, 409)
  })

  it("answers 404 for a run that does not exist", () => {
    const { admin } = setup()

    assert.equal(admin.cancel(999).status, 404)
  })
})
