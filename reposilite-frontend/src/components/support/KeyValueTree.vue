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
import { computed, reactive } from 'vue'
import MdBadge from '../md/MdBadge.vue'
import MdButton from '../md/MdButton.vue'
import MdIcon from '../md/MdIcon.vue'
import { buildKeyTree, filterTree, groupLabels, OPEN_GROUPS_UP_TO, splitMatch, type KeySplit, type TreeNode } from './keyTree'

// One section of keys and values, grouped by prefix. The rows form one table, so every value starts in the same column.
// A group is a row with a toggle and its key count; its keys follow it while it is open. Values are shown exactly as given.
// The filter is owned by the page: a query opens the groups on the path to each match and marks the matching text.
const props = withDefaults(defineProps<{
  entries: [string, string][]
  split: KeySplit
  label: string
  title: string
  headingId?: string
  query?: string
}>(), { headingId: undefined, query: '' })

// One row of the table. A group row has no value; a leaf row has no count.
interface Row {
  kind: 'leaf' | 'group'
  key: string
  depth: number
  value: string
  count: number
  open: boolean
}

const CHEVRON = 'M6 9l6 6 6-6'
// The indent of a level is a style utility (md-tree-depth-N), so the classes are written out here for the style gate.
const DEPTH_CLASSES = ['md-tree-depth-0', 'md-tree-depth-1', 'md-tree-depth-2', 'md-tree-depth-3', 'md-tree-depth-4', 'md-tree-depth-5', 'md-tree-depth-6']
const depthClass = (depth: number) => DEPTH_CLASSES[Math.min(depth, DEPTH_CLASSES.length - 1)]
// Below medium width the value sits under its key, one level deeper, so the value cell takes the indent of the next level
// there only. Index d holds the indent of level d + 1, so the deepest class is md-tree-depth-6.
const VALUE_DEPTH_CLASSES = ['max-medium:md-tree-depth-1', 'max-medium:md-tree-depth-2', 'max-medium:md-tree-depth-3', 'max-medium:md-tree-depth-4', 'max-medium:md-tree-depth-5', 'max-medium:md-tree-depth-6']
const valueDepthClass = (depth: number) => VALUE_DEPTH_CLASSES[Math.min(depth, VALUE_DEPTH_CLASSES.length - 1)]
const MARK_CLASS = 'rounded-corner-small bg-primary-container text-on-primary-container'

const filtering = computed(() => props.query.trim() !== '')
const tree = computed(() => buildKeyTree(props.entries, props.split))
const shown = computed(() => filterTree(tree.value, props.query))
const defaultOpen = computed(() => props.entries.length <= OPEN_GROUPS_UP_TO)

// Only the user, Expand all and Collapse all change these, so clearing the filter brings back the state before it.
const userOpen = reactive(new Map<string, boolean>())

// While filtering, a group on the path to a match is open unless the user closed it.
const isOpen = (label: string) => {
  if (filtering.value) return userOpen.get(label) !== false
  return userOpen.get(label) ?? defaultOpen.value
}

const toggle = (label: string, open: boolean) => userOpen.set(label, !open)
const expandAll = () => groupLabels(tree.value).forEach((label) => userOpen.set(label, true))
const collapseAll = () => groupLabels(tree.value).forEach((label) => userOpen.set(label, false))

// The rows in reading order: a group, then its keys when it is open, then the next group.
const rows = computed<Row[]>(() => {
  const out: Row[] = []
  const walk = (nodes: TreeNode[], depth: number) => {
    for (const node of nodes) {
      if (node.kind === 'leaf') {
        out.push({ kind: 'leaf', key: node.key, depth, value: node.value, count: 0, open: false })
        continue
      }
      const open = isOpen(node.label)
      out.push({ kind: 'group', key: node.label, depth, value: '', count: node.count, open })
      if (open) walk(node.children, depth + 1)
    }
  }
  walk(shown.value, 0)
  return out
})

const parts = (text: string) => splitMatch(text, props.query)
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-3"
    data-ingot-component="key-value-tree"
  >
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      <h2
        :id="headingId"
        class="md-title-large text-on-surface"
      >
        {{ title }}
      </h2>
      <MdBadge>{{ entries.length }} keys</MdBadge>
      <div class="ml-auto flex flex-wrap gap-1">
        <MdButton
          variant="text"
          @click="expandAll"
        >
          Expand all
        </MdButton>
        <MdButton
          variant="text"
          @click="collapseAll"
        >
          Collapse all
        </MdButton>
      </div>
    </div>
    <div
      role="table"
      :aria-label="label"
      class="grid min-w-0 grid-cols-1 medium:grid-cols-(--grid-md-key-value) gap-x-6 font-mono md-code-small"
    >
      <div
        v-for="row in rows"
        :key="`${row.kind}:${row.key}`"
        role="row"
        class="col-span-full grid min-w-0 grid-cols-1 medium:grid-cols-subgrid"
      >
        <div
          v-if="row.kind === 'group'"
          role="rowheader"
          class="col-span-full min-w-0 py-0.5"
          :class="depthClass(row.depth)"
        >
          <button
            type="button"
            class="md-state-layer md-focus-ring flex w-full min-w-0 cursor-pointer items-center gap-2 rounded-corner-small py-1 text-left md-title-small text-on-surface"
            :aria-expanded="row.open"
            @click="toggle(row.key, row.open)"
          >
            <MdIcon
              :path="CHEVRON"
              size="md"
              class="shrink-0 transition-transform duration-md-short3"
              :class="row.open ? 'rotate-180' : ''"
            />
            <span class="min-w-0 wrap-anywhere">
              <template
                v-for="(part, index) in parts(row.key)"
                :key="index"
              >
                <mark
                  v-if="part.hit"
                  :class="MARK_CLASS"
                >{{ part.text }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <MdBadge class="shrink-0">
              {{ row.count }} keys
            </MdBadge>
          </button>
        </div>
        <template v-else>
          <div
            role="rowheader"
            class="min-w-0 wrap-anywhere py-1 text-on-surface-variant"
            :class="depthClass(row.depth)"
          >
            <template
              v-for="(part, index) in parts(row.key)"
              :key="index"
            >
              <mark
                v-if="part.hit"
                :class="MARK_CLASS"
              >{{ part.text }}</mark>
              <template v-else>
                {{ part.text }}
              </template>
            </template>
          </div>
          <div
            role="cell"
            class="min-w-0 wrap-anywhere py-1"
            :class="[valueDepthClass(row.depth), row.value === '' ? 'italic text-on-surface-variant' : 'text-on-surface']"
          >
            <template v-if="row.value === ''">
              (empty)
            </template>
            <template v-else>
              <template
                v-for="(part, index) in parts(row.value)"
                :key="index"
              >
                <mark
                  v-if="part.hit"
                  :class="MARK_CLASS"
                >{{ part.text }}</mark>
                <template v-else>
                  {{ part.text }}
                </template>
              </template>
            </template>
          </div>
        </template>
      </div>
    </div>
    <p
      v-if="filtering && shown.length === 0"
      class="md-body-medium text-on-surface-variant"
    >
      No key matches
    </p>
  </div>
</template>
