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
/*
 * A table with a caption, a row header and sortable columns. The table scrolls sideways inside a named region when the
 * page is narrow. A column with a priority is hidden below a width: low below the md breakpoint, and medium, expanded,
 * large and xlarge below that window class. A column with priority base or high (the default) shows at every width.
 *
 * Props:
 *   caption     the visible caption, and the name of the scroll region
 *   columns     { key, label, sortable?, priority? }, the priority being high (the default), low, base, medium,
 *               expanded, large or xlarge
 *   rows        one object per row, keyed by the column keys. The first column is the row header (th scope row).
 *   modelValue  the sort, { key, direction } with direction ascending or descending (v-model). Without it the table
 *               keeps the sort itself.
 *   captionHidden  the caption stays in the DOM for screen readers but is visually hidden (default false)
 *   stickyHeader   the header cells stick to the top of the scroll region (default false)
 *   label       the name of the scroll region, when it is not the caption
 *   presorted   the rows come in their sorted order already: the table shows the sort and reports a choice, but does
 *               not sort the rows itself (default false)
 *   rowKey      the column key whose value identifies a row, so a row keeps its element when the order changes
 *   rowAttrs    attributes of each row element, as { attribute: column key }: the attribute takes the row's value
 * A column with fill takes the remaining width, so a cell that truncates its text has a width to truncate to.
 * Emits update:modelValue with the next sort when a sortable header is chosen.
 * Slot cell: { row, column, value }, for a cell that is more than its text.
 * Slot empty: shown in one cell across every column while there are no rows.
 */
import { computed, ref } from 'vue'
import MdIcon from './MdIcon.vue'
import { sortRows, type SortDirection } from './MdDataTable.sort'

type Direction = SortDirection
type Priority = 'high' | 'low' | 'base' | 'medium' | 'expanded' | 'large' | 'xlarge'
type Align = 'start' | 'end' | 'center'
interface Column {
  key: string
  label: string
  sortable?: boolean
  priority?: Priority
  align?: Align
  fill?: boolean
}
interface Sort {
  key: string
  direction: Direction
}

const props = withDefaults(
  defineProps<{
    caption: string
    columns: Column[]
    rows: Record<string, unknown>[]
    modelValue?: Sort | null
    captionHidden?: boolean
    stickyHeader?: boolean
    label?: string
    presorted?: boolean
    rowKey?: string
    rowAttrs?: Record<string, string>
  }>(),
  {
    modelValue: undefined,
    captionHidden: false,
    stickyHeader: false,
    label: undefined,
    presorted: false,
    rowKey: undefined,
    rowAttrs: undefined,
  },
)

const emit = defineEmits<{ 'update:modelValue': [sort: Sort] }>()

const own = ref<Sort | null>(null)
const sort = computed<Sort | null>(() => (props.modelValue === undefined ? own.value : props.modelValue))

const sortedRows = computed(() => {
  const current = sort.value
  return current && !props.presorted ? sortRows(props.rows, current.key, current.direction) : props.rows
})

// The first click on a column sorts it ascending, the next one descending, and so on.
const choose = (column: Column) => {
  const current = sort.value
  const direction: Direction = current?.key === column.key && current.direction === 'ascending' ? 'descending' : 'ascending'
  const next = { key: column.key, direction }
  own.value = next
  emit('update:modelValue', next)
}

const ariaSort = (column: Column) => {
  if (!column.sortable) return undefined
  return sort.value?.key === column.key ? sort.value.direction : 'none'
}

const CHEVRON = 'M6 9l6 6 6-6'
// The classes are written out in full, so Tailwind finds them in the source.
const PRIORITY_CLASS: Record<Priority, string> = {
  high: '',
  base: '',
  low: 'hidden md:table-cell',
  medium: 'hidden medium:table-cell',
  expanded: 'hidden expanded:table-cell',
  large: 'hidden large:table-cell',
  xlarge: 'hidden xlarge:table-cell',
}
const priorityClass = (column: Column) => PRIORITY_CLASS[column.priority ?? 'high']
const chevronClass = (column: Column) => (ariaSort(column) === 'descending' ? 'rotate-180' : '')
// The classes are written out in full, so Tailwind finds them in the source. A column without an align starts its text.
const ALIGN_CLASS: Record<Align, string> = { start: 'text-start', end: 'text-end', center: 'text-center' }
const alignClass = (column?: Column) => ALIGN_CLASS[column?.align ?? 'start']
const fillClass = (column?: Column) => (column?.fill ? 'w-full max-w-0' : '')
const keyOf = (row: Record<string, unknown>, index: number) => (props.rowKey ? String(row[props.rowKey]) : index)
const attrsOf = (row: Record<string, unknown>) =>
  Object.fromEntries(Object.entries(props.rowAttrs ?? {}).map(([attribute, key]) => [attribute, String(row[key])]))
const headerClass = computed(() => (props.stickyHeader ? 'sticky top-0 z-md-sticky bg-surface' : ''))
</script>

<template>
  <div
    role="region"
    :aria-label="label ?? caption"
    tabindex="0"
    class="overflow-x-auto rounded-corner-small focus-visible:outline-2 focus-visible:outline-primary"
    data-ingot-component="md-data-table"
  >
    <table class="w-full min-w-120 border-collapse text-left text-on-surface">
      <caption
        class="md-title-small px-4 py-3 text-left text-on-surface"
        :class="{ 'sr-only': captionHidden }"
      >
        {{ caption }}
      </caption>
      <thead data-ingot-part="header">
        <tr class="border-b border-outline-variant">
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            :aria-sort="ariaSort(column)"
            class="md-label-large whitespace-nowrap px-4 py-3 align-middle text-on-surface-variant"
            :class="[priorityClass(column), alignClass(column), fillClass(column), headerClass]"
          >
            <button
              v-if="column.sortable"
              type="button"
              class="inline-flex min-h-6 min-w-6 items-center gap-1 rounded-corner-small focus-visible:outline-2 focus-visible:outline-primary"
              @click="choose(column)"
            >
              {{ column.label }}
              <MdIcon
                :path="CHEVRON"
                size="sm"
                class="transition-transform duration-md-short3"
                :class="chevronClass(column)"
              />
            </button>
            <template v-else>
              {{ column.label }}
            </template>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, index) in sortedRows"
          :key="keyOf(row, index)"
          class="border-b border-outline-variant"
          data-ingot-part="row"
          v-bind="attrsOf(row)"
        >
          <th
            v-if="columns[0]"
            scope="row"
            class="md-body-medium px-4 py-3 align-middle text-on-surface"
            :class="[alignClass(columns[0]), fillClass(columns[0])]"
          >
            <slot
              name="cell"
              :row="row"
              :column="columns[0]"
              :value="row[columns[0].key]"
            >
              {{ row[columns[0].key] }}
            </slot>
          </th>
          <td
            v-for="column in columns.slice(1)"
            :key="column.key"
            class="md-body-medium px-4 py-3 align-middle text-on-surface-variant"
            :class="[priorityClass(column), alignClass(column), fillClass(column)]"
          >
            <slot
              name="cell"
              :row="row"
              :column="column"
              :value="row[column.key]"
            >
              {{ row[column.key] }}
            </slot>
          </td>
        </tr>
        <tr
          v-if="sortedRows.length === 0 && $slots.empty"
          class="border-b border-outline-variant"
        >
          <td
            :colspan="columns.length"
            class="md-body-medium px-4 py-6 text-on-surface-variant"
          >
            <slot name="empty" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
