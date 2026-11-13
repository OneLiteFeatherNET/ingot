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

import { inject, onBeforeUnmount, ref, watch } from 'vue'
import { TOP_BAR_HEIGHT, TREE_MARGIN, treeFits } from './treeFits'

/*
 * Measures whether the Administration tree fits between the bars (see treeFits.js). The measurement runs when the tree
 * is mounted, when the tree or the window changes size, and when the caller asks for it; never on scroll, so the tree
 * does not switch between sticky and static under the pointer.
 *
 * The size observer is injected under `treeObserverKey`, so a test can pass a fake that it triggers by hand. The default
 * observes the tree with a ResizeObserver and the window with a resize listener.
 */
export const treeObserverKey = Symbol('treeObserver')

// The sticky wrapper of the save bar, drawn by SettingsView while something is unsaved.
const SAVE_BAR = '.settings-save-bar'

export const observeTree = (element, onChange) => {
  const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(onChange)
  observer?.observe(element)
  window.addEventListener('resize', onChange)
  return () => {
    observer?.disconnect()
    window.removeEventListener('resize', onChange)
  }
}

// `getElement` returns the element of the tree, or nothing while the tree is not drawn. The result `fits` is true when
// the tree may be sticky; `measure` takes the decision again, and the caller calls it when the save bar appears.
export const useTreeFits = (getElement) => {
  const observe = inject(treeObserverKey, observeTree)
  const fits = ref(false)
  let stopObserving = null

  const measure = () => {
    const element = getElement()
    if (!element) {
      fits.value = false
      return
    }
    const bar = document.querySelector(SAVE_BAR)
    fits.value = treeFits({
      navHeight: element.offsetHeight,
      viewportHeight: window.innerHeight,
      topBarHeight: TOP_BAR_HEIGHT,
      saveBarHeight: bar ? bar.offsetHeight + TREE_MARGIN : 0,
      margin: TREE_MARGIN
    })
  }

  watch(getElement, (element) => {
    stopObserving?.()
    stopObserving = element ? observe(element, measure) : null
    measure()
  }, { immediate: true, flush: 'post' })

  onBeforeUnmount(() => stopObserving?.())

  return { fits, measure }
}
