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
 * Whether the repository list shows the write policy and the visibility of each row. The choice belongs to
 * this viewer, so it is kept in localStorage. Storage can be blocked, full or missing, so every access is
 * guarded: a failure keeps the details hidden, the default, and the page still works.
 */
export const LIST_DETAILS_KEY = 'repository-list-details'

const readListDetails = (): boolean => {
  try {
    return globalThis.localStorage.getItem(LIST_DETAILS_KEY) === 'true'
  } catch {
    // Storage is blocked: the details stay hidden.
    return false
  }
}

const writeListDetails = (shown: boolean): void => {
  try {
    globalThis.localStorage.setItem(LIST_DETAILS_KEY, String(shown))
  } catch {
    // Storage is blocked or full: the choice lasts until the page is left.
  }
}

export {
  readListDetails,
  writeListDetails
}
