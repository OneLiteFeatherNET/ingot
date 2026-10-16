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
import { computed, ref } from 'vue'
import MdTextField from '../../md/MdTextField.vue'
import ConflictDialog from '../../settings/ConflictDialog.vue'
import UnsavedChangesBar from '../../settings/UnsavedChangesBar.vue'
import SegmentedControl from '../../tokens/SegmentedControl.vue'
import SegmentedOption from '../../tokens/SegmentedOption.vue'
import NodeAddField from './NodeAddField.vue'
import NodeInheritedSection from './NodeInheritedSection.vue'
import NodeRow from './NodeRow.vue'
import NodeTemplateMenu from './NodeTemplateMenu.vue'
import NodeTreeView from './NodeTreeView.vue'
import { diffNodes, filterRows } from './nodeList'
import { buildTree } from './nodeTree'

/*
 * The editor of the permission nodes of one subject. It does no requests: the host (the principal
 * or team page, with usePermissionNodes) hands in the lists and takes the changes back.
 *
 * `nodes` is the draft, `savedNodes` is the stored list the unsaved bar counts against. Every edit
 * (add, remove, flip Allow/Deny, apply a template) emits update:nodes with the whole new draft, so
 * the host uses v-model:nodes. Save and discard are emitted from the unsaved bar. The three
 * conflict-* events pass on the choices of the conflict dialog, which this component shows while
 * `conflict` is set.
 *
 * A read only editor lists the nodes with their state as text and has no add, toggle, remove or
 * bar.
 */
const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  },
  savedNodes: {
    type: Array,
    default: null
  },
  /** Read only nodes from teams: [{ node, allowed, source, subject, depth }] */
  inherited: {
    type: Array,
    default: () => []
  },
  /** One line for each team whose nodes could not be read. */
  inheritedWarnings: {
    type: Array,
    default: () => []
  },
  /** Messages the server gave for rows, by node text. */
  rowErrors: {
    type: Object,
    default: () => ({})
  },
  /** A message that belongs to no row. */
  formError: {
    type: String,
    default: ''
  },
  readonly: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  saving: {
    type: Boolean,
    default: false
  },
  /** [{ node, description, kind }] for the suggestions. */
  catalog: {
    type: Array,
    default: () => []
  },
  /** [{ id, label, nodes }] */
  templates: {
    type: Array,
    default: () => []
  },
  conflict: {
    type: Object,
    default: null
  }
})

const emit = defineEmits([
  'update:nodes',
  'save',
  'discard',
  'conflict-reload',
  'conflict-overwrite',
  'conflict-dismiss'
])

const view = ref('list')
const query = ref('')
const reviewing = ref(false)

const rows = computed(() => filterRows(props.nodes, query.value))
const tree = computed(() => buildTree(rows.value))
const changes = computed(() => diffNodes(props.savedNodes ?? props.nodes, props.nodes))
const showBar = computed(() => !props.readonly && (changes.value.count > 0 || props.saving))

const update = (next) => emit('update:nodes', next)

const add = (node) => update([...props.nodes, { node, allowed: true }])
const addMany = (added) => update([...props.nodes, ...added])
const remove = (node) => update(props.nodes.filter(row => row.node !== node))
const toggle = (node, allowed) =>
  update(props.nodes.map(row => (row.node === node ? { ...row, allowed } : row)))

const discard = () => {
  reviewing.value = false
  emit('discard')
}

const stateWord = (allowed) => (allowed ? 'Allow' : 'Deny')
</script>

<template>
  <section
    class="flex flex-col gap-4"
    data-test="node-editor"
  >
    <p
      v-if="loading"
      role="status"
      class="md-body-large text-on-surface-variant"
      data-test="nodes-loading"
    >
      Loading permission nodes
    </p>

    <template v-else>
      <template v-if="!readonly">
        <NodeAddField
          :nodes="nodes"
          :catalog="catalog"
          @add="add"
        />
        <NodeTemplateMenu
          :templates="templates"
          :catalog="catalog"
          :nodes="nodes"
          @apply="addMany"
        />
      </template>

      <p
        v-if="formError"
        role="alert"
        class="whitespace-pre-line md-body-medium text-error"
        data-test="form-error"
      >
        {{ formError }}
      </p>

      <div
        v-if="nodes.length > 0"
        class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"
      >
        <MdTextField
          v-model="query"
          label="Filter nodes"
          variant="outlined"
          type="search"
          autocomplete="off"
          spellcheck="false"
          class="sm:max-w-sm"
        />
        <div class="w-full sm:w-48">
          <SegmentedControl label="View">
            <SegmentedOption
              :on="view === 'list'"
              data-test="view-list"
              @click="view = 'list'"
            >
              List
            </SegmentedOption>
            <SegmentedOption
              :on="view === 'tree'"
              data-test="view-tree"
              @click="view = 'tree'"
            >
              Tree
            </SegmentedOption>
          </SegmentedControl>
        </div>
      </div>

      <div
        v-if="nodes.length === 0"
        class="flex flex-col gap-1 rounded-corner-medium bg-surface-container-low p-6"
        data-test="nodes-empty"
      >
        <p class="md-title-medium text-on-surface">
          No permission nodes
        </p>
        <p class="md-body-medium text-on-surface-variant">
          {{ readonly ? 'Nothing is granted or denied directly.' : 'Add a node above or start from a template.' }}
        </p>
      </div>
      <p
        v-else-if="rows.length === 0"
        class="md-body-medium text-on-surface-variant"
        data-test="nodes-no-match"
      >
        No node matches "{{ query }}".
      </p>
      <ul
        v-else-if="view === 'list'"
        class="flex flex-col divide-y divide-outline-variant rounded-corner-medium border border-outline-variant"
        aria-label="Permission nodes"
        data-test="node-list"
      >
        <li
          v-for="row in rows"
          :key="row.node"
          data-test="node-row"
        >
          <NodeRow
            :entry="row"
            :readonly="readonly"
            :error="rowErrors[row.node] ?? ''"
            @toggle="(allowed) => toggle(row.node, allowed)"
            @remove="remove(row.node)"
          />
        </li>
      </ul>
      <NodeTreeView
        v-else
        :items="tree"
        :readonly="readonly"
        :row-errors="rowErrors"
        class="rounded-corner-medium border border-outline-variant"
        aria-label="Permission nodes as a tree"
        data-test="node-tree"
        @toggle="toggle"
        @remove="remove"
      />

      <NodeInheritedSection
        v-if="inherited.length > 0 || inheritedWarnings.length > 0"
        :inherited="inherited"
        :direct="nodes"
        :warnings="inheritedWarnings"
      />
    </template>

    <div
      v-if="showBar"
      class="sticky bottom-0 z-10 flex flex-col"
      data-test="unsaved-bar"
    >
      <ul
        v-if="reviewing"
        class="flex flex-col gap-1 border-t border-outline-variant bg-surface-container px-4 py-3 md-body-medium text-on-surface"
        data-test="change-summary"
      >
        <li
          v-for="entry in changes.added"
          :key="`added-${entry.node}`"
        >
          Add <code class="font-mono">{{ entry.node }}</code> as {{ stateWord(entry.allowed) }}
        </li>
        <li
          v-for="entry in changes.removed"
          :key="`removed-${entry.node}`"
        >
          Remove <code class="font-mono">{{ entry.node }}</code>
        </li>
        <li
          v-for="entry in changes.changed"
          :key="`changed-${entry.node}`"
        >
          Change <code class="font-mono">{{ entry.node }}</code> from {{ stateWord(entry.from) }} to {{ stateWord(entry.to) }}
        </li>
      </ul>
      <UnsavedChangesBar
        :count="changes.count"
        :saving="saving"
        @save="emit('save')"
        @discard="discard"
        @review="reviewing = !reviewing"
      />
    </div>

    <ConflictDialog
      :conflict="conflict"
      @reload="emit('conflict-reload')"
      @overwrite="emit('conflict-overwrite')"
      @dismiss="emit('conflict-dismiss')"
    />
  </section>
</template>
