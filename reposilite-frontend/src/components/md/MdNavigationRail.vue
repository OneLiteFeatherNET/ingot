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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComponentPublicInstance } from 'vue'
import { clearRailWidth, publishRailWidth } from '../../helpers/navigation-rail'
import type { NavIconSlot, NavItem } from './types'

/*
 * Material Design 3 navigation rail: the vertical counterpart of MdNavigationBar, for the
 * medium and expanded widths. Spec: 96px wide on the surface-container colour, a 56 by 32 active
 * indicator on secondary-container, a 24px icon and a label-medium label under it. The state layer
 * is drawn on the indicator, not on the whole entry, as the spec has it.
 *
 * Labels are one line each. A label that does not fit is cut with an ellipsis and then carries its
 * full text as a title, so a plugin's long label can still be read on hover.
 *
 * It takes the same props and emits the same events as MdNavigationBar, so the two can show
 * the same destinations. The parent decides which of the two is on screen (CSS only). The `top`
 * slot fills the corner above the entries, which is as tall as the top bar beside it.
 *
 * An item may carry a `group` (the group name). Neighbouring items of one group are drawn together as an
 * element labelled with the group name, with a divider between groups. Items without a group form one
 * unnamed list, as before.
 */
interface Block {
  group?: string
  items: NavItem[]
}

const props = withDefaults(defineProps<{
  items: NavItem[]
  modelValue?: string
  label?: string
}>(), {
  modelValue: undefined,
  label: 'Primary'
})

defineSlots<{
  top?(): unknown
  icon?: NavIconSlot
}>()

const emit = defineEmits<{
  'update:modelValue': [key: string]
}>()

// The rail publishes its width for overlays such as the snackbar, and withdraws it when it leaves.
onMounted(() => publishRailWidth())
onBeforeUnmount(() => clearRailWidth())

// Neighbouring items with the same `group` form one block, in the order the items were given.
const blocks = computed(() => props.items.reduce<Block[]>((all, item) => {
  const last = all[all.length - 1]
  if (last && last.group === item.group) {
    last.items.push(item)
  } else {
    all.push({ group: item.group, items: [item] })
  }
  return all
}, []))

const isActive = (item: NavItem) => item.key === props.modelValue

// The label elements by entry key, and the keys of the labels that are cut at the rail's width.
const labels = new Map<string, HTMLElement>()
const cut = ref<Set<string>>(new Set())
const setLabel = (key: string, element: Element | ComponentPublicInstance | null) => {
  if (element instanceof HTMLElement) labels.set(key, element)
  else labels.delete(key)
}
const measureLabels = () => {
  cut.value = new Set([...labels].filter(([, element]) => element.scrollWidth > element.clientWidth).map(([key]) => key))
}
const titleOf = (item: NavItem) => (cut.value.has(item.key) ? item.label : undefined)

onMounted(() => {
  measureLabels()
  window.addEventListener('resize', measureLabels)
})
onBeforeUnmount(() => window.removeEventListener('resize', measureLabels))
watch(() => props.items, () => nextTick(measureLabels), { deep: true })

const isPlainLeftClick = (event: MouseEvent) =>
  event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey

// The focus ring is for keyboard focus. A pointer click (detail above 0) leaves the destination
// it selected without focus, so no ring is left behind in any browser. A keyboard activation
// has detail 0 and keeps the focus where the keyboard put it.
const select = (item: NavItem, event: MouseEvent) => {
  if (item.href) {
    if (!isPlainLeftClick(event)) {
      return
    }
    event.preventDefault()
  }
  if (event.detail > 0) {
    (event.currentTarget as HTMLElement).blur()
  }
  emit('update:modelValue', item.key)
}
</script>

<template>
  <nav
    :aria-label="label"
    class="flex h-full w-md-rail flex-col items-center bg-surface-container text-on-surface"
    data-ingot-component="md-navigation-rail"
  >
    <div
      v-if="$slots.top"
      class="flex h-16 w-full min-w-0 shrink-0 items-center justify-center"
    >
      <slot name="top" />
    </div>
    <div
      v-for="(block, index) in blocks"
      :key="block.group ?? index"
      :role="block.group ? 'group' : undefined"
      :aria-label="block.group"
      class="flex w-full flex-col items-center"
      :class="index > 0 ? 'mt-2 border-t border-outline-variant pt-2' : 'pt-2'"
    >
      <ul
        role="list"
        class="flex w-full list-none flex-col items-center gap-2"
      >
        <li
          v-for="item in block.items"
          :key="item.key"
          class="flex w-full"
        >
          <component
            :is="item.href ? 'a' : 'button'"
            :href="item.href"
            :type="item.href ? undefined : 'button'"
            :aria-current="isActive(item) ? 'page' : undefined"
            class="group md-focus-ring-inset relative flex min-h-14 w-full flex-col items-center justify-center gap-1 rounded-corner-large px-1 py-1 no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard motion-reduce:transition-none"
            :class="isActive(item) ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="select(item, $event)"
          >
            <span
              class="md-state-layer flex h-8 w-14 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-medium2) ease-md-standard group-hover:md-state-hover motion-reduce:transition-none [&>svg]:size-6"
              :class="isActive(item) ? 'bg-secondary-container text-on-secondary-container' : ''"
              aria-hidden="true"
            >
              <slot
                name="icon"
                :item="item"
                :active="isActive(item)"
              />
            </span>
            <span
              :ref="(element) => setLabel(item.key, element)"
              class="md-label-medium block max-w-full truncate text-center whitespace-nowrap"
              :class="isActive(item) ? 'md-label-medium-prominent text-on-surface' : ''"
              :title="titleOf(item)"
            >{{ item.label }}</span>
          </component>
        </li>
      </ul>
    </div>
  </nav>
</template>
