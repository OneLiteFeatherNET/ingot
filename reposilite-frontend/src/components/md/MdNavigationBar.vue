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

<script setup>
/*
 * Material Design 3 navigation bar: three to five destinations with an icon and a label.
 * Spec: 80px container on surface-container, 64 by 32 active indicator on secondary-container,
 * 24px icon, label-medium label that is always shown.
 *
 * Each item is { key, label, href? }. With `href` the item is a link. A plain left click
 * keeps the page in place and emits update:modelValue, so the parent switches the section
 * through the router. A modified click (ctrl, shift, meta, alt or a middle click) is left
 * to the browser, so the link can open in a new tab without also switching this page.
 * Without `href` the item is a button and always emits.
 *
 * The icon slot receives { item, active } for each destination.
 */
const props = defineProps({
  items: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    default: undefined
  },
  label: {
    type: String,
    default: 'Primary'
  }
})

const emit = defineEmits(['update:modelValue'])

const isActive = (item) => item.key === props.modelValue

const isPlainLeftClick = (event) =>
  event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey

const select = (item, event) => {
  if (item.href) {
    if (!isPlainLeftClick(event)) {
      return
    }
    event.preventDefault()
  }
  emit('update:modelValue', item.key)
}
</script>

<template>
  <nav
    :aria-label="label"
    class="h-20 w-full bg-surface-container text-on-surface"
  >
    <ul
      role="list"
      class="flex h-full list-none items-center justify-around gap-1 px-2"
    >
      <li
        v-for="item in items"
        :key="item.key"
        class="flex h-full flex-1"
      >
        <component
          :is="item.href ? 'a' : 'button'"
          :href="item.href"
          :type="item.href ? undefined : 'button'"
          :aria-current="isActive(item) ? 'page' : undefined"
          class="md-state-layer md-focus-ring relative flex h-14 min-w-12 flex-1 flex-col items-center justify-center gap-1 rounded-corner-large p-1 no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard motion-reduce:transition-none"
          :class="isActive(item) ? 'text-on-surface' : 'text-on-surface-variant'"
          @click="select(item, $event)"
        >
          <span
            class="flex h-8 w-16 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-medium2) ease-md-standard motion-reduce:transition-none [&>svg]:size-6"
            :class="isActive(item) ? 'bg-secondary-container text-on-secondary-container' : ''"
            aria-hidden="true"
          >
            <slot
              name="icon"
              :item="item"
              :active="isActive(item)"
            />
          </span>
          <span class="md-label-medium">{{ item.label }}</span>
        </component>
      </li>
    </ul>
  </nav>
</template>
