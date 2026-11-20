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
import MdNavigationItem from './MdNavigationItem.vue'
import type { NavIconSlot, NavItem } from './types'
/*
 * Material Design 3 navigation bar: three to five destinations with an icon and a label.
 * Spec: 80px container on surface-container, 64 by 32 active indicator on secondary-container,
 * 24px icon, label-medium label that is always shown.
 *
 * Each item is { key, label, href?, shortLabel? }. `shortLabel`, when set, is the visible text and the accessible
 * name of the item (axe label-content-name-mismatch compares whole words, so the name is the visible text), and
 * `label` becomes its description (title). With `href` the item is a link. A plain left click keeps the page in place and emits update:modelValue, so the parent switches the section
 * through the router. A modified click (ctrl, shift, meta, alt or a middle click) is left
 * to the browser, so the link can open in a new tab without also switching this page.
 * Without `href` the item is a button and always emits.
 *
 * The icon slot receives { item, active } for each destination. Each destination is an MdNavigationItem with the
 * bar variant.
 */
const props = withDefaults(defineProps<{
  items: NavItem[]
  modelValue?: string
  label?: string
}>(), {
  modelValue: undefined,
  label: 'Primary'
})

defineSlots<{
  icon?: NavIconSlot
}>()

const emit = defineEmits<{
  'update:modelValue': [key: string]
}>()

const isActive = (item: NavItem) => item.key === props.modelValue

// MdNavigationItem leaves a modified click on a link to the browser and reports every other click. A reported
// click on a link keeps the page in place, so the parent switches the section through the router.
const select = (item: NavItem, event: MouseEvent) => {
  if (item.href) {
    event.preventDefault()
  }
  emit('update:modelValue', item.key)
}
</script>

<template>
  <nav
    :aria-label="label"
    class="h-20 w-full bg-surface-container text-on-surface"
    data-ingot-component="md-navigation-bar"
  >
    <ul
      role="list"
      class="flex h-full w-full min-w-0 list-none items-center justify-around gap-1 px-2"
    >
      <!-- min-w-0: an item shrinks to its share of the width instead of its label's width, so the
           bar never runs past the client area. -->
      <li
        v-for="item in items"
        :key="item.key"
        class="flex h-full min-w-0 flex-1 basis-0"
      >
        <!-- The label is cut with an ellipsis when its item is narrower than the text (a 320 px window with a wide
             fallback font), so the text never runs past the edge of the window. The full name stays in the title. -->
        <MdNavigationItem
          variant="bar"
          :label="item.shortLabel ?? item.label"
          :href="item.href"
          :active="isActive(item)"
          :description="item.shortLabel ? item.label : undefined"
          :label-title="item.label"
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
  </nav>
</template>
