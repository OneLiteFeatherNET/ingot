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
/* A fieldset with a legend, the frame of one group of choices in a wizard step. */
defineProps<{
  legend: string
}>()
</script>

<template>
  <fieldset
    class="flex flex-col gap-2"
    data-ingot-component="wizard-field-group"
  >
    <legend class="pb-2 md-title-small text-on-surface">
      {{ legend }}
    </legend>
    <slot />
  </fieldset>
</template>
