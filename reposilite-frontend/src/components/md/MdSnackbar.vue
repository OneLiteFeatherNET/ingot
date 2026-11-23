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
import { computed, inject } from 'vue'
import { SNACKBAR_QUEUE_KEY, snackbar as sharedQueue } from '../../store/snackbar'
import MdTransition from './MdTransition.vue'
import MdButton from './MdButton.vue'
import MdIconButton from './MdIconButton.vue'

/*
 * Material Design 3 snackbar. It shows the head of a notification queue and nothing else:
 * the queue decides what comes next and when a notice leaves. App.vue provides the queue
 * once, and the application's shared queue is the default when nothing is provided.
 *
 * Errors are announced at once (`role="alert"`). Everything else is a polite status update
 * (`role="status"`), so that a success message never interrupts what the user is reading.
 */
const queue = inject(SNACKBAR_QUEUE_KEY, sharedQueue)

const current = computed(() => queue.current.value)
const isError = computed(() => current.value?.tone === 'error')
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-4 bottom-4 max-medium:bottom-[calc(1rem+var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom))] z-md-snackbar flex justify-center medium:inset-x-auto medium:bottom-6 medium:left-[calc(var(--app-rail-width,0px)+1.5rem)] medium:justify-start"
    data-ingot-component="md-snackbar"
  >
    <MdTransition
      pattern="snackbar"
      mode="out-in"
    >
      <div
        v-if="current"
        :key="current.id"
        :role="isError ? 'alert' : 'status'"
        :aria-live="isError ? undefined : 'polite'"
        :data-tone="current.tone"
        class="pointer-events-auto flex min-h-12 w-full max-w-md-snackbar items-center gap-2 rounded-corner-extra-small bg-inverse-surface py-2 pr-2 pl-4 text-inverse-on-surface shadow-elevation-3 md-body-medium"
      >
        <span class="min-w-0 flex-1 break-words py-1">{{ current.message }}</span>
        <MdButton
          v-if="current.action"
          variant="text"
          inverse
          inset="dense"
          class="shrink-0"
          @click="queue.runAction(current.id)"
        >
          {{ current.action.label }}
        </MdButton>
        <MdIconButton
          inverse
          aria-label="Dismiss notification"
          @click="queue.dismiss(current.id)"
        >
          <svg
            viewBox="0 -960 960 960"
            aria-hidden="true"
            focusable="false"
          >
            <path
              fill="currentColor"
              d="m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z"
            />
          </svg>
        </MdIconButton>
      </div>
    </MdTransition>
  </div>
</template>
