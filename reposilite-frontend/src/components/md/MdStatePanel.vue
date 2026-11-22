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
import MdTransition from './MdTransition.vue'

/*
 * The state of a list, a table or a page that has no content to show: empty, loading or error.
 * Loading draws skeleton lines instead of a spinner and sets aria-busy on the panel. The error
 * state offers one Retry action; the parent does the request and emits nothing else.
 *
 * A panel that needs more than one action passes them through the `actions` slot. The slot
 * replaces the default Retry in the error state, and it is drawn in the empty state too.
 *
 * The error state is announced at once with role alert. A view that already announces the same
 * change through a live region of its own passes announce false, so the change is read once.
 * The container tone draws the error state on the error container, with the text colour that
 * pairs with it, for a banner that sits above content.
 *
 * The states change through the fade pattern of MdTransition: the skeleton leaves over short2 and the content enters over
 * short4, one after the other. The skeleton keeps its own 200 millisecond delay, which is not a transition, and a panel
 * that is loading on first paint is drawn at once.
 */
withDefaults(defineProps<{
  state?: 'empty' | 'loading' | 'error'
  title?: string
  description?: string
  retryLabel?: string
  tone?: 'surface' | 'container'
  announce?: boolean
}>(), {
  state: 'empty',
  title: undefined,
  description: undefined,
  retryLabel: 'Retry',
  tone: 'surface',
  announce: true
})

// The attributes of the parent belong on the element that is drawn, not on the transition around it.
defineOptions({ inheritAttrs: false })

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <MdTransition
    pattern="fade"
    mode="out-in"
  >
    <div
      v-if="state === 'loading'"
      aria-busy="true"
      data-ingot-state="loading"
      class="flex flex-col items-center gap-3 px-6 py-8 text-center text-on-surface"
      v-bind="$attrs"
      data-ingot-component="md-state-panel"
    >
      <p
        v-if="title"
        class="md-title-small m-0"
      >
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
      :role="announce ? 'alert' : undefined"
      data-ingot-state="error"
      :class="tone === 'container' ? 'bg-error-container text-on-error-container' : 'text-on-surface'"
      class="flex flex-col items-center gap-3 px-6 py-8 text-center"
      v-bind="$attrs"
      data-ingot-component="md-state-panel"
    >
      <p
        v-if="title"
        class="md-title-small m-0"
      >
        {{ title }}
      </p>
      <p
        v-if="description"
        class="md-body-medium m-0 text-on-surface-variant"
      >
        {{ description }}
      </p>
      <div
        v-if="$slots.actions"
        class="flex flex-wrap items-center justify-center gap-2"
      >
        <slot name="actions" />
      </div>
      <MdButton
        v-else
        variant="tonal"
        @click="emit('retry')"
      >
        {{ retryLabel }}
      </MdButton>
    </div>
    <div
      v-else
      data-ingot-state="empty"
      class="flex flex-col items-center gap-3 px-6 py-8 text-center text-on-surface"
      v-bind="$attrs"
      data-ingot-component="md-state-panel"
    >
      <p
        v-if="title"
        class="md-title-small m-0"
      >
        {{ title }}
      </p>
      <p
        v-if="description"
        class="md-body-medium m-0 text-on-surface-variant"
      >
        {{ description }}
      </p>
      <div
        v-if="$slots.actions"
        class="flex flex-wrap items-center justify-center gap-2"
      >
        <slot name="actions" />
      </div>
    </div>
  </MdTransition>
</template>
