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
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import { SCRIM_STYLE } from './dialog'
import { formatChangeValue, groupChanges } from './settingsDiff'

/*
 * Every unsaved change, grouped by domain and entry, with a revert for each one. From 840px it is a
 * side sheet on the right; below that it fills the window. Nothing is written until Save is pressed,
 * and Revert only moves the draft back to the saved value of that one field.
 */
const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  changes: {
    type: Array,
    default: () => []
  },
  invalid: {
    type: Boolean,
    default: false
  },
  saving: {
    type: Boolean,
    default: false
  },
  /*
   * The stored settings changed elsewhere: the changes compare the stored value (before) with the draft (after).
   * Revert then takes the stored value into the draft, and nothing is saved from here; the conflict dialog owns that.
   */
  conflict: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:open', 'revert', 'discard', 'save'])

const sections = computed(() => groupChanges(props.changes))

const close = () => emit('update:open', false)

const labelOf = (change) => {
  if (change.label) return change.label
  return change.group ? 'Entry' : change.domainTitle
}

const valueOf = (change) => {
  if (change.masked) return 'changed'
  if (change.kind === 'changed') return `${formatChangeValue(change.before)} → ${formatChangeValue(change.after)}`
  if (change.kind === 'added') return change.after === undefined ? '' : formatChangeValue(change.after)
  return change.before === undefined ? '' : formatChangeValue(change.before)
}

const badgeOf = (change) => ({ added: 'Added', removed: 'Removed' })[change.kind] ?? ''

const revertLabelOf = (change) => {
  const badge = badgeOf(change)
  return badge ? `Revert ${badge.toLowerCase()} ${labelOf(change)}` : `Revert ${labelOf(change)}`
}
</script>

<template>
  <VueFinalModal
    :model-value="open"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex"
    content-class="flex min-h-full w-full justify-end"
    @update:model-value="emit('update:open', $event)"
  >
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-review-title"
      class="flex min-h-full w-full flex-col bg-surface-container-low text-on-surface shadow-elevation-3 min-[840px]:w-[28rem] min-[840px]:rounded-l-corner-extra-large"
    >
      <header class="flex items-center justify-between gap-2 border-b border-outline-variant px-4 py-4 md:px-6">
        <h2
          id="settings-review-title"
          class="md-title-large text-on-surface"
        >
          {{ conflict ? 'Stored settings compared' : 'Review changes' }}
        </h2>
        <MdButton
          variant="text"
          type="button"
          @click="close"
        >
          Close
        </MdButton>
      </header>

      <div class="flex-1 overflow-y-auto px-4 py-4 md:px-6">
        <p
          v-if="conflict"
          class="pb-4 md-body-medium text-on-surface-variant"
        >
          The stored settings were saved elsewhere. Each change shows the stored value, then your draft.
        </p>
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
          <div
            v-for="group in section.groups"
            :key="group.key"
            class="flex flex-col gap-2 rounded-corner-medium border border-outline-variant bg-surface p-3"
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
          </div>
        </section>
      </div>

      <footer class="flex flex-col gap-3 border-t border-outline-variant px-4 py-3 md:px-6">
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
  </VueFinalModal>
</template>
