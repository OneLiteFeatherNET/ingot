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
import { onBeforeUnmount, onMounted } from 'vue'
import { clearRailWidth, publishRailWidth } from '../../helpers/navigation-rail'

/*
 * Material Design 3 navigation rail: the vertical counterpart of MdNavigationBar, for the
 * medium and expanded widths. Spec: 80px wide on the surface colour, a 56 by 32 active
 * indicator on secondary-container, a 24px icon and a label-medium label under it. The items
 * start 44px from the top.
 *
 * It takes the same props and emits the same events as MdNavigationBar, so the two can show
 * the same destinations. The parent decides which of the two is on screen (CSS only).
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

// The rail publishes its width for overlays such as the snackbar, and withdraws it when it leaves.
onMounted(() => publishRailWidth())
onBeforeUnmount(() => clearRailWidth())

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
    class="flex h-full w-20 flex-col items-center bg-surface pt-11 text-on-surface"
  >
    <ul
      role="list"
      class="flex w-full list-none flex-col items-center gap-2"
    >
      <li
        v-for="item in items"
        :key="item.key"
        class="flex w-full"
      >
        <component
          :is="item.href ? 'a' : 'button'"
          :href="item.href"
          :type="item.href ? undefined : 'button'"
          :aria-current="isActive(item) ? 'page' : undefined"
          class="md-state-layer md-focus-ring relative flex min-h-14 w-full flex-col items-center justify-center gap-1 rounded-corner-large py-1 no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard motion-reduce:transition-none"
          :class="isActive(item) ? 'text-on-surface' : 'text-on-surface-variant'"
          @click="select(item, $event)"
        >
          <span
            class="flex h-8 w-14 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-medium2) ease-md-standard motion-reduce:transition-none [&>svg]:size-6"
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
