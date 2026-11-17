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
// Shared response checks. Each returns a predicate that k6's check() can run; no k6 module is imported, so the
// predicates are plain functions that the Node unit tests can call.

export interface StatusResponse {
  readonly status: number;
}

export function statusIs(expected: number): (response: StatusResponse) => boolean {
  return (response) => response.status === expected;
}

export function statusIn(expected: readonly number[]): (response: StatusResponse) => boolean {
  return (response) => expected.includes(response.status);
}
