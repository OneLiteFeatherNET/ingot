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

// The shapes of the Nexus migration as the wizard holds them. The migration endpoints are not part of the OpenAPI
// document behind src/types/api.ts, so these are written by hand and follow the server's JSON field names.

// A job of the migration. The kind is DRY_RUN, IMPORT or VERIFY; the status is one of the server's job states.
export interface MigrationJob {
  id: number
  kind: string
  status: string
  summary?: { blockers?: string[] } | null
  [field: string]: unknown
}

// The connection to the Nexus instance. A stored password is held by the page only, never in the store state.
export interface NexusConnection {
  [field: string]: unknown
  url: string
  user: string
  passwordMode?: string
  reference?: string
  password?: string
}

// One row of the mapping: a Nexus repository, whether it is imported and the name it gets on the Ingot side.
export interface MappingRow {
  nexusName: string
  targetName?: string | null
  included: boolean
  [field: string]: unknown
}

export interface Mapping {
  repositories: MappingRow[]
  conflictPolicy: string
}

// The clock and the tab visibility that the poller reads from its caller, so the tests drive the schedule.
export interface PollerTimers {
  setTimeout(callback: () => void, ms: number): unknown
  clearTimeout(handle: unknown): void
}

export interface PollerVisibility {
  isHidden(): boolean
  subscribe(listener: () => void): () => void
}

// The error of a failed read. The status is 0 when no answer came at all.
export interface PollFailure {
  status?: number
}

export interface PollerOptions<T> {
  fetch: () => Promise<T>
  timers: PollerTimers
  visibility: PollerVisibility
  isFinished?: (answer: T) => boolean
  same?: (a: T, b: T) => boolean
  onUpdate?: (answer: T) => void
  onFinished?: (answer: T) => void
  onError?: (error: unknown) => void
  onUnauthorized?: (error: unknown) => void
  onStopped?: (error: unknown) => void
}

// The operator's acceptance of the loss report of one dry run, for one exact mapping (see planFingerprint).
export interface Acceptance {
  dryRunJobId: number
  fingerprint: string
}

// The facts of the page that the step gating reads.
export interface WizardFacts {
  connectionTested: boolean
  discovered: boolean
  mapping?: Mapping | null
}

// The reply of the discovery call. Its fields are read by the discover step only.
export type DiscoveryReport = Record<string, unknown>

// The password holder of the wizard: the password lives in memory for the visit and nowhere else.
export interface PasswordHolder {
  remember(password: string | null | undefined): void
  recall(): string | null
  forget(): void
}
