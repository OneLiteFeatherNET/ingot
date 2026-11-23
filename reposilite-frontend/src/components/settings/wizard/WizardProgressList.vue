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
import MdAlert from '../../md/MdAlert.vue'
import MdButton from '../../md/MdButton.vue'
import { failureNote } from '../repositoryWizard'
import type { ProgressEntry } from '../repositoryWizard'

/*
 * The create sequence: one row per action with its state. A failed row says what went wrong, lists
 * what exists already and offers to retry that step.
 */
defineProps<{
  actions: { key: string; label: string }[]
  progress: Record<string, ProgressEntry>
  created: string[]
  running: boolean
}>()

defineEmits<{
  retry: []
}>()

const STATE_TEXT: Record<ProgressEntry['state'], string> = {
  pending: 'Waiting',
  running: 'Working',
  done: 'Done',
  failed: 'Failed'
}

const stateText = (progress: Record<string, ProgressEntry>, key: string) => {
  const state = progress[key]?.state
  return state === undefined ? '' : STATE_TEXT[state]
}
</script>

<template>
  <ol
    data-ingot-component="wizard-progress-list"
    aria-label="Progress"
    class="flex flex-col gap-3"
  >
    <li
      v-for="action in actions"
      :key="action.key"
      class="flex flex-col gap-1 rounded-corner-medium bg-surface-container p-4"
      :data-step="action.key"
      :data-state="progress[action.key].state"
    >
      <span class="md-title-small text-on-surface">{{ action.label }}</span>
      <span class="md-body-medium text-on-surface-variant">{{ stateText(progress, action.key) }}</span>
      <template v-if="progress[action.key].state === 'failed'">
        <MdAlert
          data-test="step-failure"
          tone="error"
        >
          {{ failureNote(action.key) }}
        </MdAlert>
        <p class="md-body-small text-on-surface-variant break-words">
          {{ progress[action.key].error }}
        </p>
        <div
          v-if="created.length > 0"
          data-test="created"
          class="flex flex-col gap-1"
        >
          <p class="md-label-large text-on-surface">
            Created so far
          </p>
          <ul class="m-0 flex list-disc flex-col gap-0.5 pl-5 md-body-small text-on-surface-variant">
            <li
              v-for="line in created"
              :key="line"
            >
              {{ line }}
            </li>
          </ul>
        </div>
        <div>
          <MdButton
            data-test="retry"
            variant="tonal"
            type="button"
            :disabled="running"
            @click="$emit('retry')"
          >
            Retry this step
          </MdButton>
        </div>
      </template>
    </li>
  </ol>
</template>
