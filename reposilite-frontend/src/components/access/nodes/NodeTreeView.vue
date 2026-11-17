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
import { reactive, type PropType } from 'vue'
import MdIcon from '../../md/MdIcon.vue'
import NodeRow from './NodeRow.vue'
import { containsNode, isWildcard, leafCount, stateBelow, type TreeItem } from './nodeTree'
import type { NodeEntry } from './permissionNodesApi'

/*
 * The nodes as a tree grouped by dot segment (see nodeTree.ts). A chain with a single child is one
 * row, so a level only appears where the nodes branch. A group is a native details element whose
 * summary shows its path, the number of nodes below it and whether they all allow, all deny or mix.
 * A leaf is one row with its full node, the same row as the list view. Groups start open unless
 * `expandAll` is off (more than 20 nodes and no filter); a group that holds `revealNode` is always
 * open, so the filter and an added node show their path.
 */
const props = defineProps({
  items: {
    type: Array as PropType<TreeItem<NodeEntry>[]>,
    required: true
  },
  readonly: {
    type: Boolean,
    default: false
  },
  rowErrors: {
    type: Object as PropType<Record<string, string>>,
    default: () => ({})
  },
  expandAll: {
    type: Boolean,
    default: true
  },
  /** The node whose path is open, and the node that was just added or found again. */
  revealNode: {
    type: String,
    default: ''
  },
  highlighted: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['toggle', 'remove'])

const isOpen = (item: TreeItem<NodeEntry>): boolean =>
  props.expandAll || (props.revealNode !== '' && containsNode(item, props.revealNode))

// The state of each group as the browser reports it, so that the chevron turns with the group.
const shown = reactive<Record<string, boolean>>({})
const isShown = (item: TreeItem<NodeEntry>): boolean => shown[item.key] ?? isOpen(item)
const onGroupToggle = (key: string, event: Event) => {
  shown[key] = (event.target as HTMLDetailsElement).open
}

const CHEVRON = 'M9 6l6 6-6 6'
const STATE_LABEL = { allow: 'All allow', deny: 'All deny', mixed: 'Mixed' }
const STATE_CLASS = { allow: 'text-on-surface', deny: 'text-error', mixed: 'text-on-surface-variant' }
</script>

<template>
  <ul
    class="flex flex-col"
    data-ingot-component="node-tree-view"
  >
    <li
      v-for="item in items"
      :key="item.key"
      :data-test="item.entry ? 'tree-leaf' : 'tree-group'"
      :data-key="item.key"
    >
      <template v-if="item.entry">
        <NodeRow
          :entry="item.entry"
          :readonly="readonly"
          :error="rowErrors[item.entry.node] ?? ''"
          :wildcard-hint="isWildcard(item.entry.node)"
          :highlighted="highlighted === item.entry.node"
          @toggle="(allowed) => emit('toggle', item.entry?.node, allowed)"
          @remove="emit('remove', item.entry?.node)"
        />
        <NodeTreeView
          v-if="item.children.length > 0"
          :items="item.children"
          :readonly="readonly"
          :row-errors="rowErrors"
          :expand-all="expandAll"
          :reveal-node="revealNode"
          :highlighted="highlighted"
          class="pl-4"
          @toggle="(node, allowed) => emit('toggle', node, allowed)"
          @remove="(node) => emit('remove', node)"
        />
      </template>
      <details
        v-else
        :open="isOpen(item)"
        @toggle="(event) => onGroupToggle(item.key, event)"
      >
        <summary
          class="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 bg-surface-container-low px-4 py-2"
          data-test="tree-summary"
        >
          <MdIcon
            :path="CHEVRON"
            size="sm"
            class="shrink-0 text-on-surface-variant transition-transform motion-reduce:transition-none"
            :class="isShown(item) ? 'rotate-90' : ''"
          />
          <code class="break-all font-mono md-label-large text-on-surface">{{ item.key }}</code>
          <span class="md-label-medium text-on-surface-variant">{{ leafCount(item) }} nodes</span>
          <span
            class="md-label-medium"
            :class="STATE_CLASS[stateBelow(item)]"
            data-test="tree-state"
          >{{ STATE_LABEL[stateBelow(item)] }}</span>
        </summary>
        <NodeTreeView
          :items="item.children"
          :readonly="readonly"
          :row-errors="rowErrors"
          :expand-all="expandAll"
          :reveal-node="revealNode"
          :highlighted="highlighted"
          class="pl-4"
          @toggle="(node, allowed) => emit('toggle', node, allowed)"
          @remove="(node) => emit('remove', node)"
        />
      </details>
    </li>
  </ul>
</template>
