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
import MdButton from './MdButton.vue'
import MdSkeleton from './MdSkeleton.vue'

/*
 * The state of a list, a table or a page that has no content to show: empty, loading or error.
 * Loading draws skeleton lines instead of a spinner and sets aria-busy on the panel. The error
 * state offers one Retry action; the parent does the request and emits nothing else.
 */
withDefaults(defineProps<{
  state?: 'empty' | 'loading' | 'error'
  title?: string
  description?: string
  retryLabel?: string
}>(), {
  state: 'empty',
  title: undefined,
  description: undefined,
  retryLabel: 'Retry'
})

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div
    v-if="state === 'loading'"
    aria-busy="true"
    data-ingot-state="loading"
    class="flex flex-col items-center gap-3 px-6 py-8 text-center text-on-surface"
    data-ingot-component="md-state-panel"
  >
    <p v-if="title" class="md-title-small m-0">
      {{ title }}
    </p>
    <div class="flex w-full max-w-sm flex-col gap-3">
      <MdSkeleton width="full" />
      <MdSkeleton width="md" />
      <MdSkeleton width="sm" />
    </div>
  </div>
  <div
    v-else-if="state === 'error'"
    role="alert"
    data-ingot-state="error"
    class="flex flex-col items-center gap-3 px-6 py-8 text-center text-on-surface"
    data-ingot-component="md-state-panel"
  >
    <p v-if="title" class="md-title-small m-0">
      {{ title }}
    </p>
    <p v-if="description" class="md-body-medium m-0 text-on-surface-variant">
      {{ description }}
    </p>
    <MdButton variant="tonal" @click="emit('retry')">
      {{ retryLabel }}
    </MdButton>
  </div>
  <div
    v-else
    data-ingot-state="empty"
    class="flex flex-col items-center gap-3 px-6 py-8 text-center text-on-surface"
    data-ingot-component="md-state-panel"
  >
    <p v-if="title" class="md-title-small m-0">
      {{ title }}
    </p>
    <p v-if="description" class="md-body-medium m-0 text-on-surface-variant">
      {{ description }}
    </p>
  </div>
</template>
