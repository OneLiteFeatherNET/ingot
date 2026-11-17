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
import { computed, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import { SCRIM_STYLE } from '../settings/dialog'
import { countLabel, formatBytes, formatDateTime } from './format'
import { statusLabel } from './cleanup'
import type { CleanupRun } from './cleanup'

/*
 * Confirms a run now. A run deletes artifacts at once and cannot be undone, so the run button
 * stays disabled until the policy id is typed exactly. After the run the dialog shows its result
 * in place of the form.
 */
const props = withDefaults(defineProps<{
  modelValue?: boolean
  policyId: string
  running?: boolean
  /** The run record the server returned, once the run has finished. */
  result?: CleanupRun | null
  /** The readable failure of the run, when it did not start. */
  error?: string | null
}>(), {
  modelValue: false,
  running: false,
  result: null,
  error: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const typed = ref('')
const matches = computed(() => typed.value === props.policyId)
const cancel = ref<{ $el: HTMLElement } | null>(null)
let opener: Element | null = null

watch(() => props.modelValue, (open) => {
  if (open) {
    typed.value = ''
    opener = document.activeElement
    return
  }
  if (opener instanceof HTMLElement && opener.isConnected) opener.focus()
})

// Cancel takes the focus as soon as it is drawn, and the focus trap of the modal starts on it too, so the modal's own
// first focus (the id field) does not take it away.
watch(cancel, (button) => {
  if (button && props.modelValue) button.$el.focus()
})
const focusTrap = {
  allowOutsideClick: true,
  initialFocus: () => cancel.value?.$el
}

const close = () => emit('update:modelValue', false)

const confirm = () => {
  if (!matches.value || props.running) return
  emit('confirm')
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex min-h-full items-center justify-center p-4"
    :focus-trap="focusTrap"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div
      class="w-[calc(100vw-2rem)] max-w-lg max-h-[90dvh] overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="run-now-title"
      :aria-describedby="result ? undefined : 'run-now-body'"
      data-ingot-component="run-now-dialog"
    >
      <h2
        id="run-now-title"
        class="md-headline-small text-on-surface"
      >
        Run {{ policyId }} now?
      </h2>

      <template v-if="!result">
        <form
          id="run-now-form"
          class="mt-3 flex flex-col gap-4"
          novalidate
          @submit.prevent="confirm"
        >
          <p
            id="run-now-body"
            class="md-body-medium text-on-surface-variant"
          >
            The run deletes the artifacts this policy selects, at once, and a deleted artifact cannot be
            restored. Type the policy id to confirm.
          </p>
          <MdTextField
            id="run-now-input"
            label="Policy id"
            variant="outlined"
            autocomplete="off"
            :model-value="typed"
            :supporting-text="`Type ${policyId} exactly.`"
            @update:model-value="typed = $event"
          />
          <p
            v-if="error"
            data-ingot-state="error"
            class="md-body-medium text-error"
            role="alert"
          >
            {{ error }}
          </p>
          <div class="flex flex-wrap justify-end gap-2">
            <MdButton
              id="run-now-cancel"
              ref="cancel"
              variant="text"
              type="button"
              :disabled="running"
              @click="close"
            >
              Cancel
            </MdButton>
            <MdButton
              id="run-now-confirm"
              variant="filled"
              error
              type="submit"
              :disabled="!matches || running"
            >
              {{ running ? 'Running…' : 'Run now' }}
            </MdButton>
          </div>
        </form>
      </template>

      <template v-else>
        <div class="mt-3 flex flex-col gap-3">
          <p class="md-body-large text-on-surface">
            <span class="md-title-medium">{{ statusLabel(result.status) }}.</span>
            {{ countLabel(result.deletedCount, 'artifact') }} deleted, {{ formatBytes(result.deletedBytes) }}.
          </p>
          <p class="md-body-small text-on-surface-variant">
            Finished {{ formatDateTime(result.finishedAt ?? result.startedAt) }}
          </p>
          <ul
            v-if="result.failures.length"
            class="flex list-disc flex-col gap-1 pl-5 md-body-medium text-error"
            aria-label="Failures"
          >
            <li
              v-for="(failure, index) in result.failures"
              :key="index"
            >
              {{ failure }}
            </li>
          </ul>
        </div>
        <div class="mt-6 flex justify-end">
          <MdButton
            variant="tonal"
            type="button"
            @click="close"
          >
            Close
          </MdButton>
        </div>
      </template>
    </div>
  </VueFinalModal>
</template>
