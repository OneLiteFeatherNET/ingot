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
import MdRadio from '../../md/MdRadio.vue'

/*
 * One choice of a wizard step, drawn as a radio card: a title and a line on who it is for. The
 * chosen card takes the secondary container. Cards of the same name form one group.
 */
defineProps<{
  modelValue: string
  value: string
  name: string
  label: string
  who: string
  id?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <MdRadio
    :id="id"
    data-ingot-component="wizard-choice-card"
    :name="name"
    :value="value"
    :model-value="modelValue"
    card
    class="rounded-corner-medium border p-3"
    :class="modelValue === value ? 'border-primary bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface'"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <span class="flex min-w-0 flex-col gap-0.5">
      <span class="md-title-small">{{ label }}</span>
      <span class="md-body-medium">{{ who }}</span>
    </span>
  </MdRadio>
</template>
