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
import { computed, reactive, h, type FunctionalComponent, type VNode } from 'vue'
import MdButton from '../md/MdButton.vue'
import { buildKeyTree, filterTree, groupLabels, OPEN_GROUPS_UP_TO, splitMatch, type KeySplit, type TreeNode } from './keyTree'

// A list of keys and values, grouped by prefix into native disclosures. The values are shown exactly as they were given.
// The filter is owned by the page: a query opens the groups on the path to each match and marks the matching text.
const props = withDefaults(defineProps<{
  entries: [string, string][]
  split: KeySplit
  label: string
  query?: string
}>(), { query: '' })

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

// A toggle the user made differs from what the tree shows. The toggles that the tree makes itself match it and are skipped.
const onToggle = (label: string, event: Event) => {
  const open = (event.target as HTMLDetailsElement).open
  if (open !== isOpen(label)) userOpen.set(label, open)
}

const expandAll = () => groupLabels(tree.value).forEach((label) => userOpen.set(label, true))
const collapseAll = () => groupLabels(tree.value).forEach((label) => userOpen.set(label, false))

const marked = (text: string): (string | VNode)[] => splitMatch(text, props.query).map((part) =>
  part.hit ? h('mark', { class: 'rounded-corner-small bg-primary-container text-on-primary-container' }, part.text) : part.text,
)

const renderNode = (node: TreeNode): VNode => {
  if (node.kind === 'leaf') {
    return h('dl', { class: 'grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1 py-1' }, [
      h('dt', { class: 'min-w-0 wrap-anywhere text-on-surface' }, marked(node.key)),
      h('dd', { class: 'min-w-0 wrap-anywhere text-on-surface-variant' }, marked(node.value)),
    ])
  }
  return h('details', {
    class: 'min-w-0 border-l border-outline pl-3',
    open: isOpen(node.label),
    onToggle: (event: Event) => onToggle(node.label, event),
  }, [
    h('summary', { class: 'cursor-pointer py-1 wrap-anywhere text-on-surface' }, [
      ...marked(node.label),
      h('span', { class: 'ml-2 text-on-surface-variant' }, `${node.count} keys`),
    ]),
    h(Nodes, { nodes: node.children }),
  ])
}

// The nodes of one level. It calls itself for the children of a group.
const Nodes: FunctionalComponent<{ nodes: TreeNode[] }> = (nodeProps) => nodeProps.nodes.map(renderNode)
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-2"
    data-ingot-component="key-value-tree"
  >
    <div class="flex flex-wrap gap-2">
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
    <div
      role="region"
      :aria-label="label"
      tabindex="0"
      class="max-h-(--container-md-key-tree) min-w-0 overflow-y-auto font-mono md-code-small"
    >
      <Nodes :nodes="shown" />
    </div>
    <p
      v-if="filtering && shown.length === 0"
      class="md-body-medium text-on-surface-variant"
    >
      No key matches
    </p>
  </div>
</template>
