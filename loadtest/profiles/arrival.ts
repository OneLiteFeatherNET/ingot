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
// The virtual user sizing of the open-model executors (design D2). Pure: no k6 runtime and no clock.

/** Arrival rates are given in requests per second, so every executor counts its rate in seconds. */
export const ARRIVAL_TIME_UNIT = '1s';

export interface ArrivalVus {
  /** Virtual users started before the test, so that the first iterations do not wait for allocation. */
  readonly preAllocatedVUs: number;
  /** The most virtual users the executor may add when iterations run slow. */
  readonly maxVUs: number;
}

/**
 * Sizes the virtual users of an arrival rate: one per request per second, and up to four times that when the server
 * answers slowly. Provisional until the first measured run (design D3); a too small pool shows up as dropped iterations
 * and would be read as a server limit, so the pool is kept generous.
 */
export function arrivalVus(rate: number): ArrivalVus {
  if (!Number.isFinite(rate) || rate < 0) {
    throw new Error(`refusing an arrival rate of ${rate}: a rate is a finite number of requests per second`);
  }
  const preAllocatedVUs = Math.max(1, Math.ceil(rate));
  return { preAllocatedVUs, maxVUs: preAllocatedVUs * 4 };
}
