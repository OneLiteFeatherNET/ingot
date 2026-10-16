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
import { computed, ref, useId } from 'vue'
import MdButton from '../../md/MdButton.vue'
import MdTextField from '../../md/MdTextField.vue'
import { parseNode } from './nodeGrammar'
import { groupSuggestions, hasNode, suggestWithArtifacts } from './nodeList'

/*
 * The field that adds a node. It is a combobox: as the text changes, the catalog nodes that contain
 * it (and are not listed yet) appear in a listbox grouped as Static, Repository, Artifact and
 * Wildcard, each with its description. Artifact nodes are completed as they are typed: a repository
 * continues with '.artifact.', and a groupId or artifactId with the actions read, deploy, delete and
 * '*'. A continuation fills the field and adds nothing. ArrowDown and ArrowUp move through the
 * options, Enter on an option picks it, and Escape closes the list. Free text is accepted when it is
 * a valid node: the grammar is checked live and an invalid or duplicate text shows its error under
 * the field and cannot be added.
 *
 * The suggestions are a plain filter over the catalog the parent loaded once, so typing needs no
 * request and no timer.
 */
const props = defineProps({
  /** The nodes already listed (the draft). */
  nodes: {
    type: Array,
    default: () => []
  },
  /** [{ node, description, kind }] */
  catalog: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['add'])

const inputId = `node-add-${useId()}`
const listboxId = `${inputId}-listbox`
const optionId = (index) => `${inputId}-option-${index}`

const text = ref('')
const open = ref(false)
const active = ref(-1)

const trimmed = computed(() => text.value.trim())
const suggestions = computed(() => suggestWithArtifacts(props.catalog, text.value, props.nodes))
const groups = computed(() => groupSuggestions(suggestions.value))
const showList = computed(() => open.value && suggestions.value.length > 0)

const errorText = computed(() => {
  if (trimmed.value === '') return ''
  if (hasNode(props.nodes, trimmed.value)) return 'Already listed'
  const parsed = parseNode(trimmed.value)
  return parsed.valid ? '' : parsed.problems[0].message
})

const canAdd = computed(() => trimmed.value !== '' && errorText.value === '')
const activeDescendant = computed(() => (showList.value && active.value >= 0 ? optionId(active.value) : undefined))

const reset = () => {
  text.value = ''
  active.value = -1
  open.value = false
}

const add = (node) => {
  emit('add', node)
  reset()
}

// A continuation is not a node yet: it fills the field and keeps the list open for the next part.
const pick = (item) => {
  if (item.continuation) {
    text.value = item.node
    active.value = -1
    open.value = true
    return
  }
  add(item.node)
}

const submit = () => {
  if (canAdd.value) add(trimmed.value)
}

const onInput = (value) => {
  text.value = value
  open.value = true
  active.value = -1
}

const move = (step) => {
  const count = suggestions.value.length
  if (count === 0) return
  if (!open.value) {
    open.value = true
    active.value = step > 0 ? 0 : count - 1
    return
  }
  active.value = (active.value + step + count) % count
}

const onKeydown = (event) => {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    if (showList.value && active.value >= 0) pick(suggestions.value[active.value])
    else submit()
  } else if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    open.value = false
    active.value = -1
  }
}

const onFocus = () => {
  open.value = true
}

const onBlur = () => {
  open.value = false
  active.value = -1
}
</script>

<template>
  <div class="flex flex-col gap-2 sm:flex-row sm:items-start">
    <div class="relative min-w-0 flex-1">
      <MdTextField
        :id="inputId"
        :model-value="text"
        label="Add a node"
        variant="outlined"
        autocomplete="off"
        spellcheck="false"
        :error="errorText !== ''"
        :error-text="errorText"
        supporting-text="Type a node or choose one from the list"
        role="combobox"
        aria-autocomplete="list"
        :aria-expanded="showList ? 'true' : 'false'"
        :aria-controls="listboxId"
        :aria-activedescendant="activeDescendant"
        @update:model-value="onInput"
        @keydown="onKeydown"
        @focus="onFocus"
        @blur="onBlur"
      />
      <ul
        v-show="showList"
        :id="listboxId"
        role="listbox"
        aria-label="Node suggestions"
        class="absolute inset-x-0 top-14 z-20 max-h-72 overflow-y-auto rounded-corner-extra-small bg-surface-container shadow-elevation-2"
        @mousedown.prevent
      >
        <li
          v-for="group in groups"
          :key="group.kind"
          role="presentation"
        >
          <span
            :id="`${inputId}-group-${group.kind}`"
            class="block px-4 pb-1 pt-3 md-label-medium text-on-surface-variant"
            data-test="suggestion-heading"
          >{{ group.label }}</span>
          <ul
            role="group"
            :aria-labelledby="`${inputId}-group-${group.kind}`"
          >
            <li
              v-for="item in group.options"
              :id="optionId(suggestions.indexOf(item))"
              :key="item.node"
              role="option"
              :aria-selected="suggestions.indexOf(item) === active ? 'true' : 'false'"
              class="md-state-layer relative flex cursor-pointer flex-col px-4 py-2"
              :class="suggestions.indexOf(item) === active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
              data-test="suggestion"
              @click="pick(item)"
            >
              <code class="break-all font-mono md-body-medium">{{ item.node }}</code>
              <span
                v-if="item.description"
                class="md-body-small text-on-surface-variant"
              >{{ item.description }}</span>
            </li>
          </ul>
        </li>
      </ul>
    </div>
    <MdButton
      variant="tonal"
      class="shrink-0 self-start sm:mt-2"
      :disabled="!canAdd"
      data-test="add-node"
      @click="submit"
    >
      Add
    </MdButton>
  </div>
</template>
