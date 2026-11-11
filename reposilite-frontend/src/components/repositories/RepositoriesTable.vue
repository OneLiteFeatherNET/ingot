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
import { RouterLink, useRouter } from 'vue-router'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import TypeBadge from './TypeBadge.vue'
import { WRITE_CHOICES } from './writePolicy'
import { MIXED_WARNING } from './repositoryReferences'

/*
 * The table of the repositories: a plain HTML table, not an ARIA grid, so each row is reached with Tab in the order
 * name link, then its Actions button, and a screen reader reads it with its table commands. The name cell is the row
 * header, and every column header carries scope="col". Only the sorted column has aria-sort. The actions of a row are
 * one menu, and every action is named after the repository it acts on. Columns are hidden by priority at narrower
 * widths; Name, Type and Actions are always shown. The table itself is the only thing that scrolls on a narrow screen,
 * inside a named region.
 */
const props = defineProps({
  // The rows the page shows, already filtered and sorted (see repositoryRows.js).
  rows: {
    type: Array,
    required: true
  },
  // The column the rows are sorted by, or null when they keep the order of the draft.
  sortKey: {
    type: String,
    default: null
  },
  sortDirection: {
    type: String,
    default: 'ascending'
  },
  // The Version policy column is shown only when the served schema has the field.
  versionPolicy: {
    type: Boolean,
    default: false
  },
  // The Write policy column is shown only when the served schema has the fields it reads.
  writePolicy: {
    type: Boolean,
    default: false
  },
  caption: {
    type: String,
    required: true
  },
  emptyText: {
    type: String,
    default: 'No repositories yet.'
  }
})

const emit = defineEmits(['sort', 'copy', 'delete'])

const router = useRouter()

const SORT_ARROWS = { ascending: '▲', descending: '▼' }
const VERSION_LABELS = { RELEASE: 'Release', SNAPSHOT: 'Snapshot', MIXED: 'Mixed' }
const WRITE_LABELS = Object.fromEntries(WRITE_CHOICES.map(choice => [choice.value, choice.label]))

const columnCount = computed(() => 6 + (props.versionPolicy ? 1 : 0) + (props.writePolicy ? 1 : 0))

const ariaSort = (key) => (props.sortKey === key ? props.sortDirection : undefined)
const arrowOf = (key) => (props.sortKey === key ? SORT_ARROWS[props.sortDirection] : '')

// A group has no version policy of its own; an absent one means Mixed, as the server reads it.
const versionLabel = (row) => (row.type.key === 'GROUP' ? '-' : (VERSION_LABELS[row.versionPolicy] ?? 'Mixed'))
const writeLabel = (row) => (row.writePolicy ? WRITE_LABELS[row.writePolicy] : '-')

// A row opens its detail at its route: the id, or a key address for an entry without one.
const detailHref = (route) => router.resolve({ name: 'settings-repository', params: { id: route } }).href
const openDetail = (route) => router.push({ name: 'settings-repository', params: { id: route } })

// The actions of a row, in one menu. An unnamed entry has no address to copy, so it has no copy action.
const actionsOf = (row) => [
  { value: 'edit', label: 'Edit', ariaLabel: `Edit ${row.name}`, href: detailHref(row.route) },
  ...(row.unnamed ? [] : [{ value: 'copy', label: 'Copy address', ariaLabel: `Copy address of ${row.name}` }]),
  { value: 'delete', label: 'Delete', ariaLabel: `Delete ${row.name}`, error: true }
]
const onAction = (row, value) => {
  if (value === 'edit') openDetail(row.route)
  else if (value === 'copy') emit('copy', row)
  else emit('delete', row)
}
</script>

<template>
  <div
    role="region"
    aria-label="Repositories table"
    tabindex="0"
    class="overflow-x-auto rounded-corner-medium md-focus-ring"
  >
    <table class="w-full min-w-[40rem] border-collapse text-left">
      <caption class="pb-2 text-left md-body-medium text-on-surface-variant">
        {{ caption }}
      </caption>
      <thead>
        <tr class="border-b border-outline-variant">
          <th
            scope="col"
            class="px-3 py-2 text-left md-label-large text-on-surface"
            :aria-sort="ariaSort('name')"
          >
            <button
              type="button"
              class="md-focus-ring min-h-6 min-w-6 rounded-corner-small py-1 text-left"
              @click="emit('sort', 'name')"
            >
              Name<span aria-hidden="true">{{ arrowOf('name') }}</span>
            </button>
          </th>
          <th
            scope="col"
            class="px-3 py-2 text-left md-label-large text-on-surface"
            :aria-sort="ariaSort('type')"
          >
            <button
              type="button"
              class="md-focus-ring min-h-6 min-w-6 rounded-corner-small py-1 text-left"
              @click="emit('sort', 'type')"
            >
              Type<span aria-hidden="true">{{ arrowOf('type') }}</span>
            </button>
          </th>
          <th
            scope="col"
            class="hidden px-3 py-2 text-left md-label-large text-on-surface min-[1600px]:table-cell"
            :aria-sort="ariaSort('visibility')"
          >
            <button
              type="button"
              class="md-focus-ring min-h-6 min-w-6 rounded-corner-small py-1 text-left"
              @click="emit('sort', 'visibility')"
            >
              Visibility<span aria-hidden="true">{{ arrowOf('visibility') }}</span>
            </button>
          </th>
          <th
            v-if="versionPolicy"
            scope="col"
            class="hidden px-3 py-2 text-left md-label-large text-on-surface min-[1600px]:table-cell"
          >
            Version policy
          </th>
          <th
            v-if="writePolicy"
            scope="col"
            class="hidden whitespace-nowrap px-3 py-2 text-left md-label-large text-on-surface min-[1240px]:table-cell"
          >
            Write policy
          </th>
          <th
            scope="col"
            class="hidden px-3 py-2 text-left md-label-large text-on-surface min-[1920px]:table-cell"
            :aria-sort="ariaSort('storage')"
          >
            <button
              type="button"
              class="md-focus-ring min-h-6 min-w-6 rounded-corner-small py-1 text-left"
              @click="emit('sort', 'storage')"
            >
              Storage<span aria-hidden="true">{{ arrowOf('storage') }}</span>
            </button>
          </th>
          <th
            scope="col"
            class="hidden px-3 py-2 text-left md-label-large text-on-surface min-[1240px]:table-cell"
          >
            Address
          </th>
          <th
            scope="col"
            class="px-3 py-2 text-left md-label-large text-on-surface"
          >
            Actions
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.route"
          class="border-b border-outline-variant align-top"
        >
          <th
            scope="row"
            class="min-w-48 w-full max-w-0 px-3 py-3 text-left md-body-large text-on-surface"
          >
            <RouterLink
              :to="{ name: 'settings-repository', params: { id: row.route } }"
              :title="row.name"
              class="md-focus-ring md-touch-target relative block truncate rounded-corner-small text-primary"
            >
              {{ row.name }}
            </RouterLink>
            <span
              v-if="row.unsaved"
              class="ml-2 md-label-medium text-warning"
            >Unsaved</span>
            <span
              v-if="row.unnamed"
              class="block md-body-small text-on-surface-variant"
            >Enter an id.</span>
          </th>
          <td class="px-3 py-3">
            <div class="flex flex-col gap-2">
              <TypeBadge :type="row.type" />
              <p
                v-if="row.type.key === 'MIXED'"
                class="md-body-small text-warning"
              >
                {{ MIXED_WARNING }}
              </p>
            </div>
          </td>
          <td class="hidden px-3 py-3 md-body-medium text-on-surface min-[1600px]:table-cell">
            {{ row.visibility }}
          </td>
          <td
            v-if="versionPolicy"
            class="hidden px-3 py-3 md-body-medium text-on-surface min-[1600px]:table-cell"
          >
            {{ versionLabel(row) }}
          </td>
          <td
            v-if="writePolicy"
            class="hidden px-3 py-3 md-body-medium text-on-surface min-[1240px]:table-cell"
          >
            {{ writeLabel(row) }}
          </td>
          <td class="hidden px-3 py-3 md-body-medium text-on-surface min-[1920px]:table-cell">
            {{ row.storage }}
          </td>
          <td class="hidden w-full max-w-0 px-3 py-3 min-[1240px]:table-cell">
            <code
              :title="row.address"
              class="block truncate md-body-small text-on-surface-variant"
            >{{ row.address }}</code>
          </td>
          <td class="px-3 py-2">
            <MdOverflowMenu
              :label="`Actions for ${row.name}`"
              :items="actionsOf(row)"
              @select="(value) => onAction(row, value)"
            />
          </td>
        </tr>
        <tr v-if="rows.length === 0">
          <td
            :colspan="columnCount"
            class="px-3 py-6 md-body-medium text-on-surface-variant"
          >
            {{ emptyText }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
