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
 * The numbered steps at the top of the repository wizard. The current step is marked with
 * aria-current, the finished ones take the secondary container and the rest stay quiet.
 */
defineProps<{
  steps: { key: string; label: string }[]
  stepIndex: number
}>()
</script>

<template>
  <ol
    data-ingot-component="wizard-step-indicator"
    aria-label="Steps"
    class="grid gap-2"
    :style="{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }"
  >
    <li
      v-for="(step, index) in steps"
      :key="step.key"
      class="flex min-w-0 flex-col items-center gap-1 text-center"
      :aria-current="index === stepIndex ? 'step' : undefined"
    >
      <span
        class="flex size-8 items-center justify-center rounded-corner-full md-label-large"
        :class="index === stepIndex ? 'bg-primary text-on-primary' : index < stepIndex ? 'bg-secondary-container text-on-secondary-container' : 'bg-surface-container-highest text-on-surface-variant'"
      >{{ index + 1 }}</span>
      <span
        class="max-w-full truncate md-label-medium"
        :class="index === stepIndex ? 'text-on-surface' : 'text-on-surface-variant'"
      >{{ step.label }}</span>
    </li>
  </ol>
</template>
