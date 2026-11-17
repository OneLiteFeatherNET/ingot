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
 * The action row of a dialog: the buttons sit at the end of the row, with one gap between them, and the row wraps onto a
 * new line when the dialog is too narrow for all of them. The buttons are the slot content, in the order given.
 */
defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <div
    class="flex flex-wrap items-center justify-end gap-2"
    data-ingot-component="md-dialog-actions"
  >
    <slot />
  </div>
</template>
