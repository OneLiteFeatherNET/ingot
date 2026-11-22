<!--
  - Copyright (c) 2026 OneLiteFeather
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<script setup lang="ts">
/*
 * A tab strip that follows the WAI-ARIA tabs pattern: a tablist, one tab per entry with aria-selected, a roving
 * tabindex, and Left, Right, Home and End to move between the tabs. The panels are the slot `default`, scoped by the tab.
 *
 * Props:
 *   tabs        the entries, { key, label }, in order
 *   label       the accessible name of the tablist
 *   modelValue  the key of the selected tab (v-model). Without it the first tab is selected.
 *   queryParam  the query parameter that holds the selected key. Off by default, so the tabs do not touch the address.
 *               When a name is given and a router is installed, choosing a tab replaces the history entry.
 * Emits update:modelValue with the key of the tab that was chosen.
 */
import { computed, inject, onMounted, ref, useId, watch } from 'vue'
import { routeLocationKey, routerKey } from 'vue-router'
import { useTabIndicator } from '../../composables/useTabIndicator'
import { motionDirection, type MotionDirection } from '../../helpers/motionDirection'
import { vHidden } from '../../helpers/vHidden'
import MdTransition from './MdTransition.vue'

export interface AppTab {
  key: string
  label: string
}

const props = withDefaults(
  defineProps<{
    tabs: AppTab[]
    label: string
    modelValue?: string
    queryParam?: string | null
  }>(),
  { modelValue: undefined, queryParam: null },
)

const emit = defineEmits<{ 'update:modelValue': [key: string] }>()

// Injected without a default warning, so the component also mounts where no router is installed.
const router = inject(routerKey, null)
const route = inject(routeLocationKey, null)
const uid = useId()
const buttons = new Map<string, HTMLElement>()

const addressName = computed(() => (router && route && props.queryParam ? props.queryParam : null))
const requested = computed(() => {
  const name = addressName.value
  return name && route ? route.query[name] : props.modelValue
})
const selected = computed(() => {
  const key = props.tabs.find((tab) => tab.key === requested.value)?.key
  return key ?? props.tabs[0]?.key
})

const tabId = (key: string) => `${uid}-tab-${key}`
const panelId = (key: string) => `${uid}-panel-${key}`

const choose = (key: string) => {
  emit('update:modelValue', key)
  const name = addressName.value
  if (name && router && route && route.query[name] !== key) {
    void router.replace({ path: route.path, query: { ...route.query, [name]: key }, hash: route.hash })
  }
}

const focusAt = (index: number) => {
  const count = props.tabs.length
  const target = props.tabs[(index + count) % count]
  choose(target.key)
  // preventScroll: the list is scrolled sideways by the selection, and focus must not scroll the page.
  buttons.get(target.key)?.focus({ preventScroll: true })
}

// Right and Left wrap at the ends. Home and End go to the first and the last tab. Every other key is left alone.
const onKeydown = (event: KeyboardEvent, index: number) => {
  const last = props.tabs.length - 1
  const moves: Record<string, number> = {
    ArrowRight: index === last ? 0 : index + 1,
    ArrowLeft: index === 0 ? last : index - 1,
    Home: 0,
    End: last,
  }
  const target = moves[event.key]
  if (target === undefined) return
  event.preventDefault()
  focusAt(target)
}

const remember = (key: string, element: unknown) => {
  if (element instanceof HTMLElement) buttons.set(key, element)
}

// A tab row that is wider than the screen scrolls sideways. The selected tab is brought fully into view on the first
// render (a deep link such as ?tab=configuration) and whenever the selection changes. Only the tab list moves: the page
// keeps its vertical position, because scrollIntoView would scroll every ancestor, the page included.
const listRef = ref<HTMLElement | null>(null)

const reducedMotion = () =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const showSelectedTab = (behavior: 'auto' | 'smooth') => {
  const key = selected.value
  const list = listRef.value
  const tab = key ? buttons.get(key) : undefined
  if (!list || !tab) return
  const bounds = list.getBoundingClientRect()
  const box = tab.getBoundingClientRect()
  let offset = 0
  if (box.left < bounds.left) offset = box.left - bounds.left
  else if (box.right > bounds.right) offset = box.right - bounds.right
  if (offset === 0) return
  list.scrollTo?.({ left: list.scrollLeft + offset, behavior })
}

// One indicator for the whole strip, moved under the selected tab with a transform (design "Indicator slide"). The first
// paint places it at once. A later choice slides it on the motion tokens, unless the visitor asks for reduced motion,
// where it is swapped at once like every other transition of the dashboard.
const indicator = useTabIndicator(
  selected,
  () => (selected.value ? buttons.get(selected.value) : undefined),
  () => props.tabs.map((tab) => buttons.get(tab.key)).filter((tab): tab is HTMLElement => tab !== undefined),
)
const indicatorSlides = computed(() => indicator.moved.value && !reducedMotion())
watch(() => props.tabs.map((tab) => tab.key).join(), () => indicator.refresh(), { flush: 'post' })

// The panels move on the shared axis X (design "Shared axis X"), with the direction of the two tab indexes: a tab to
// the right enters from the right. The direction is set before the panels are patched, so the leaving and the entering
// panel take the same one. The first paint has no direction and no transition.
const panelDirection = ref<MotionDirection>(0)
const indexOf = (key: string | undefined) => props.tabs.findIndex((tab) => tab.key === key)
watch(selected, (to, from) => {
  const fromIndex = indexOf(from)
  const toIndex = indexOf(to)
  panelDirection.value = fromIndex < 0 || toIndex < 0 ? 0 : motionDirection(fromIndex, toIndex)
})

onMounted(() => showSelectedTab('auto'))
watch(selected, () => showSelectedTab(reducedMotion() ? 'auto' : 'smooth'), { flush: 'post' })
</script>

<template>
  <div
    class="grid grid-cols-1 gap-4"
    data-ingot-component="md-tabs"
  >
    <div
      ref="listRef"
      role="tablist"
      :aria-label="label"
      class="relative flex flex-nowrap gap-1 overflow-x-auto border-b border-outline-variant pb-px scrollbar-hidden"
    >
      <button
        v-for="(tab, index) in tabs"
        :id="tabId(tab.key)"
        :ref="(element) => remember(tab.key, element)"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="tab.key === selected"
        :aria-controls="panelId(tab.key)"
        :tabindex="tab.key === selected ? 0 : -1"
        class="md-focus-ring md-label-large -mb-px min-h-6 min-w-6 shrink-0 border-b-2 px-4 py-2"
        :class="tab.key === selected ? 'border-transparent text-primary' : 'border-transparent text-on-surface-variant hover:text-on-surface'"
        @click="choose(tab.key)"
        @keydown="onKeydown($event, index)"
      >
        {{ tab.label }}
      </button>
      <span
        v-show="indicator.transform.value"
        data-test="tab-indicator"
        aria-hidden="true"
        class="pointer-events-none absolute bottom-0 left-0 h-0.5 w-px origin-left bg-primary"
        :class="indicatorSlides ? 'transition-transform duration-md-short4 ease-md-standard-decelerate' : ''"
        :style="{ transform: indicator.transform.value }"
      />
    </div>

    <!--
      Every panel stays in the document and the ones not selected carry the hidden attribute, as before. The panels share
      the grid cell under the tab list, so the leaving panel and the entering one overlap instead of stacking.
    -->
    <MdTransition
      v-for="tab in tabs"
      :key="tab.key"
      pattern="shared-x"
      :direction="panelDirection"
      mode="default"
      persisted
    >
      <div
        :id="panelId(tab.key)"
        v-hidden="tab.key !== selected"
        role="tabpanel"
        tabindex="0"
        class="md-focus-ring col-start-1 row-start-2"
        :aria-labelledby="tabId(tab.key)"
      >
        <slot :tab="tab.key" />
      </div>
    </MdTransition>
  </div>
</template>
