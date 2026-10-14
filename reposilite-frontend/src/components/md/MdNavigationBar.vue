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
 * It fits the dashboard's five tabs. The active destination carries aria-current="page".
 *
 * Each item is { key, label, href? }. With `href` the item is a link and the browser
 * navigates; without it the item is a button. Either way a click emits update:modelValue
 * with the item's key, so the parent can switch the view it shows.
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

const select = (item) => emit('update:modelValue', item.key)
</script>

<template>
  <nav
    :aria-label="label"
    class="w-full bg-surface-container text-on-surface"
  >
    <ul
      role="list"
      class="flex list-none items-stretch justify-around gap-1 px-2 py-2"
    >
      <li
        v-for="item in items"
        :key="item.key"
        class="flex flex-1"
      >
        <component
          :is="item.href ? 'a' : 'button'"
          :href="item.href"
          :type="item.href ? undefined : 'button'"
          :aria-current="isActive(item) ? 'page' : undefined"
          class="md-state-layer md-focus-ring relative flex min-h-12 min-w-12 flex-1 flex-col items-center justify-center gap-1 rounded-corner-large p-1 no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard"
          :class="isActive(item) ? 'text-on-surface' : 'text-on-surface-variant'"
          @click="select(item)"
        >
          <span
            class="flex h-8 w-16 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-medium2) ease-md-standard [&>svg]:size-6"
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
