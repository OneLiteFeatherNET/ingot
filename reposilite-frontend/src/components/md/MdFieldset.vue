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
 * Material Design 3 fieldset: a titled group of form controls, such as the radio buttons of one choice or
 * the checkboxes of one list. The title is the legend of a native fieldset, so assistive technology names the
 * group by it. The gap between the controls is the caller's class, because a group of radio buttons with help
 * lines and a group of checkboxes are spaced differently.
 */
defineProps<{
  legend: string
}>()

defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <fieldset
    class="flex min-w-0 flex-col"
    data-ingot-component="md-fieldset"
  >
    <legend class="mb-2 md-title-small text-on-surface-variant">
      {{ legend }}
    </legend>
    <slot />
  </fieldset>
</template>
