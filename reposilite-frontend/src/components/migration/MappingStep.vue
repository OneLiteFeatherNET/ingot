<script setup>
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Step 3: the target name, the inclusion and the conflict policy of every repository. The mapping lives in the page
// only; each change is written to the wizard at once, so the dry run starts from exactly what is on screen.
import { computed, inject, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import { formatBytes } from '../maintenance/format'
import { mappingWarnings } from '../../store/migration/gating'
import { MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'

const wizard = inject(MIGRATION_WIZARD, useWizard())

const POLICIES = [
  { value: 'refuse', label: 'Refuse', detail: 'Keep the existing repository and stop the import of the repository that conflicts.' },
  { value: 'abort', label: 'Abort', detail: 'Stop the whole repository at the first conflict.' },
  { value: 'overwrite', label: 'Overwrite', detail: 'Replace the files that exist in the target. Both steps are audited.' },
]

// The rows as the discovery gave them, with the operator's choices from the first visit applied when there are some.
const discoveredRows = () => (wizard.state.discovery?.repositories ?? []).map(repository => ({
  nexusName: repository.name,
  targetName: repository.name,
  included: repository.supported,
  supported: repository.supported,
  format: repository.format,
  type: repository.type,
  bytes: repository.bytes,
}))

// A discovery without a mapping starts one, so the dry run has a mapping to read.
if (wizard.state.discovery && !wizard.state.mapping) {
  wizard.setMapping(discoveredRows(), 'refuse')
}

const rows = computed(() => wizard.state.mapping?.repositories ?? [])
const policy = computed(() => wizard.state.mapping?.conflictPolicy ?? 'refuse')
const warnings = computed(() => mappingWarnings(rows.value))
const warningOf = (nexusName) => warnings.value.find(warning => warning.nexusName === nexusName)?.kind ?? null

// The radio group is driven by `chosen`, so a cancelled overwrite puts the checked radio back on the policy in force.
const chosen = ref(policy.value)
const overwriteOpen = ref(false)
watch(policy, (value) => { chosen.value = value })
watch(chosen, (value) => {
  if (value === policy.value) return
  if (value === 'overwrite') {
    overwriteOpen.value = true
    return
  }
  setPolicy(value)
})

const setPolicy = (value) => wizard.setMapping(rows.value, value)

const confirmOverwrite = () => {
  overwriteOpen.value = false
  setPolicy('overwrite')
}

const cancelOverwrite = () => {
  overwriteOpen.value = false
  chosen.value = policy.value
}

const updateRow = (nexusName, patch) => {
  const next = rows.value.map(row => (row.nexusName === nexusName ? { ...row, ...patch } : row))
  wizard.setMapping(next, policy.value)
}

const rowId = (nexusName) => nexusName.replace(/[^A-Za-z0-9_-]/g, '-')
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-mapping-title"
  >
    <h2
      id="migration-mapping-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Mapping
    </h2>

    <p
      v-if="!wizard.state.discovery"
      class="md-body-large text-on-surface-variant"
    >
      Discover the repositories first.
      <RouterLink
        class="md-focus-ring text-primary"
        :to="{ name: 'settings-migration', params: { step: 'discover' } }"
      >
        Go to Discover
      </RouterLink>
    </p>

    <template v-else>
      <fieldset class="flex flex-col gap-2">
        <legend class="md-title-small text-on-surface">
          When a repository already exists in Ingot
        </legend>
        <label
          v-for="choice in POLICIES"
          :key="choice.value"
          class="flex items-start gap-2 md-body-large"
        >
          <input
            v-model="chosen"
            type="radio"
            name="migration-conflict-policy"
            :value="choice.value"
            :data-policy="choice.value"
            class="mt-1"
          >
          <span>
            <span class="block">{{ choice.label }}</span>
            <span class="block md-body-medium text-on-surface-variant">{{ choice.detail }}</span>
          </span>
        </label>
      </fieldset>

      <table
        data-test="mapping"
        class="w-full text-left md-body-medium"
      >
        <caption class="md-title-small pb-2 text-left text-on-surface">
          Repositories to import
        </caption>
        <thead>
          <tr>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Nexus name
            </th>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Format
            </th>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Size
            </th>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Target name
            </th>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Include
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.nexusName"
            :data-row="row.nexusName"
          >
            <th
              scope="row"
              class="py-2 pr-4 font-normal"
            >
              {{ row.nexusName }}
            </th>
            <td class="py-2 pr-4">
              {{ row.format }} ({{ row.type }})
            </td>
            <td class="py-2 pr-4">
              {{ formatBytes(row.bytes) }}
            </td>
            <td class="py-2 pr-4">
              <template v-if="row.supported">
                <MdTextField
                  :id="`migration-target-${rowId(row.nexusName)}`"
                  :label="`Target name for ${row.nexusName}`"
                  variant="outlined"
                  autocomplete="off"
                  :model-value="row.targetName"
                  :error="warningOf(row.nexusName) !== null"
                  :error-text="warningOf(row.nexusName) === 'empty' ? 'Enter a target name.' : warningOf(row.nexusName) === 'duplicate' ? 'Shares this name with another included repository.' : ''"
                  @update:model-value="updateRow(row.nexusName, { targetName: $event })"
                />
              </template>
              <span v-else>Excluded: the format is not supported</span>
            </td>
            <td class="py-2 pr-4">
              <label
                v-if="row.supported"
                class="flex items-center gap-2"
              >
                <input
                  :id="`migration-include-${rowId(row.nexusName)}`"
                  type="checkbox"
                  :checked="row.included"
                  @change="updateRow(row.nexusName, { included: $event.target.checked })"
                >
                Include {{ row.nexusName }}
              </label>
            </td>
          </tr>
        </tbody>
      </table>

      <div>
        <RouterLink
          class="md-focus-ring md-label-large text-primary"
          :to="{ name: 'settings-migration', params: { step: 'dry-run' } }"
        >
          Continue to Dry run
        </RouterLink>
      </div>
    </template>

    <Teleport to="body">
      <div
        v-if="overwriteOpen"
        class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/40 p-4"
      >
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="migration-overwrite-title"
          class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface"
        >
          <h2
            id="migration-overwrite-title"
            class="md-headline-small"
          >
            Replace existing files?
          </h2>
          <p class="mt-3 md-body-medium text-on-surface-variant">
            Existing files will be replaced in every repository that conflicts. This cannot be undone.
          </p>
          <div class="mt-6 flex justify-end gap-2">
            <MdButton
              id="migration-overwrite-cancel"
              variant="text"
              @click="cancelOverwrite"
            >
              Cancel
            </MdButton>
            <MdButton
              id="migration-overwrite-confirm"
              variant="filled"
              @click="confirmOverwrite"
            >
              Replace files
            </MdButton>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>
