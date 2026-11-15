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

/**
 * Which of the five states the resolved-requests card is in.
 *
 * A failed request is an error and nothing else. Only a reply that says `statisticsEnabled`
 * is false may be shown as "disabled": reporting a network failure as a switched-off feature
 * sends the reader to the settings page to fix something that is not broken.
 *
 * @param {{ enabled: boolean | null, failed: boolean, series: unknown[] | null }} input
 *   `enabled` is null until the server has answered.
 * @returns {'error' | 'loading' | 'disabled' | 'empty' | 'ready'}
 */
export const statisticsState = ({ enabled, failed, series }) => {
  if (failed) return 'error'
  if (enabled === null || enabled === undefined) return 'loading'
  if (!enabled) return 'disabled'
  return series?.length ? 'ready' : 'empty'
}
