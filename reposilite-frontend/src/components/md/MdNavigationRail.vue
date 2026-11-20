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
import MdNavigationItem from './MdNavigationItem.vue'
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
 * unnamed list, as before. Each destination is an MdNavigationItem with the rail variant.
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

// The destinations by entry key, and the keys of the labels that are cut at the rail's width. Each destination is an
// MdNavigationItem, which exposes the element of its label. The element is read when the labels are measured, because
// the item sets its own template ref after the rail has received the item.
const destinations = new Map<string, ComponentPublicInstance>()
const cut = ref<Set<string>>(new Set())
const setLabel = (key: string, instance: Element | ComponentPublicInstance | null) => {
  if (instance && !(instance instanceof Element) && 'labelElement' in instance) destinations.set(key, instance)
  else destinations.delete(key)
}
const labelOf = (instance: ComponentPublicInstance): HTMLElement | null => {
  const element = (instance as ComponentPublicInstance & { labelElement?: unknown }).labelElement
  return element instanceof HTMLElement ? element : null
}
const measureLabels = () => {
  cut.value = new Set([...destinations]
    .filter(([, instance]) => {
      const element = labelOf(instance)
      return element !== null && element.scrollWidth > element.clientWidth
    })
    .map(([key]) => key))
}
const titleOf = (item: NavItem) => (cut.value.has(item.key) ? item.label : undefined)

onMounted(() => {
  measureLabels()
  window.addEventListener('resize', measureLabels)
})
onBeforeUnmount(() => window.removeEventListener('resize', measureLabels))
watch(() => props.items, () => nextTick(measureLabels), { deep: true })

// MdNavigationItem leaves a modified click on a link to the browser and reports every other click. A reported
// click on a link keeps the page in place, so the parent switches the section through the router.
// The focus ring is for keyboard focus. A pointer click (detail above 0) leaves the destination
// it selected without focus, so no ring is left behind in any browser. A keyboard activation
// has detail 0 and keeps the focus where the keyboard put it.
const select = (item: NavItem, event: MouseEvent) => {
  if (item.href) {
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
          <MdNavigationItem
            :ref="(instance) => setLabel(item.key, instance)"
            variant="rail"
            :label="item.label"
            :href="item.href"
            :active="isActive(item)"
            :label-title="titleOf(item)"
            @select="select(item, $event)"
          >
            <template #leading>
              <slot
                name="icon"
                :item="item"
                :active="isActive(item)"
              />
            </template>
          </MdNavigationItem>
        </li>
      </ul>
    </div>
  </nav>
</template>
