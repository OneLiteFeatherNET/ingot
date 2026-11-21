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
import { RouterLink, useRouter } from 'vue-router'
import MdDataTable from '../md/MdDataTable.vue'
import MdIcon from '../md/MdIcon.vue'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import TypeBadge from './TypeBadge.vue'
import { WRITE_CHOICES } from './writePolicy'
import { MIXED_WARNING } from './repositoryReferences'
import type { RepositoryRow, SortDirection, SortKey } from './repositoryRows'

/*
 * The table of the repositories: a plain HTML table, not an ARIA grid, so each row is reached with Tab in the order
 * name link, then its Actions button, and a screen reader reads it with its table commands. The name cell is the row
 * header, and every column header carries scope="col". Only the sorted column has aria-sort. The actions of a row are
 * one menu, and every action is named after the repository it acts on. The table is the shared MdDataTable: columns
 * are hidden by window class priority at narrower widths; Name, Type and Actions are always shown. The table itself is
 * the only thing that scrolls on a narrow screen, inside a named region. The page sorts the rows (repositoryRows.ts),
 * so the table shows them in the order it is given and only reports the column that was chosen.
 */
const props = withDefaults(defineProps<{
  // The rows the page shows, already filtered and sorted (see repositoryRows.ts).
  rows: RepositoryRow[]
  // The column the rows are sorted by, or null when they keep the order of the draft.
  sortKey?: SortKey | null
  sortDirection?: SortDirection
  // The Version policy column is shown only when the served schema has the field.
  versionPolicy?: boolean
  // The Write policy column is shown only when the served schema has the fields it reads.
  writePolicy?: boolean
  caption: string
  emptyText?: string
}>(), {
  sortKey: null,
  sortDirection: 'ascending',
  versionPolicy: false,
  writePolicy: false,
  emptyText: 'No repositories yet.'
})

const emit = defineEmits<{
  sort: [key: SortKey]
  copy: [row: RepositoryRow]
  delete: [row: RepositoryRow]
}>()

const router = useRouter()

// Three dots on one vertical line: zero-length strokes with round caps, drawn by MdIcon.
const MORE_PATH = 'M12 5.25h.01M12 12h.01M12 18.75h.01'

const VERSION_LABELS: Record<string, string> = { RELEASE: 'Release', SNAPSHOT: 'Snapshot', MIXED: 'Mixed' }
const WRITE_LABELS: Record<string, string> = Object.fromEntries(WRITE_CHOICES.map(choice => [choice.value, choice.label]))

// Visibility, Version policy and Storage need the room of an xlarge window; Write policy and Address that of a large one.
const columns = computed(() => [
  { key: 'name', label: 'Name', sortable: true },
  { key: 'type', label: 'Type', sortable: true },
  { key: 'visibility', label: 'Visibility', sortable: true, priority: 'xlarge' as const },
  ...(props.versionPolicy ? [{ key: 'versionPolicy', label: 'Version policy', priority: 'xlarge' as const }] : []),
  ...(props.writePolicy ? [{ key: 'writePolicy', label: 'Write policy', priority: 'large' as const }] : []),
  { key: 'storage', label: 'Storage', sortable: true, priority: 'xlarge' as const },
  { key: 'address', label: 'Address', priority: 'large' as const, fill: true },
  { key: 'actions', label: 'Actions' },
])

// The sort the page keeps, as the table reads it, and the column the table reports when a header is chosen.
const sort = computed(() => (props.sortKey ? { key: props.sortKey, direction: props.sortDirection } : null))
const onSort = (next: { key: string }) => emit('sort', next.key as SortKey)

// The table takes plain records; each cell reads its repository back from the row.
const tableRows = computed(() => props.rows as unknown as Record<string, unknown>[])
const repositoryOf = (row: Record<string, unknown>) => row as unknown as RepositoryRow
// Each row carries the name it shows (the anchor spec: a repository row has data-ingot-repository).
const ROW_ANCHORS = { 'data-ingot-repository': 'name' }

// A group has no version policy of its own; an absent one means Mixed, as the server reads it.
const versionLabel = (row: RepositoryRow): string => (row.type.key === 'GROUP' ? '-' : (VERSION_LABELS[String(row.versionPolicy)] ?? 'Mixed'))
const writeLabel = (row: RepositoryRow): string => (row.writePolicy ? WRITE_LABELS[row.writePolicy] : '-')

// A row opens its detail at its route: the id, or a key address for an entry without one.
const detailHref = (route: string): string => router.resolve({ name: 'settings-repository', params: { id: route } }).href
const openDetail = (route: string) => router.push({ name: 'settings-repository', params: { id: route } })

// The actions of a row, in one menu. An unnamed entry has no address to copy, so it has no copy action.
const actionsOf = (row: RepositoryRow) => [
  { value: 'edit', label: 'Edit', ariaLabel: `Edit ${row.name}`, href: detailHref(row.route) },
  ...(row.unnamed ? [] : [{ value: 'copy', label: 'Copy address', ariaLabel: `Copy address of ${row.name}` }]),
  { value: 'delete', label: 'Delete', ariaLabel: `Delete ${row.name}`, error: true }
]
const onAction = (row: RepositoryRow, value: string) => {
  if (value === 'edit') openDetail(row.route)
  else if (value === 'copy') emit('copy', row)
  else emit('delete', row)
}
</script>

<template>
  <div data-ingot-component="repositories-table">
    <MdDataTable
      :caption="caption"
      caption-hidden
      label="Repositories table"
      :columns="columns"
      :rows="tableRows"
      :model-value="sort"
      presorted
      row-key="route"
      :row-attrs="ROW_ANCHORS"
      @update:model-value="onSort"
    >
      <template #cell="{ row, column }">
        <div
          v-if="column.key === 'name'"
          data-ingot-part="cell-name"
        >
          <RouterLink
            :to="{ name: 'settings-repository', params: { id: repositoryOf(row).route } }"
            :title="repositoryOf(row).name"
            class="md-focus-ring md-touch-target relative block max-w-80 truncate rounded-corner-small text-primary"
          >
            {{ repositoryOf(row).name }}
          </RouterLink>
          <span
            v-if="repositoryOf(row).unsaved"
            class="ml-2 md-label-medium text-warning"
          >Unsaved</span>
          <span
            v-if="repositoryOf(row).unnamed"
            class="block md-body-small text-on-surface-variant"
          >Enter an id.</span>
        </div>
        <div
          v-else-if="column.key === 'type'"
          class="flex flex-col gap-2"
        >
          <div class="w-max">
            <TypeBadge :type="repositoryOf(row).type" />
          </div>
          <p
            v-if="repositoryOf(row).type.key === 'MIXED'"
            class="md-body-small text-warning"
          >
            {{ MIXED_WARNING }}
          </p>
        </div>
        <template v-else-if="column.key === 'versionPolicy'">
          {{ versionLabel(repositoryOf(row)) }}
        </template>
        <template v-else-if="column.key === 'writePolicy'">
          {{ writeLabel(repositoryOf(row)) }}
        </template>
        <code
          v-else-if="column.key === 'address'"
          :title="repositoryOf(row).address"
          class="block truncate md-body-small text-on-surface-variant"
        >{{ repositoryOf(row).address }}</code>
        <MdOverflowMenu
          v-else-if="column.key === 'actions'"
          :label="`Actions for ${repositoryOf(row).name}`"
          :items="actionsOf(repositoryOf(row))"
          trigger-class="!px-0 w-10"
          @select="(value) => onAction(repositoryOf(row), value)"
        >
          <template #trigger>
            <span class="sr-only">Actions for {{ repositoryOf(row).name }}</span>
            <MdIcon :path="MORE_PATH" />
          </template>
        </MdOverflowMenu>
        <template v-else>
          {{ row[column.key] }}
        </template>
      </template>
      <template #empty>
        <p
          data-ingot-part="empty"
          data-ingot-state="empty"
        >
          {{ emptyText }}
        </p>
      </template>
    </MdDataTable>
  </div>
</template>
