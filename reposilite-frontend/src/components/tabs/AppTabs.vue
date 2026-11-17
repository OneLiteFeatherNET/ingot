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
// The tabs of the system information and repository pages. The tabs live in MdTabs; this wrapper keeps the selected tab
// on the `?tab=` address, which both pages rely on, so their imports and their behaviour do not change.
import MdTabs from '../md/MdTabs.vue'

withDefaults(
  defineProps<{
    tabs: { key: string; label: string }[]
    label: string
    modelValue?: string
    // The query parameter that holds the selected key. Null or an empty value keeps the selection off the address.
    queryParam?: string | null
  }>(),
  { modelValue: undefined, queryParam: 'tab' },
)

const emit = defineEmits<{ 'update:modelValue': [key: string] }>()
</script>

<template>
  <MdTabs
    :tabs="tabs"
    :label="label"
    :model-value="modelValue"
    :query-param="queryParam"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template
      v-for="(_, name) in $slots"
      #[name]="slotProps"
    >
      <slot
        :name="name"
        v-bind="slotProps"
      />
    </template>
  </MdTabs>
</template>
