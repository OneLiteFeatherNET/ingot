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
import NodeRow from './NodeRow.vue'

/*
 * The nodes as a tree grouped by dot segment (see nodeTree.js). A group shows its segment; a leaf
 * is a row that shows its last segment and the allow state, with the same controls as the list.
 * The component renders itself for the children of an item.
 */
defineProps({
  items: {
    type: Array,
    required: true
  },
  readonly: {
    type: Boolean,
    default: false
  },
  rowErrors: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['toggle', 'remove'])
</script>

<template>
  <ul class="flex flex-col">
    <li
      v-for="item in items"
      :key="item.key"
      :data-test="item.entry ? 'tree-leaf' : 'tree-group'"
      :data-key="item.key"
    >
      <NodeRow
        v-if="item.entry"
        :entry="item.entry"
        :label="item.segment"
        :readonly="readonly"
        :error="rowErrors[item.entry.node] ?? ''"
        @toggle="(allowed) => emit('toggle', item.entry.node, allowed)"
        @remove="emit('remove', item.entry.node)"
      />
      <p
        v-else
        class="px-4 py-2 font-mono md-label-large text-on-surface-variant"
      >
        {{ item.segment }}
      </p>
      <NodeTreeView
        v-if="item.children.length > 0"
        :items="item.children"
        :readonly="readonly"
        :row-errors="rowErrors"
        class="ml-4 border-l border-outline-variant"
        @toggle="(node, allowed) => emit('toggle', node, allowed)"
        @remove="(node) => emit('remove', node)"
      />
    </li>
  </ul>
</template>
