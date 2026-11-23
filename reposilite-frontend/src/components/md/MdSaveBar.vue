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
 * Material Design 3 save bar: the bar of a form that has unsaved changes. It sticks to the bottom of its
 * scroll container and, below the medium window class, stays above the bottom bar and the safe area. The
 * content, such as the count of changes and the save and discard buttons, is the caller's slot.
 */
defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <div
    class="sticky bottom-0 z-md-sticky max-medium:bottom-[calc(var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom,0px))]"
    data-ingot-component="md-save-bar"
  >
    <slot />
  </div>
</template>
