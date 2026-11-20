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
import { computed, type PropType } from 'vue'
import { optionId, type ComboboxOption } from '../../helpers/useCombobox'
import MdTransition from './MdTransition.vue'

/*
 * The popup list of a combobox. The input, its text and its keys belong to the consumer, which runs them through useCombobox
 * and binds the input's aria-activedescendant to the id of the active option (optionId). The list is shown with v-show, so
 * its id stays in the DOM for aria-controls while it is hidden. A click on an option does not take focus from the input.
 * It opens and closes through the menu pattern of MdTransition and scales from its top edge, next to the input above it.
 *
 * Props:
 *   id       the id of the list; the options take their ids from it
 *   label    the accessible name of the list
 *   options  [{ value, description, disabled, group }]; the parent has already filtered them. Consecutive options that
 *            name the same group are listed in one group (role="group") under a heading that names it; the option ids
 *            and the active index still count through the whole list
 *   active   the index of the active option, or -1
 *   open     whether the list is shown
 *   mono     shows the option values in the monospace face (nodes, paths)
 * Emits choose(value) for a click on an enabled option.
 */
const props = defineProps({
  id: {
    type: String,
    required: true
  },
  label: {
    type: String,
    required: true
  },
  options: {
    type: Array as PropType<ComboboxOption[]>,
    default: () => []
  },
  active: {
    type: Number,
    default: -1
  },
  open: {
    type: Boolean,
    default: true
  },
  mono: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits<{ choose: [value: string] }>()

interface IndexedOption {
  option: ComboboxOption
  index: number
}

// Runs of consecutive options with the same group. A list without groups is one run with no heading, and its wrappers are
// presentational, so the options stay the direct items of the listbox in the accessibility tree.
const sections = computed(() => {
  const runs: { group?: string; entries: IndexedOption[] }[] = []
  props.options.forEach((option, index) => {
    const last = runs[runs.length - 1]
    if (last && last.group === option.group) last.entries.push({ option, index })
    else runs.push({ group: option.group, entries: [{ option, index }] })
  })
  return runs
})

const grouped = computed(() => props.options.some((option) => option.group !== undefined))
const groupId = (run: number): string => `${props.id}-group-${run}`
</script>

<template>
  <MdTransition pattern="menu">
    <ul
      v-show="open && options.length > 0"
      :id="id"
      role="listbox"
      :aria-label="label"
      class="z-md-menu max-h-72 origin-top overflow-y-auto rounded-corner-extra-small bg-surface-container shadow-elevation-2"
      data-ingot-component="md-listbox"
      @mousedown.prevent
    >
      <li
        v-for="({ group, entries }, run) in sections"
        :key="group ?? run"
        role="presentation"
      >
        <span
          v-if="grouped"
          :id="groupId(run)"
          class="block px-4 pb-1 pt-3 md-label-medium text-on-surface-variant"
          data-test="option-group"
        >{{ group }}</span>
        <ul
          :role="grouped ? 'group' : 'presentation'"
          :aria-labelledby="grouped ? groupId(run) : undefined"
        >
          <li
            v-for="{ option, index } in entries"
            :id="optionId(id, index)"
            :key="option.value"
            role="option"
            :aria-selected="index === active ? 'true' : 'false'"
            :aria-disabled="option.disabled ? 'true' : undefined"
            class="md-state-layer relative flex flex-col px-4 py-2"
            :class="[
              option.disabled ? 'cursor-default text-on-surface/38' : 'cursor-pointer',
              index === active ? 'bg-secondary-container text-on-secondary-container' : option.disabled ? '' : 'text-on-surface'
            ]"
            data-test="option"
            @click="!option.disabled && emit('choose', option.value)"
          >
            <span
              class="break-all md-body-medium"
              :class="mono ? 'font-mono' : ''"
            >{{ option.value }}</span>
            <span
              v-if="option.description"
              class="md-body-small text-on-surface-variant"
            >{{ option.description }}</span>
          </li>
        </ul>
      </li>
    </ul>
  </MdTransition>
</template>
