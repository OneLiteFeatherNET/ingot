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
import { computed } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdSideSheet from '../md/MdSideSheet.vue'
import { formatChangeValue, groupChanges } from './settingsDiff'
import type { ChangeKind, SettingsChange } from './settingsDiff'

/*
 * Every unsaved change, grouped by domain and entry, with a revert for each one, in the wide side sheet
 * on the end edge. Nothing is written until Save is pressed, and Revert only moves the draft back to the
 * saved value of that one field.
 */
const props = withDefaults(defineProps<{
  open?: boolean
  changes?: readonly SettingsChange[]
  invalid?: boolean
  saving?: boolean
  /*
   * The stored settings changed elsewhere: the changes compare the stored value (before) with the draft (after).
   * Revert then takes the stored value into the draft, and nothing is saved from here; the conflict dialog owns that.
   */
  conflict?: boolean
}>(), {
  open: false,
  changes: () => [],
  invalid: false,
  saving: false,
  conflict: false
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  revert: [change: SettingsChange]
  discard: []
  save: []
}>()

const sections = computed(() => groupChanges(props.changes))

const BADGES: Partial<Record<ChangeKind, string>> = { added: 'Added', removed: 'Removed' }

const labelOf = (change: SettingsChange): string => {
  if (change.label) return change.label
  return change.group ? 'Entry' : change.domainTitle
}

const valueOf = (change: SettingsChange): string => {
  if (change.masked) return 'changed'
  if (change.kind === 'changed') return `${formatChangeValue(change.before)} → ${formatChangeValue(change.after)}`
  if (change.kind === 'added') return change.after === undefined ? '' : formatChangeValue(change.after)
  return change.before === undefined ? '' : formatChangeValue(change.before)
}

const badgeOf = (change: SettingsChange): string => BADGES[change.kind] ?? ''

const revertLabelOf = (change: SettingsChange): string => {
  const badge = badgeOf(change)
  return badge ? `Revert ${badge.toLowerCase()} ${labelOf(change)}` : `Revert ${labelOf(change)}`
}
</script>

<template>
  <MdSideSheet
    :open="open"
    :title="conflict ? 'Stored settings compared' : 'Review changes'"
    side="end"
    width="wide"
    @update:open="emit('update:open', $event)"
  >
    <div
      class="flex min-h-full flex-col"
      data-ingot-component="settings-review-sheet"
    >
      <!-- The title and the close button belong to the side sheet; the header part keeps the note under them. -->
      <header data-ingot-part="header">
        <p
          v-if="conflict"
          class="pb-4 md-body-medium text-on-surface-variant"
        >
          The stored settings were saved elsewhere. Each change shows the stored value, then your draft.
        </p>
      </header>
      <div class="flex-1">
        <p
          v-if="changes.length === 0"
          class="md-body-large text-on-surface-variant"
        >
          {{ conflict ? 'Your draft matches the stored settings.' : 'No unsaved changes.' }}
        </p>

        <section
          v-for="section in sections"
          :key="section.domain"
          class="mb-6 flex flex-col gap-4"
          :aria-label="section.title"
        >
          <h3 class="md-title-medium text-on-surface">
            {{ section.title }}
          </h3>
          <MdCard
            v-for="group in section.groups"
            :key="group.key"
            variant="outlined"
            class="flex flex-col gap-2"
          >
            <h4
              v-if="group.id !== null"
              class="md-label-large text-on-surface-variant"
            >
              {{ group.title }}: {{ group.id }}
            </h4>
            <ul class="flex flex-col gap-2">
              <li
                v-for="change in group.changes"
                :key="change.id"
                class="flex flex-col gap-2 rounded-corner-small px-3 py-2 sm:flex-row sm:items-start sm:justify-between"
                :class="{
                  'bg-primary-container text-on-primary-container': change.kind === 'added',
                  'bg-error-container text-on-error-container': change.kind === 'removed'
                }"
              >
                <div class="flex min-w-0 flex-col gap-1">
                  <p class="flex flex-wrap items-center gap-2 md-label-large">
                    <span>{{ labelOf(change) }}</span>
                    <span
                      v-if="badgeOf(change)"
                      class="rounded-corner-full border border-current px-2 md-label-small"
                    >
                      {{ badgeOf(change) }}
                    </span>
                  </p>
                  <p
                    v-if="valueOf(change)"
                    class="break-words md-body-medium"
                  >
                    {{ valueOf(change) }}
                  </p>
                </div>
                <MdButton
                  variant="text"
                  type="button"
                  class="shrink-0 self-start sm:self-auto"
                  :aria-label="revertLabelOf(change)"
                  @click="emit('revert', change)"
                >
                  {{ conflict ? 'Use stored' : 'Revert' }}
                </MdButton>
              </li>
            </ul>
          </MdCard>
        </section>
      </div>

      <footer class="sticky bottom-0 flex flex-col gap-3 border-t border-outline-variant bg-surface-container-low pt-3">
        <p
          v-if="invalid"
          class="md-body-medium text-error"
        >
          Fix the errors before saving.
        </p>
        <div class="flex flex-wrap justify-end gap-2">
          <MdButton
            variant="text"
            type="button"
            :disabled="saving"
            @click="emit('discard')"
          >
            {{ conflict ? 'Take stored settings' : 'Discard all' }}
          </MdButton>
          <MdButton
            v-if="!conflict"
            variant="filled"
            type="button"
            :disabled="invalid || saving"
            @click="emit('save')"
          >
            Save
          </MdButton>
        </div>
      </footer>
    </div>
  </MdSideSheet>
</template>
