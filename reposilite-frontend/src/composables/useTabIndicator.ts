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

import { onBeforeUnmount, onMounted, ref, watch, type WatchSource } from 'vue'

/**
 * The indicator slide of a tab strip (design.md of add-dashboard-motion, "Indicator slide"). The strip draws one
 * indicator, one pixel wide and pinned to the left edge of the tab list. It is moved under the selected tab and stretched
 * to the tab's width with a transform, so the slide animates transform only and changes the box of no neighbour.
 */
export const indicatorTransform = (offset: number, width: number): string => `translateX(${offset}px) scaleX(${width})`

/**
 * Measures the selected tab whenever the selection changes, and again when a tab changes size (a web font that loads
 * late, a narrower window). `selected` returns the element of the selected tab, which must be laid out against the tab
 * list (the list is the offset parent of its tabs). `tabs` returns every tab, so a change of any width moves the
 * indicator with it. `moved` turns true at the first change of selection: the first paint places the indicator at once,
 * and only a later choice slides it.
 */
export const useTabIndicator = (
  selection: WatchSource<unknown>,
  selected: () => HTMLElement | undefined,
  tabs: () => HTMLElement[],
) => {
  const transform = ref<string>()
  const moved = ref(false)

  const measure = () => {
    const tab = selected()
    transform.value = tab ? indicatorTransform(tab.offsetLeft, tab.offsetWidth) : undefined
  }

  let observer: ResizeObserver | null = null
  const observe = () => {
    observer?.disconnect()
    if (typeof ResizeObserver === 'undefined') return
    observer = new ResizeObserver(measure)
    tabs().forEach((tab) => observer?.observe(tab))
  }

  // Places the indicator again and observes the tabs that are drawn now, for a strip whose tabs were replaced.
  const refresh = () => {
    measure()
    observe()
  }

  onMounted(refresh)
  watch(selection, () => {
    moved.value = true
    refresh()
  }, { flush: 'post' })
  onBeforeUnmount(() => observer?.disconnect())

  return { transform, moved, refresh }
}
