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
 * The selected row of a list pane. The list asks once per render which of its rows belongs to the
 * open route, and each row only reads the answer, so two rows can never both claim to be selected.
 */

/** The key of the row that is open, or null when the route names no row of the list. */
export const selectedKey = <T>(items: readonly T[], keyOf: (item: T) => string, current: string | undefined | null): string | null => {
  if (current === undefined || current === null) return null
  const match = items.find(item => keyOf(item) === current)
  return match === undefined ? null : keyOf(match)
}
