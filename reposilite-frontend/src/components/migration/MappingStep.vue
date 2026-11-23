<script setup lang="ts">
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
import MdDataTable from '../md/MdDataTable.vue'
import MdTextField from '../md/MdTextField.vue'
import MigrationDialog from './MigrationDialog.vue'
import { formatBytes } from '../maintenance/format'
import { mappingWarnings } from '../../store/migration/gating'
import { MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'
import type { MappingRow } from '../../store/migration/types'
import type { DiscoveredNexus } from './types'

const wizard = inject(MIGRATION_WIZARD, useWizard())

const POLICIES = [
  { value: 'refuse', label: 'Refuse', detail: 'Keep the existing repository and stop the import of the repository that conflicts.' },
  { value: 'abort', label: 'Abort', detail: 'Stop the whole repository at the first conflict.' },
  { value: 'overwrite', label: 'Overwrite', detail: 'Replace the files that exist in the target. Both steps are audited.' },
]

// The rows as the discovery gave them, with the operator's choices from the first visit applied when there are some.
const discoveredRows = (): MappingRow[] => ((wizard.state.discovery as DiscoveredNexus | null)?.repositories ?? []).map(repository => ({
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
const MAPPING_COLUMNS = [
  { key: 'nexusName', label: 'Nexus name' },
  { key: 'format', label: 'Format' },
  { key: 'size', label: 'Size' },
  { key: 'targetName', label: 'Target name' },
  { key: 'included', label: 'Include' },
]
const tableRows = computed(() => rows.value.map(row => ({
  ...row,
  format: `${row.format} (${row.type})`,
  size: formatBytes(row.bytes),
})))

// The cell slot hands over the table row, which is the mapping row plus the two formatted columns.
const cellRow = (cell: Record<string, unknown>) => cell as unknown as MappingRow

const warnings = computed(() => mappingWarnings(rows.value))
const warningOf = (nexusName: string) => warnings.value.find(warning => warning.nexusName === nexusName)?.kind ?? null

// The radio group is driven by `chosen`, so a cancelled overwrite puts the checked radio back on the policy in force.
const chosen = ref<string>(policy.value)
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

const setPolicy = (value: string) => wizard.setMapping(rows.value, value)

// The policy is written before the dialog closes, so closing it any other way keeps the policy in force.
const confirmOverwrite = () => {
  setPolicy('overwrite')
  overwriteOpen.value = false
}

const cancelOverwrite = () => {
  overwriteOpen.value = false
}

watch(overwriteOpen, (open) => {
  if (!open && policy.value !== 'overwrite') chosen.value = policy.value
})

const updateRow = (nexusName: string, patch: Partial<MappingRow>) => {
  const next = rows.value.map(row => (row.nexusName === nexusName ? { ...row, ...patch } : row))
  wizard.setMapping(next, policy.value)
}

// The include box of a row: the checked state of the box the event came from.
const setIncluded = (nexusName: string, event: Event) => updateRow(nexusName, { included: (event.target as HTMLInputElement).checked })

const rowId = (nexusName: string) => nexusName.replace(/[^A-Za-z0-9_-]/g, '-')
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-mapping-title"
    data-ingot-component="mapping-step"
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

      <MdDataTable
        data-test="mapping"
        caption="Repositories to import"
        :columns="MAPPING_COLUMNS"
        :rows="tableRows"
        :row-attrs="{ 'data-row': 'nexusName' }"
      >
        <template #cell="{ row: cell, column, value }">
          <template v-if="column.key === 'targetName'">
            <MdTextField
              v-if="cellRow(cell).supported"
              :id="`migration-target-${rowId(cellRow(cell).nexusName)}`"
              :label="`Target name for ${cellRow(cell).nexusName}`"
              variant="outlined"
              autocomplete="off"
              :model-value="cellRow(cell).targetName ?? ''"
              :error="warningOf(cellRow(cell).nexusName) !== null"
              :error-text="warningOf(cellRow(cell).nexusName) === 'empty' ? 'Enter a target name.' : warningOf(cellRow(cell).nexusName) === 'duplicate' ? 'Shares this name with another included repository.' : ''"
              @update:model-value="updateRow(cellRow(cell).nexusName, { targetName: $event })"
            />
            <span v-else>Excluded: the format is not supported</span>
          </template>
          <template v-else-if="column.key === 'included'">
            <label
              v-if="cellRow(cell).supported"
              class="flex items-center gap-2"
            >
              <input
                :id="`migration-include-${rowId(cellRow(cell).nexusName)}`"
                type="checkbox"
                :checked="cellRow(cell).included"
                @change="setIncluded(cellRow(cell).nexusName, $event)"
              >
              Include {{ cellRow(cell).nexusName }}
            </label>
          </template>
          <template v-else>
            {{ value }}
          </template>
        </template>
      </MdDataTable>

      <div>
        <RouterLink
          class="md-focus-ring md-label-large text-primary"
          :to="{ name: 'settings-migration', params: { step: 'dry-run' } }"
        >
          Continue to Dry run
        </RouterLink>
      </div>
    </template>

    <MigrationDialog
      v-model="overwriteOpen"
      title-id="migration-overwrite-title"
      title="Replace existing files?"
    >
      <p>
        Existing files will be replaced in every repository that conflicts. This cannot be undone.
      </p>
      <template #actions>
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
          error
          @click="confirmOverwrite"
        >
          Replace files
        </MdButton>
      </template>
    </MigrationDialog>
  </section>
</template>
