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
import { computed } from 'vue'

/*
 * Material Design 3 tab row. `primary` tabs are the top level of the settings view; `secondary`
 * tabs nest inside a renderer. The row only draws the bar: the caller renders the panel of the
 * selected key. Tabs are buttons with role="tab", arrow, Home and End keys move between them,
 * and the selected one is the only tab in the tab order.
 */
const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  modelValue: {
    type: [String, Number],
    default: undefined
  },
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'secondary'].includes(value)
  },
  label: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const isSelected = (tab) => tab.key === props.modelValue

const select = (index) => {
  emit('update:modelValue', props.tabs[index].key)
}

const onKeydown = (event, index) => {
  const last = props.tabs.length - 1
  const targets = {
    ArrowRight: index === last ? 0 : index + 1,
    ArrowLeft: index === 0 ? last : index - 1,
    Home: 0,
    End: last
  }
  if (!(event.key in targets)) return
  event.preventDefault()
  const target = targets[event.key]
  select(target)
  event.currentTarget.parentElement.querySelectorAll('[role="tab"]')[target]?.focus()
}

const tabClasses = (tab) => [
  'md-state-layer md-focus-ring relative inline-flex min-w-0 flex-col items-center justify-center rounded-t-(--md-sys-shape-corner-small) px-4',
  'transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
  props.variant === 'primary' ? 'min-h-12' : 'min-h-10',
  isSelected(tab) ? 'text-primary' : 'text-on-surface-variant hover:text-on-surface'
]

const indicatorClasses = computed(() => props.variant === 'primary'
  ? 'absolute inset-x-4 bottom-0 h-[3px] rounded-t-[3px] bg-primary'
  : 'absolute inset-x-0 bottom-0 h-0.5 bg-primary')
</script>

<template>
  <div
    role="tablist"
    :aria-label="label"
    class="flex overflow-x-auto border-b border-outline-variant"
  >
    <button
      v-for="(tab, index) in tabs"
      :key="String(tab.key)"
      type="button"
      role="tab"
      :aria-selected="isSelected(tab) ? 'true' : 'false'"
      :tabindex="isSelected(tab) ? 0 : -1"
      :class="tabClasses(tab)"
      @click="select(index)"
      @keydown="onKeydown($event, index)"
    >
      <span class="md-title-small max-w-full [overflow-wrap:anywhere]">{{ tab.label }}</span>
      <span
        v-if="isSelected(tab)"
        aria-hidden="true"
        :class="indicatorClasses"
      />
    </button>
  </div>
</template>
