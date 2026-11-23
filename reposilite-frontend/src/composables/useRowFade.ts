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

import { watch } from 'vue'

/*
 * Tells a list which of its rows were added after the list had loaded, so that only those fade in (the motion-row-enter
 * class of src/styles/md3/motion-transitions.css). The rows of the initial load, the rows that only change their order
 * and the rows below a removed row are never marked, and a removed row is simply gone. Reduced motion is left to the
 * single rule of src/style.css, which makes the fade an instant swap.
 *
 * keys   the keys of the rows as they are shown now
 * ready  false while the list loads; the rows present when it turns true are the initial load, and a list that goes
 *        back to false starts over, so the rows of another directory do not fade either
 */
export const useRowFade = (keys: () => readonly string[], ready: () => boolean = () => true) => {
  // The rows the list has shown since its load, and the ones among them that were added later. Plain sets on purpose:
  // the watcher runs before the render of the same change, so a row reads its mark in the render that draws it.
  let known: Set<string> | null = null
  let added = new Set<string>()

  watch(
    () => [ready(), keys()] as const,
    ([isReady, current]) => {
      if (!isReady) {
        known = null
        added = new Set()
        return
      }
      if (known === null) {
        known = new Set(current)
        return
      }
      const present = new Set(current)
      for (const key of current) {
        if (!known.has(key)) added.add(key)
      }
      for (const key of added) {
        if (!present.has(key)) added.delete(key)
      }
      known = present
    },
    { immediate: true }
  )

  return { isAdded: (key: string): boolean => added.has(key) }
}
