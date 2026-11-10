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
import { computed, inject, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DeleteEntryDialog from '../settings/DeleteEntryDialog.vue'
import RepositoryWizard from '../settings/RepositoryWizard.vue'
import { idText } from '../settings/collections'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import usePlaceholders from '../../store/placeholders'
import { copyToClipboard } from '../card/copySnippet'
import RepositoriesTable from './RepositoriesTable.vue'
import { captionOf, filterRows, rowsOf, sortRows } from './repositoryRows'
import { groupsReferencing } from './repositoryReferences'

/*
 * The Repositories page: one table of the repositories of the draft, with a filter, sorting, the row actions and the
 * two flows that change the list, creating (the guided wizard) and deleting (a dialog that asks for the typed name).
 * Every write goes through the store's functions, so the draft, the save bar and the leave guard see it. Nothing is
 * saved here.
 *
 * `schemaFields` are the field names the served maven schema declares for a repository. The Version policy column
 * shows only with versionPolicy; the Write policy column shows when redeployment or readOnly is declared.
 */
const props = defineProps({
  schemaFields: {
    type: Array,
    default: () => []
  }
})

const store = inject(configurationStoreKey, null) ?? useConfiguration().store
const route = useRoute()
const router = useRouter()
const { basePath, productionUrl } = usePlaceholders()

const heading = ref(null)
const newButton = ref(null)
const query = ref('')
const sortKey = ref(null)
const sortDirection = ref('ascending')
const message = ref('')
const wizardOpen = ref(false)
// The id of the repository whose delete is asked for; empty while no delete dialog is open.
const deleteTarget = ref('')
const deleteOpen = computed({
  get: () => deleteTarget.value !== '',
  set: (open) => {
    if (!open) deleteTarget.value = ''
  }
})

const SORT_LABELS = { name: 'Name', type: 'Type', visibility: 'Visibility', storage: 'Storage' }

const loaded = computed(() => store.configurations.value.maven !== undefined)
const entries = computed(() => store.configurations.value.maven?.repositories ?? [])
const savedIds = computed(() => {
  const saved = store.savedSettings.value.maven?.repositories
  return Array.isArray(saved) ? saved.map(idText) : undefined
})
const origin = computed(() => new URL(productionUrl).origin)
const base = computed(() => basePath || '/')
const rows = computed(() => rowsOf(entries.value, { origin: origin.value, basePath: base.value, savedIds: savedIds.value }))
const shownRows = computed(() => {
  const filtered = filterRows(rows.value, query.value)
  return sortKey.value ? sortRows(filtered, sortKey.value, sortDirection.value) : filtered
})
const caption = computed(() => captionOf(shownRows.value.length, rows.value.length))
const countText = computed(() => `${shownRows.value.length} of ${rows.value.length} repositories shown`)
const emptyText = computed(() => (rows.value.length === 0 ? 'No repositories yet.' : 'No repository matches the filter.'))
const hasVersionPolicy = computed(() => props.schemaFields.includes('versionPolicy'))
const hasWritePolicy = computed(() => props.schemaFields.includes('redeployment') || props.schemaFields.includes('readOnly'))

const sortBy = (key) => {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === 'ascending' ? 'descending' : 'ascending'
  else {
    sortKey.value = key
    sortDirection.value = 'ascending'
  }
  message.value = `Sorted by ${SORT_LABELS[key]}, ${sortDirection.value}`
}

const copyAddress = async (row) => {
  const copied = await copyToClipboard(row.address, typeof navigator === 'undefined' ? undefined : navigator.clipboard)
  message.value = copied ? `Copied address of ${row.id}` : `Could not copy the address of ${row.id}`
}

// A query the page acts on once is removed from the address, so a reload or a second visit does not repeat it.
const takeQuery = (key) => {
  const next = { ...route.query }
  delete next[key]
  router.replace({ name: route.name, params: route.params, query: next })
}

// `?new=repository` opens the wizard once, such as the checklist link of the dashboard.
watch(() => route.query.new, (value) => {
  if (value !== 'repository') return
  takeQuery('new')
  wizardOpen.value = true
}, { immediate: true })

// After a repository was deleted on the detail page, the list says so and takes the focus.
watch(() => route.query.deleted, (value) => {
  if (typeof value !== 'string' || value === '') return
  takeQuery('deleted')
  message.value = `${value} deleted, not saved yet`
  nextTick(() => heading.value?.focus())
}, { immediate: true })

// Closing the wizard without creating anything gives the focus back to the button that opened it.
watch(wizardOpen, (open, wasOpen) => {
  if (wasOpen && !open) nextTick(() => newButton.value?.$el?.focus())
})

const deleteReferences = computed(() => groupsReferencing(entries.value, deleteTarget.value))
const askDelete = (id) => {
  deleteTarget.value = id
}

// The row goes out of the draft at once; the save bar counts it. The focus moves to the heading, never to the row.
const confirmDelete = () => {
  const id = deleteTarget.value
  const position = entries.value.findIndex(entry => entry.id === id)
  if (position >= 0) store.removeEntry('maven', 'repositories', position)
  message.value = `${id} deleted, not saved yet`
  nextTick(() => heading.value?.focus())
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="flex min-w-0 flex-col gap-1">
        <h1
          ref="heading"
          tabindex="-1"
          class="md-headline-medium text-on-surface focus:outline-none"
        >
          Repositories
        </h1>
        <p class="md-body-medium text-on-surface-variant">
          Every repository of this instance. Changes are saved with the other settings.
        </p>
      </div>
      <MdButton
        ref="newButton"
        data-test="new-entry"
        variant="filled"
        type="button"
        class="shrink-0"
        @click="wizardOpen = true"
      >
        New repository
      </MdButton>
    </header>

    <p
      v-if="!loaded"
      class="md-body-large text-on-surface-variant"
    >
      Loading configuration
    </p>

    <template v-else>
      <MdTextField
        :model-value="query"
        label="Filter repositories"
        variant="outlined"
        autocomplete="off"
        supporting-text="Search by id, description or tag. Use tag:name for one tag."
        @update:model-value="query = $event"
      />
      <p
        role="status"
        data-test="filter-count"
        class="md-body-medium text-on-surface-variant"
      >
        {{ countText }}
      </p>
      <p
        role="status"
        data-test="status-message"
        class="md-body-medium text-on-surface"
      >
        {{ message }}
      </p>
      <RepositoriesTable
        :rows="shownRows"
        :sort-key="sortKey"
        :sort-direction="sortDirection"
        :version-policy="hasVersionPolicy"
        :write-policy="hasWritePolicy"
        :caption="caption"
        :empty-text="emptyText"
        @sort="sortBy"
        @copy="copyAddress"
        @delete="askDelete"
      />
    </template>

    <RepositoryWizard v-model="wizardOpen" />

    <DeleteEntryDialog
      :id="deleteTarget"
      v-model="deleteOpen"
      :references="deleteReferences"
      @confirm="confirmDelete"
    />
  </div>
</template>
