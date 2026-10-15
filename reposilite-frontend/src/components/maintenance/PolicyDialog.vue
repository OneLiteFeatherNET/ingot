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
import { computed, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdSelect from '../md/MdSelect.vue'
import MdSwitch from '../md/MdSwitch.vue'
import MdTextField from '../md/MdTextField.vue'
import { SCRIM_STYLE } from '../settings/dialog'
import {
  SCOPES,
  draftFromPolicy,
  emptyDraft,
  policyFromDraft,
  policyWarnings,
  scopeHelp,
  validateDraft
} from './policy'

/*
 * Creates a policy, or edits one. The id of an existing policy is fixed. Errors are shown once the
 * user has tried to save, and a save with an error sends nothing: the parent receives the policy
 * in the wire shape only when every rule holds.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  /** The policy being edited, or null for a new one. */
  policy: {
    type: Object,
    default: null
  },
  existingIds: {
    type: Array,
    default: () => []
  },
  /** The repository ids the maven settings define. */
  repositoryIds: {
    type: Array,
    default: () => []
  },
  saving: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const draft = ref(emptyDraft())
const submitted = ref(false)
const isEditing = computed(() => props.policy !== null)

const errors = computed(() => validateDraft(draft.value, {
  existingIds: props.existingIds,
  editing: isEditing.value
}))
const shownErrors = computed(() => (submitted.value ? errors.value : {}))
const warnings = computed(() => policyWarnings(draft.value))

// Every repository the maven settings define, plus any the policy already names, so that a
// repository removed from the settings can still be seen and unselected.
const repositoryOptions = computed(() => [...new Set([...props.repositoryIds, ...draft.value.repositories])])

const scheduleOptions = [
  { value: 'manual', label: 'Manual only' },
  { value: 'daily', label: 'Daily at a time' }
]
const scopeOptions = SCOPES.map(scope => ({ value: scope.value, label: scope.label }))

watch(() => props.modelValue, (open) => {
  if (!open) return
  draft.value = props.policy ? draftFromPolicy(props.policy) : emptyDraft()
  submitted.value = false
}, { immediate: true })

const toggleRepository = (id) => {
  const selected = draft.value.repositories
  draft.value.repositories = selected.includes(id)
    ? selected.filter(entry => entry !== id)
    : [...selected, id]
}

const close = () => emit('update:modelValue', false)

const submit = () => {
  submitted.value = true
  if (Object.keys(errors.value).length > 0) return
  emit('submit', policyFromDraft(draft.value))
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex min-h-full items-center justify-center p-4"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div
      class="w-[calc(100vw-2rem)] max-w-xl max-h-[90dvh] overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      role="dialog"
      aria-modal="true"
      aria-labelledby="policy-dialog-title"
    >
      <h2
        id="policy-dialog-title"
        class="md-headline-small text-on-surface"
      >
        {{ isEditing ? `Edit ${policy.id}` : 'New cleanup policy' }}
      </h2>

      <form
        id="policy-form"
        class="mt-5 flex flex-col gap-5"
        novalidate
        @submit.prevent="submit"
      >
        <MdTextField
          id="policy-id"
          label="Id"
          variant="outlined"
          autocomplete="off"
          :disabled="isEditing"
          :model-value="draft.id"
          :supporting-text="isEditing ? 'The id cannot be changed after creation.' : 'Letters, digits, dots, hyphens and underscores.'"
          :error="!!shownErrors.id"
          :error-text="shownErrors.id"
          @update:model-value="draft.id = $event"
        />

        <div class="flex flex-col gap-2">
          <MdSwitch
            id="policy-enabled"
            v-model="draft.enabled"
          >
            Enabled
          </MdSwitch>
          <p class="md-body-small text-on-surface-variant">
            Disabled policies can still be previewed and run by hand.
          </p>
        </div>

        <fieldset class="flex flex-col gap-2">
          <legend class="md-label-large text-on-surface-variant">
            Repositories
          </legend>
          <div
            v-if="repositoryOptions.length"
            class="flex flex-wrap gap-2"
          >
            <MdChip
              v-for="id in repositoryOptions"
              :key="id"
              variant="filter"
              :selected="draft.repositories.includes(id)"
              :data-repository="id"
              @click="toggleRepository(id)"
            >
              {{ id }}
            </MdChip>
          </div>
          <p
            v-else
            class="md-body-small text-on-surface-variant"
          >
            No repository is configured yet. Add one in the Maven settings.
          </p>
          <p
            v-for="warning in warnings"
            :key="warning"
            class="md-body-small text-warning"
            role="status"
          >
            {{ warning }}
          </p>
        </fieldset>

        <MdSelect
          id="policy-scope"
          label="Scope"
          :model-value="draft.scope"
          :options="scopeOptions"
          :supporting-text="scopeHelp(draft.scope)"
          @update:model-value="draft.scope = $event"
        />

        <fieldset class="flex flex-col gap-4">
          <legend class="md-label-large text-on-surface-variant">
            Limits. Set at least one.
          </legend>
          <MdTextField
            id="policy-keep-latest"
            label="Keep the newest versions"
            variant="outlined"
            type="number"
            min="1"
            step="1"
            inputmode="numeric"
            :model-value="draft.keepLatest"
            supporting-text="Versions beyond this count are candidates. Leave empty to ignore."
            :error="!!shownErrors.keepLatest"
            :error-text="shownErrors.keepLatest"
            @update:model-value="draft.keepLatest = $event"
          />
          <MdTextField
            id="policy-older-than-days"
            label="Older than (days)"
            variant="outlined"
            type="number"
            min="1"
            step="1"
            inputmode="numeric"
            :model-value="draft.olderThanDays"
            supporting-text="Versions published longer ago than this are candidates."
            :error="!!shownErrors.olderThanDays"
            :error-text="shownErrors.olderThanDays"
            @update:model-value="draft.olderThanDays = $event"
          />
          <MdTextField
            id="policy-not-downloaded-days"
            label="Not downloaded for (days)"
            variant="outlined"
            type="number"
            min="1"
            step="1"
            inputmode="numeric"
            :model-value="draft.notDownloadedForDays"
            supporting-text="Uses the file modification time when no download times exist. Most useful for the mirror cache."
            :error="!!shownErrors.notDownloadedForDays"
            :error-text="shownErrors.notDownloadedForDays"
            @update:model-value="draft.notDownloadedForDays = $event"
          />
          <p
            v-if="shownErrors.retention"
            class="md-body-medium text-error"
            role="alert"
          >
            {{ shownErrors.retention }}
          </p>
        </fieldset>

        <MdTextField
          id="policy-group-filter"
          label="Group filter (optional)"
          variant="outlined"
          autocomplete="off"
          :model-value="draft.groupFilter"
          supporting-text="A Java regular expression matched against the artifact path, for example net/onelitefeather/.*"
          :error="!!shownErrors.groupFilter"
          :error-text="shownErrors.groupFilter"
          @update:model-value="draft.groupFilter = $event"
        />

        <fieldset class="flex flex-col gap-3 pt-3">
          <legend class="md-label-large text-on-surface-variant">
            Schedule
          </legend>
          <MdSelect
            id="policy-schedule"
            label="When to run"
            :model-value="draft.scheduleMode"
            :options="scheduleOptions"
            supporting-text="A scheduled run uses the server's local time zone."
            @update:model-value="draft.scheduleMode = $event"
          />
          <MdTextField
            v-if="draft.scheduleMode === 'daily'"
            id="policy-schedule-time"
            label="Time (HH:mm)"
            variant="outlined"
            type="time"
            :model-value="draft.scheduleTime"
            :error="!!shownErrors.schedule"
            :error-text="shownErrors.schedule"
            @update:model-value="draft.scheduleTime = $event"
          />
        </fieldset>

        <div class="mt-2 flex flex-wrap justify-end gap-2">
          <MdButton
            id="policy-cancel"
            variant="text"
            type="button"
            @click="close"
          >
            Cancel
          </MdButton>
          <MdButton
            id="policy-save"
            variant="filled"
            type="submit"
            :disabled="saving"
          >
            Save policy
          </MdButton>
        </div>
      </form>
    </div>
  </VueFinalModal>
</template>
