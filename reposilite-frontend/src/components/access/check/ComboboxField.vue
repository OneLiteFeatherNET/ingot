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
import MdTextField from '../../md/MdTextField.vue'

/*
 * A text field with a listbox of suggestions, the pattern of the node editor's add field. The text
 * is free: a suggestion only fills it in. The parent decides which options fit the text.
 *
 * Keys: ArrowDown and ArrowUp open the list and move through it, Enter takes the active option (and
 * otherwise leaves the key to the form, which submits), Escape closes the list.
 *
 * Props:
 *   modelValue    the text
 *   options       [{ value, description }] to suggest; the parent has already filtered them
 *   mono          shows the option values in the monospace face (nodes)
 *   listboxLabel  the accessible name of the list
 * Emits update:modelValue for typing and for a chosen option.
 */
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    required: true
  },
  options: {
    type: Array,
    default: () => []
  },
  supportingText: {
    type: String,
    default: ''
  },
  error: {
    type: Boolean,
    default: false
  },
  errorText: {
    type: String,
    default: ''
  },
  mono: {
    type: Boolean,
    default: false
  },
  listboxLabel: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const inputId = `combobox-${useId()}`
const listboxId = `${inputId}-listbox`
const optionId = (index) => `${inputId}-option-${index}`

const open = ref(false)
const active = ref(-1)

const showList = computed(() => open.value && props.options.length > 0)
const activeDescendant = computed(() => (showList.value && active.value >= 0 ? optionId(active.value) : undefined))

const choose = (value) => {
  emit('update:modelValue', value)
  open.value = false
  active.value = -1
}

const onInput = (value) => {
  emit('update:modelValue', value)
  open.value = true
  active.value = -1
}

const move = (step) => {
  const count = props.options.length
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
    if (showList.value && active.value >= 0) {
      event.preventDefault()
      choose(props.options[active.value].value)
    } else {
      open.value = false
    }
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
  <div class="relative min-w-0">
    <MdTextField
      :id="inputId"
      :model-value="modelValue"
      :label="label"
      variant="outlined"
      autocomplete="off"
      spellcheck="false"
      :error="error"
      :error-text="errorText"
      :supporting-text="supportingText"
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
      :aria-label="listboxLabel"
      class="absolute inset-x-0 top-14 z-20 max-h-72 overflow-y-auto rounded-corner-extra-small bg-surface-container shadow-elevation-2"
      @mousedown.prevent
    >
      <li
        v-for="(option, index) in options"
        :id="optionId(index)"
        :key="option.value"
        role="option"
        :aria-selected="index === active ? 'true' : 'false'"
        class="md-state-layer relative flex cursor-pointer flex-col px-4 py-2"
        :class="index === active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
        data-test="option"
        @click="choose(option.value)"
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
  </div>
</template>
