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
import { computed, inject } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/*
 * Material Design 3 list item. Inside a plain MdList the row is a link (`to`, routed through the
 * router so the dashboard base is kept), a button (`button`), or plain text. Inside a selectable
 * MdList the row is an option: it is focusable, reports `aria-selected`, and emits `select` on a
 * click or on Enter and Space. The leading and trailing slots hold icons or an avatar.
 */
const props = withDefaults(defineProps<{
  to?: RouteLocationRaw
  button?: boolean
  selected?: boolean
}>(), {
  to: undefined,
  button: false,
  selected: false
})

const emit = defineEmits<{
  select: []
}>()

defineSlots<{
  leading?(): unknown
  default?(): unknown
  trailing?(): unknown
}>()

interface ListSettings {
  divided: boolean
  inset: 'none' | 16 | 72
  selectable: boolean
}

const list = inject<ListSettings | undefined>('md-list', undefined)

const isOption = computed(() => Boolean(list?.selectable))

// Each class is written out in full, so the compiler finds it in the source.
const RULE_START = { 16: 'start-md-inset-16', 72: 'start-md-inset-72' } as Record<string, string>
const ruleStart = computed(() => RULE_START[String(list?.inset)] ?? 'start-0')

const contentTag = computed(() => {
  if (isOption.value) return 'div'
  if (props.to !== undefined) return RouterLink
  return props.button ? 'button' : 'div'
})

const contentAttrs = computed(() => {
  if (isOption.value) return {}
  if (props.to !== undefined) return { to: props.to }
  return props.button ? { type: 'button' } : {}
})

// Only an option takes the keys. A link or a button in a plain list keeps its own Enter and Space.
const select = (event?: Event) => {
  if (!isOption.value) return
  event?.preventDefault()
  emit('select')
}
</script>

<template>
  <li
    :role="isOption ? 'option' : undefined"
    :aria-selected="isOption ? (selected ? 'true' : 'false') : undefined"
    :tabindex="isOption ? 0 : undefined"
    :class="[
      'group relative',
      isOption && selected ? 'bg-secondary-container text-on-secondary-container' : ''
    ]"
    data-ingot-component="md-list-item"
    @click="select"
    @keydown.enter="select"
    @keydown.space="select"
  >
    <component
      :is="contentTag"
      v-bind="contentAttrs"
      class="md-state-layer md-focus-ring md-touch-target relative flex min-h-14 w-full items-center gap-4 px-4 py-3 text-left no-underline md-body-large"
    >
      <span
        v-if="$slots.leading"
        class="shrink-0"
      >
        <slot name="leading" />
      </span>
      <span class="min-w-0 flex-1">
        <slot />
      </span>
      <span
        v-if="$slots.trailing"
        class="shrink-0"
      >
        <slot name="trailing" />
      </span>
    </component>
    <span
      v-if="list?.divided"
      data-test="item-rule"
      aria-hidden="true"
      :class="['absolute end-0 top-0 h-px bg-outline-variant group-first:hidden', ruleStart]"
    />
  </li>
</template>
