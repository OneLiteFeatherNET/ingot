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

<script setup>
import { computed } from 'vue'
import { snackbar as sharedQueue } from '../../store/snackbar'

/*
 * Material Design 3 snackbar. It shows the head of a notification queue and nothing else:
 * the queue decides what comes next and when a notice leaves. Mount it once, in App.vue.
 *
 * Errors are announced at once (`role="alert"`). Everything else is a polite status update
 * (`role="status"`), so that a success message never interrupts what the user is reading.
 *
 * The `queue` prop exists for tests, which pass a queue of their own. The application uses
 * the shared queue.
 */
const props = defineProps({
  queue: {
    type: Object,
    default: () => sharedQueue
  }
})

const current = computed(() => props.queue.current.value)
const isError = computed(() => current.value?.tone === 'error')
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-4 bottom-4 max-[600px]:bottom-[calc(1rem+var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom))] z-[1100] flex justify-center md:inset-x-auto md:bottom-6 md:left-6 md:justify-start"
  >
    <Transition name="md-snackbar">
      <div
        v-if="current"
        :key="current.id"
        :role="isError ? 'alert' : 'status'"
        :aria-live="isError ? undefined : 'polite'"
        :data-tone="current.tone"
        class="pointer-events-auto flex min-h-12 w-full max-w-[560px] items-center gap-2 rounded-corner-extra-small bg-inverse-surface py-2 pr-2 pl-4 text-inverse-on-surface shadow-elevation-3 md-body-medium"
      >
        <span class="min-w-0 flex-1 break-words py-1">{{ current.message }}</span>
        <button
          v-if="current.action"
          type="button"
          class="md-state-layer md-focus-ring inline-flex min-h-12 shrink-0 items-center rounded-corner-full px-3 text-inverse-primary md-label-large"
          @click="queue.runAction(current.id)"
        >
          {{ current.action.label }}
        </button>
        <button
          type="button"
          aria-label="Dismiss notification"
          class="md-state-layer md-focus-ring inline-flex size-12 shrink-0 items-center justify-center rounded-corner-full text-inverse-on-surface [&>svg]:size-6"
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
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.md-snackbar-enter-active,
.md-snackbar-leave-active {
  transition:
    opacity var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard),
    transform var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard);
}

.md-snackbar-enter-from,
.md-snackbar-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (prefers-reduced-motion: reduce) {
  .md-snackbar-enter-active,
  .md-snackbar-leave-active {
    transition: none;
  }
}
</style>
