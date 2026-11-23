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
import { computed, ref, useId, type PropType } from 'vue'
import MdButton from '../../md/MdButton.vue'
import MdListbox from '../../md/MdListbox.vue'
import MdTextField from '../../md/MdTextField.vue'
import { useCombobox, type ComboboxOption } from '../../../helpers/useCombobox'
import { parseNode } from './nodeGrammar'
import { groupSuggestions, hasNode, suggestWithArtifacts } from './nodeList'
import type { CatalogItem, NodeEntry } from './permissionNodesApi'

/*
 * The field that adds a node. It is a combobox: as the text changes, the catalog nodes that contain
 * it (and are not listed yet) appear in a listbox grouped as Static, Repository, Artifact and
 * Wildcard, each with its description. Artifact nodes are completed as they are typed: a repository
 * continues with '.artifact.', and a groupId or artifactId with the actions read, deploy, delete and
 * '*'. A continuation fills the field and adds nothing. The keys are those of useCombobox: ArrowDown
 * and ArrowUp move through the options, Home and End jump to the first and the last one, Enter on an
 * option picks it, and Escape closes the list. Free text is accepted when it is a valid node: the
 * grammar is checked live and an invalid text shows its error under the field and cannot be added.
 * A node that is listed already is not added twice: Add or Enter moves to its row instead.
 *
 * The suggestions are a plain filter over the catalog the parent loaded once, so typing needs no
 * request and no timer.
 */
const props = defineProps({
  /** The nodes already listed (the draft). */
  nodes: {
    type: Array as PropType<NodeEntry[]>,
    default: () => []
  },
  /** [{ node, description, kind }] */
  catalog: {
    type: Array as PropType<CatalogItem[]>,
    default: () => []
  }
})

const emit = defineEmits(['add', 'reveal'])

const inputId = `node-add-${useId()}`
const listboxId = `${inputId}-listbox`

const text = ref('')

const trimmed = computed(() => text.value.trim())
// The suggestions in the order they are shown: group by group, so the keys walk the list as it is drawn.
const suggestions = computed(() =>
  groupSuggestions(suggestWithArtifacts(props.catalog, text.value, props.nodes))
    .flatMap(group => group.options.map(item => ({ ...item, group: group.label }))))
const options = computed<ComboboxOption[]>(() =>
  suggestions.value.map(item => ({ value: item.node, description: item.description, group: item.group })))
// A node that is listed already is not added twice: Add (or Enter) moves to its row instead.
const isListed = computed(() => trimmed.value !== '' && hasNode(props.nodes, trimmed.value))

const errorText = computed(() => {
  if (trimmed.value === '' || isListed.value) return ''
  const parsed = parseNode(trimmed.value)
  return parsed.valid ? '' : parsed.problems[0].message
})

const canAdd = computed(() => trimmed.value !== '' && (isListed.value || errorText.value === ''))

const reset = () => {
  text.value = ''
}

const add = (node: string) => {
  emit('add', node)
  reset()
}

const submit = () => {
  if (isListed.value) {
    emit('reveal', trimmed.value)
    reset()
  } else if (canAdd.value) {
    add(trimmed.value)
  }
}

const { open, active, visible, inputAttrs, onKeydown, onInput, onBlur, choose } = useCombobox({
  id: listboxId,
  options: () => options.value,
  // A continuation is not a node yet: it fills the field and keeps the list open for the next part.
  onChoose: (node) => {
    const item = suggestions.value.find(candidate => candidate.node === node)
    if (item?.continuation) {
      text.value = item.node
      open.value = true
      active.value = -1
      return
    }
    add(node)
  }
})

const onType = (value: string) => {
  text.value = value
  onInput()
}

// Enter always stays in the field: it picks the active option, or else adds (or reveals) the typed node.
const onKeydownEvent = (event: KeyboardEvent) => {
  if (onKeydown(event.key)) {
    event.preventDefault()
  } else if (event.key === 'Enter') {
    event.preventDefault()
    submit()
  }
}

const onFocus = () => {
  open.value = true
}
</script>

<template>
  <div
    class="flex flex-col gap-2 medium:flex-row medium:items-start"
    data-ingot-component="node-add-field"
  >
    <div class="relative min-w-0 flex-1">
      <MdTextField
        :id="inputId"
        v-bind="inputAttrs"
        :model-value="text"
        label="Add a node"
        variant="outlined"
        autocomplete="off"
        spellcheck="false"
        :error="errorText !== ''"
        :error-text="errorText"
        supporting-text="Type a node or choose one from the list"
        @update:model-value="onType"
        @keydown="onKeydownEvent"
        @focus="onFocus"
        @blur="onBlur"
      />
      <MdListbox
        :id="listboxId"
        class="absolute inset-x-0 top-14"
        label="Node suggestions"
        :options="options"
        :active="active"
        :open="visible"
        mono
        @choose="choose"
      />
    </div>
    <MdButton
      variant="tonal"
      class="shrink-0 self-start medium:mt-2"
      :disabled="!canAdd"
      data-test="add-node"
      @click="submit"
    >
      Add
    </MdButton>
  </div>
</template>
