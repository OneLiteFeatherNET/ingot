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

// Like nodes.test.cjs, this runs with the runner that ships with Node: vitest refuses require().
// Run it with: node --test fake-api/wire.test.cjs
const { describe, it } = require("node:test")
const assert = require("node:assert/strict")
const { instantOnWire, localDateOnWire } = require("./wire.cjs")
const identity = require("./identity.cjs")
const { createFileDetails } = require("./extensions.cjs")

// The shapes ApiTimeWireFormatTest pins on the backend: an Instant is epoch seconds, a LocalDate a [y, m, d] array.
const NOON_UTC_SECONDS = Date.UTC(2026, 9, 15, 12) / 1000

describe("instantOnWire", () => {
  it("sends an instant as a number of epoch seconds", () => {
    assert.equal(instantOnWire("2026-10-15T12:00:00Z"), NOON_UTC_SECONDS)
  })

  it("sends an absent instant as null", () => {
    assert.equal(instantOnWire(null), null)
  })
})

describe("localDateOnWire", () => {
  it("sends a day as a year, month and day array", () => {
    assert.deepEqual(localDateOnWire("2026-10-04"), [2026, 10, 4])
  })

  it("keeps the day of a timestamp, as the backend keeps the date of its LocalDate", () => {
    assert.deepEqual(localDateOnWire("2026-10-04T23:30:00Z"), [2026, 10, 4])
  })
})

describe("identity views on the wire", () => {
  it("sends the creation time of a principal as epoch seconds", () => {
    const view = identity.wire.principalDto({
      id: 2, kind: "USER", name: "alice", displayName: "Alice", disabled: false,
      createdAt: "2026-10-15T12:00:00Z", lastSeenAt: null,
    })

    assert.equal(view.createdAt, NOON_UTC_SECONDS)
    assert.equal(view.lastSeenAt, null)
  })

  it("sends the expiry and the last use of a token as epoch seconds", () => {
    const view = identity.wire.tokenDto({
      name: "laptop", description: "", createdAt: "2026-10-04", expiresAt: "2026-10-15T12:00:00Z",
      scopes: null, lastUsedAt: "2026-10-15T12:00:00Z", lastUsedIp: null, nodes: [],
    })

    assert.equal(view.expiresAt, NOON_UTC_SECONDS)
    assert.equal(view.lastUsedAt, NOON_UTC_SECONDS)
  })

  it("sends the creation day of a token as a year, month and day array", () => {
    const view = identity.wire.tokenDto({
      name: "laptop", description: "", createdAt: "2026-10-04", expiresAt: null,
      scopes: null, lastUsedAt: null, lastUsedIp: null, nodes: [],
    })

    assert.deepEqual(view.createdAt, [2026, 10, 4])
  })

  it("sends the expiry of a login session as epoch seconds", () => {
    const body = identity.wire.startSession(
      { cookie() {} },
      { id: 2, kind: "USER", name: "alice", displayName: "Alice", mustChangePassword: false },
    )

    assert.equal(typeof body.expiresAt, "number")
  })
})

describe("file details on the wire", () => {
  it("sends the modification time of a stored file as epoch seconds", () => {
    const file = createFileDetails("app-1.0.jar", "application/jar-archive", 1337)

    assert.equal(typeof file.lastModifiedTime, "number")
    assert.equal(file.lastModifiedTime, Date.parse("2026-10-14T09:12:00Z") / 1000)
  })
})
