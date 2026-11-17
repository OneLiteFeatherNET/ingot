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
import { provide } from 'vue'

/*
 * Material Design 3 list. It is a ul of MdListItem rows. `divided` draws a rule between the rows,
 * starting at the `inset` (16px, or 72px to clear a leading avatar). `selectable` makes the list a
 * listbox of options, and each row reports its selection through `selected` and the select event.
 * The rows read these settings from the list, so the list is the one place they are set.
 */
const props = withDefaults(defineProps<{
  divided?: boolean
  inset?: 'none' | 16 | 72
  selectable?: boolean
}>(), {
  divided: false,
  inset: 'none',
  selectable: false
})

provide('md-list', props)

defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <ul
    :role="selectable ? 'listbox' : undefined"
    :data-divided="String(divided)"
    class="flex flex-col text-on-surface"
    data-ingot-component="md-list"
  >
    <slot />
  </ul>
</template>
