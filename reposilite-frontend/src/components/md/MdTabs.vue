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
import { computed, inject, onMounted, useId, watch } from 'vue'
import { routeLocationKey, routerKey } from 'vue-router'

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
  buttons.get(target.key)?.focus()
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

// A tab row that is wider than the screen scrolls sideways, so the selected tab is brought into view on the first render
// (a deep link such as ?tab=configuration) and whenever the selection changes.
const scrollSelectedIntoView = () => {
  const key = selected.value
  if (key) buttons.get(key)?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
}

onMounted(scrollSelectedIntoView)
watch(selected, scrollSelectedIntoView, { flush: 'post' })
</script>

<template>
  <div
    class="flex flex-col gap-4"
    data-ingot-component="md-tabs"
  >
    <div
      role="tablist"
      :aria-label="label"
      class="flex flex-nowrap gap-1 overflow-x-auto border-b border-outline-variant pb-px scrollbar-hidden"
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
        :class="tab.key === selected ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant hover:text-on-surface'"
        @click="choose(tab.key)"
        @keydown="onKeydown($event, index)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      v-for="tab in tabs"
      :id="panelId(tab.key)"
      :key="tab.key"
      role="tabpanel"
      tabindex="0"
      class="md-focus-ring"
      :aria-labelledby="tabId(tab.key)"
      :hidden="tab.key !== selected"
    >
      <slot :tab="tab.key" />
    </div>
  </div>
</template>
