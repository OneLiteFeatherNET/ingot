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
/* The choices of the wizard as a list of labelled values, shown before anything is created. */
defineProps<{
  rows: { label: string; value: string }[]
}>()
</script>

<template>
  <dl
    class="flex flex-col gap-3 rounded-corner-medium bg-surface-container p-4"
    data-ingot-component="wizard-review-summary"
  >
    <div
      v-for="row in rows"
      :key="row.label"
      class="flex flex-col gap-0.5"
    >
      <dt class="md-label-large text-on-surface-variant">
        {{ row.label }}
      </dt>
      <dd class="m-0 break-words md-body-large text-on-surface">
        {{ row.value }}
      </dd>
    </div>
  </dl>
</template>
