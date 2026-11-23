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
 * Material Design 3 field grid: the form fields of one group, in one column below the expanded window class
 * (840px) and in two columns from it. A field that needs the full row sets a col-span-full class of its own.
 */
defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <div
    class="grid grid-cols-1 gap-4 expanded:grid-cols-2"
    data-ingot-component="md-field-grid"
  >
    <slot />
  </div>
</template>
