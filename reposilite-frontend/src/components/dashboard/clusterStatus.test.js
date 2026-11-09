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

import { describe, it, expect } from 'vitest'
import { summariseCluster } from './clusterStatus'

// The clock is a plain number passed in, so no test reads the system time.
const NOW = Date.UTC(2026, 10, 8, 12, 0, 0)
const HOUR = 60 * 60 * 1000
const MINUTE = 60 * 1000

const instance = (overrides = {}) => ({
  id: 'ingot-0',
  version: '1.3.0',
  uptimeStart: NOW - 2 * HOUR,
  usedMemory: 100,
  maxMemory: 512,
  usedThreads: 3,
  maxThreads: 20,
  failuresCount: 0,
  health: 'up',
  stale: false,
  interval: 60000,
  samples: [],
  ...overrides
})

describe('summariseCluster', () => {
  it('sums memory, threads and failure counts across all instances', () => {
    const summary = summariseCluster([
      instance({ usedMemory: 100, maxMemory: 512, usedThreads: 3, maxThreads: 20, failuresCount: 1 }),
      instance({ id: 'ingot-1', usedMemory: 151.5, maxMemory: 512, usedThreads: 5, maxThreads: 40, failuresCount: 2 })
    ], NOW)

    expect(summary.usedMemory, 'used memory is the sum of both instances').toBe(251.5)
    expect(summary.maxMemory, 'maximum memory is the sum of both instances').toBe(1024)
    expect(summary.usedThreads, 'used threads is the sum of both instances').toBe(8)
    expect(summary.maxThreads, 'maximum threads is the sum of both instances').toBe(60)
    expect(summary.failuresCount, 'failures are summed as a count').toBe(3)
  })

  it('keeps a stale instance in the sums, because it is still listed until it is dropped', () => {
    const summary = summariseCluster([
      instance({ usedMemory: 100 }),
      instance({ id: 'ingot-1', usedMemory: 50, stale: true })
    ], NOW)

    expect(summary.usedMemory, 'a stale entry stays visible, so it stays in the sum').toBe(150)
    expect(summary.staleCount, 'the stale entry is counted as stale').toBe(1)
  })

  it('shows the shortest uptime, which belongs to the most recent restart', () => {
    const summary = summariseCluster([
      instance({ uptimeStart: NOW - 2 * HOUR }),
      instance({ id: 'ingot-1', uptimeStart: NOW - 30 * MINUTE })
    ], NOW)

    expect(summary.uptime, 'the instance restarted 30 minutes ago sets the shortest uptime').toBe(30 * MINUTE)
  })

  it('reports no uptime when there are no instances', () => {
    const summary = summariseCluster([], NOW)

    expect(summary.uptime, 'an empty cluster has no uptime to show').toBeNull()
    expect(summary.instanceCount).toBe(0)
  })

  it('never reports a negative uptime for a restart that is in the future', () => {
    const summary = summariseCluster([instance({ uptimeStart: NOW + MINUTE })], NOW)

    expect(summary.uptime, 'clock skew between instances must not show a negative uptime').toBe(0)
  })

  it('shows the single version when every instance runs it', () => {
    const summary = summariseCluster([
      instance({ version: '1.3.0' }),
      instance({ id: 'ingot-1', version: '1.3.0' })
    ], NOW)

    expect(summary.version).toEqual({ label: '1.3.0', versions: ['1.3.0'], mixed: false })
  })

  it('shows mixed with every version listed once when the instances disagree', () => {
    const summary = summariseCluster([
      instance({ version: '1.2.0' }),
      instance({ id: 'ingot-1', version: '1.3.0' }),
      instance({ id: 'ingot-2', version: '1.2.0' })
    ], NOW)

    expect(summary.version).toEqual({ label: 'mixed', versions: ['1.2.0', '1.3.0'], mixed: true })
  })

  it('takes the health of the worst instance', () => {
    const summary = summariseCluster([
      instance({ health: 'up' }),
      instance({ id: 'ingot-1', health: 'down' }),
      instance({ id: 'ingot-2', health: 'unknown' })
    ], NOW)

    expect(summary.health, 'one unhealthy instance makes the cluster unhealthy').toBe('down')
  })

  it('ranks unknown health above healthy but below unhealthy', () => {
    const summary = summariseCluster([
      instance({ health: 'up' }),
      instance({ id: 'ingot-1', health: 'unknown' })
    ], NOW)

    expect(summary.health).toBe('unknown')
  })

  it('reports healthy when every instance is healthy', () => {
    const summary = summariseCluster([
      instance({ health: 'up' }),
      instance({ id: 'ingot-1', health: 'up' })
    ], NOW)

    expect(summary.health).toBe('up')
  })

  it('reports no health when there are no instances', () => {
    expect(summariseCluster([], NOW).health).toBeNull()
  })
})
