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
import { computed, nextTick, ref, type PropType } from 'vue'
import MdCode from '../../md/MdCode.vue'
import MdList from '../../md/MdList.vue'
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
import type { InheritedEntry } from './inheritedNodes'
import type { CatalogItem, NodeEntry, NodeTemplate } from './permissionNodesApi'

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
    type: Array as PropType<NodeEntry[]>,
    default: () => []
  },
  savedNodes: {
    type: Array as PropType<NodeEntry[] | null>,
    default: null
  },
  /** Read only nodes from teams: [{ node, allowed, source, subject, depth }] */
  inherited: {
    type: Array as PropType<InheritedEntry[]>,
    default: () => []
  },
  /** One line for each team whose nodes could not be read. */
  inheritedWarnings: {
    type: Array as PropType<string[]>,
    default: () => []
  },
  /** Messages the server gave for rows, by node text. */
  rowErrors: {
    type: Object as PropType<Record<string, string>>,
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
    type: Array as PropType<CatalogItem[]>,
    default: () => []
  },
  /** [{ id, label, nodes }] */
  templates: {
    type: Array as PropType<NodeTemplate[]>,
    default: () => []
  },
  conflict: {
    type: Object as PropType<{ kind: 'nodes'; name: string } | null>,
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

const view = ref<'list' | 'tree'>('list')
const query = ref('')
const state = ref<'all' | 'allow' | 'deny'>('all')
const reviewing = ref(false)
const region = ref<HTMLElement | null>(null)
// The node that was just added or found again is highlighted, and the groups on its path are opened.
const highlighted = ref('')
const revealed = ref('')

const matchesState = (entry: NodeEntry): boolean => state.value === 'all' || (state.value === 'allow') === entry.allowed
const filtering = computed(() => query.value.trim() !== '' || state.value !== 'all')
const rows = computed(() => filterRows(props.nodes, query.value).filter(matchesState))
const tree = computed(() => buildTree(rows.value))
// Above 20 nodes the groups of the tree start collapsed. A filter opens the path to every match.
const expandAll = computed(() => filtering.value || props.nodes.length <= 20)
const allowedCount = computed(() => props.nodes.filter(entry => entry.allowed).length)
const summary = computed(() => {
  const total = props.nodes.length
  const text = `${total} ${total === 1 ? 'node' : 'nodes'}: ${allowedCount.value} allow, ${total - allowedCount.value} deny`
  return filtering.value ? `${text}, showing ${rows.value.length} of ${total}` : text
})
const changes = computed(() => diffNodes(props.savedNodes ?? props.nodes, props.nodes))
const showBar = computed(() => !props.readonly && (changes.value.count > 0 || props.saving))

const update = (next: NodeEntry[]) => emit('update:nodes', next)

const prefersReducedMotion = (): boolean =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

// Brings the row of a node into view, marks it, and focuses it when asked (a node listed already).
const reveal = async (node: string, focus: boolean) => {
  revealed.value = node
  highlighted.value = node
  await nextTick()
  const target = [...(region.value?.querySelectorAll<HTMLElement>('[data-node]') ?? [])]
    .find(element => element.dataset.node === node)
  if (!target) return
  target.scrollIntoView({ block: 'nearest', behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  if (focus) target.focus()
}

const add = (node: string) => {
  const entry = { node, allowed: true }
  // A node the filter hides would not show up, so the filter is cleared for it.
  if (!(filterRows([entry], query.value).length > 0 && matchesState(entry))) {
    query.value = ''
    state.value = 'all'
  }
  update([...props.nodes, entry])
  reveal(node, false)
}
const addMany = (added: NodeEntry[]) => update([...props.nodes, ...added])
const remove = (node: string) => update(props.nodes.filter(row => row.node !== node))
const toggle = (node: string, allowed: boolean) =>
  update(props.nodes.map(row => (row.node === node ? { ...row, allowed } : row)))

const discard = () => {
  reviewing.value = false
  emit('discard')
}

const stateWord = (allowed: boolean): string => (allowed ? 'Allow' : 'Deny')
</script>

<template>
  <section
    class="flex flex-col gap-4"
    data-test="node-editor"
    data-ingot-component="node-editor"
  >
    <p
      v-if="loading"
      data-ingot-state="loading"
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
          @reveal="(node) => reveal(node, true)"
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
        <div class="flex flex-col gap-3 sm:flex-row sm:items-start">
          <div
            v-if="!readonly"
            class="w-full sm:w-56"
          >
            <SegmentedControl label="Filter by state">
              <SegmentedOption
                :on="state === 'all'"
                data-test="state-all"
                @click="state = 'all'"
              >
                All
              </SegmentedOption>
              <SegmentedOption
                :on="state === 'allow'"
                data-test="state-allow"
                @click="state = 'allow'"
              >
                Allow
              </SegmentedOption>
              <SegmentedOption
                :on="state === 'deny'"
                data-test="state-deny"
                @click="state = 'deny'"
              >
                Deny
              </SegmentedOption>
            </SegmentedControl>
          </div>
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
      </div>

      <p
        v-if="nodes.length > 0"
        class="md-body-small text-on-surface-variant"
        data-test="node-summary"
      >
        {{ summary }}
      </p>

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
      <div
        v-else
        ref="region"
        role="region"
        aria-label="Permission nodes"
        tabindex="0"
        class="max-h-md-node-region overflow-y-auto rounded-corner-medium border border-outline-variant"
        data-test="node-region"
      >
        <MdList
          v-if="view === 'list'"
          divided
          aria-label="Permission nodes"
          data-test="node-list"
        >
          <NodeRow
            v-for="row in rows"
            :key="row.node"
            :entry="row"
            :readonly="readonly"
            :error="rowErrors[row.node] ?? ''"
            :highlighted="highlighted === row.node"
            data-test="node-row"
            @toggle="(allowed) => toggle(row.node, allowed)"
            @remove="remove(row.node)"
          />
        </MdList>
        <NodeTreeView
          v-else
          :items="tree"
          :readonly="readonly"
          :row-errors="rowErrors"
          :expand-all="expandAll"
          :reveal-node="revealed"
          :highlighted="highlighted"
          aria-label="Permission nodes as a tree"
          data-test="node-tree"
          @toggle="toggle"
          @remove="remove"
        />
      </div>

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
          Add <MdCode>{{ entry.node }}</MdCode> as {{ stateWord(entry.allowed) }}
        </li>
        <li
          v-for="entry in changes.removed"
          :key="`removed-${entry.node}`"
        >
          Remove <MdCode>{{ entry.node }}</MdCode>
        </li>
        <li
          v-for="entry in changes.changed"
          :key="`changed-${entry.node}`"
        >
          Change <MdCode>{{ entry.node }}</MdCode> from {{ stateWord(entry.from) }} to {{ stateWord(entry.to) }}
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
      :conflict="conflict ?? undefined"
      @reload="emit('conflict-reload')"
      @overwrite="emit('conflict-overwrite')"
      @dismiss="emit('conflict-dismiss')"
    />
  </section>
</template>
