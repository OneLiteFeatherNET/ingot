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
 * Consumer contract for the cluster view of the instance status (GET /api/status/instance).
 *
 * The existing fields keep their names and meaning. `cluster` is an additional field, and it is
 * absent when no cluster bus is configured, so a client that reads only the existing fields gets
 * the same response as before. Assumed JSON, matched by the backend:
 *
 * {
 *   "usedMemory": 151.5,
 *   "maxMemory": 512,
 *   "usedThreads": 12,
 *   "maxThreads": 40,
 *   "uptime": 90000,
 *   "version": "1.3.0",
 *   "latestVersion": "1.3.0",
 *   "failuresCount": 0,
 *   "cluster": {
 *     "instances": [
 *       {
 *         "id": "ingot-0",
 *         "version": "1.3.0",
 *         "uptimeStart": 1762596000000,
 *         "usedMemory": 151.5,
 *         "maxMemory": 512,
 *         "usedThreads": 12,
 *         "maxThreads": 40,
 *         "failuresCount": 0,
 *         "health": "up",
 *         "stale": false,
 *         "interval": 60000,
 *         "samples": [
 *           { "at": 1762596000000, "memory": 151.5, "threads": 12 }
 *         ]
 *       }
 *     ]
 *   }
 * }
 *
 * Field notes:
 * - uptimeStart: epoch milliseconds of the last restart of that instance.
 * - memory values: megabytes. thread values: counts.
 * - health: "up" | "down" | "unknown", the health of that instance.
 * - stale: true after one interval without a snapshot. Instances older than three intervals are
 *   dropped by the backend and do not appear in the list.
 * - interval: milliseconds between two snapshots of that instance.
 * - samples: the same shape as GET /api/status/snapshots, oldest first, epoch milliseconds in "at".
 *
 * The summary is computed here from `instances`, not sent by the backend.
 */

const HEALTH_RANK = { up: 0, unknown: 1, down: 2 }

export const HEALTH_LABEL = { up: 'Healthy', unknown: 'Health unavailable', down: 'Unhealthy' }

/** The label for a health state. Anything the backend did not name is shown as unknown. */
export const healthLabel = (state) => HEALTH_LABEL[state] ?? HEALTH_LABEL.unknown

/** Time since the instance last restarted, never negative, so clock skew cannot show a negative uptime. */
export const uptimeOf = (instance, now) => Math.max(0, now - instance.uptimeStart)

const sumOf = (instances, key) => instances.reduce((total, instance) => total + instance[key], 0)

// The worst instance decides. The first one seeds the comparison, so an empty list has no health.
const worstHealth = (instances) => instances.length === 0
  ? null
  : instances.reduce(
    (worst, { health }) => (HEALTH_RANK[health] > HEALTH_RANK[worst] ? health : worst),
    instances[0].health
  )

/*
 * The cluster summary, by fixed rules: memory and threads are summed, failures are summed as a
 * count, the uptime is the shortest one (the most recent restart), the version is the single
 * version or "mixed" with every version listed once, and the health is the worst instance's.
 * Stale instances are still listed, so they stay in the sums as well.
 *
 * @param {object[]} instances the `cluster.instances` of the status response
 * @param {number} now the current time in epoch milliseconds, passed in so the rule is pure
 */
export const summariseCluster = (instances, now) => {
  const versions = [...new Set(instances.map(instance => instance.version))]
  const shortest = instances.length === 0
    ? null
    : Math.min(...instances.map(instance => uptimeOf(instance, now)))

  return {
    instanceCount: instances.length,
    staleCount: instances.filter(instance => instance.stale).length,
    usedMemory: sumOf(instances, 'usedMemory'),
    maxMemory: sumOf(instances, 'maxMemory'),
    usedThreads: sumOf(instances, 'usedThreads'),
    maxThreads: sumOf(instances, 'maxThreads'),
    failuresCount: sumOf(instances, 'failuresCount'),
    uptime: shortest,
    version: {
      label: versions.length > 1 ? 'mixed' : (versions[0] ?? ''),
      versions,
      mixed: versions.length > 1
    },
    health: worstHealth(instances)
  }
}
