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
import { useId, type PropType } from 'vue'
import MdListbox from '../../md/MdListbox.vue'
import MdTextField from '../../md/MdTextField.vue'
import { useCombobox, type ComboboxOption } from '../../../helpers/useCombobox'

/*
 * A text field with a listbox of suggestions, the pattern of the check view's principal, node, repository and path fields.
 * The text is free: a suggestion only fills it in. The parent decides which options fit the text.
 *
 * Keys: ArrowDown and ArrowUp open the list and move through it, Enter takes the active option (and otherwise leaves the
 * key to the form), Escape closes the list. The keys and the state are those of useCombobox; the list is MdListbox.
 *
 * Props:
 *   modelValue    the text
 *   options       [{ value, description }] to suggest; the parent has already filtered them
 *   mono          shows the option values in the monospace face
 *   disabled      turns the field off and hides its list
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
    type: Array as PropType<ComboboxOption[]>,
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
  disabled: {
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

const { open, active, visible, inputAttrs, onKeydown, onInput, onBlur, choose } = useCombobox({
  id: listboxId,
  options: () => props.options,
  onChoose: value => emit('update:modelValue', value)
})

const onFocus = () => {
  open.value = true
}

const onType = (value: string) => {
  emit('update:modelValue', value)
  onInput()
}

const onKeydownEvent = (event: KeyboardEvent) => {
  if (onKeydown(event.key)) event.preventDefault()
}
</script>

<template>
  <div
    class="relative min-w-0"
    data-ingot-component="combobox-field"
  >
    <MdTextField
      :id="inputId"
      v-bind="inputAttrs"
      :model-value="modelValue"
      :label="label"
      variant="outlined"
      autocomplete="off"
      spellcheck="false"
      :error="error"
      :error-text="errorText"
      :supporting-text="supportingText"
      :disabled="disabled"
      @update:model-value="onType"
      @keydown="onKeydownEvent"
      @focus="onFocus"
      @blur="onBlur"
    />
    <MdListbox
      :id="listboxId"
      class="absolute inset-x-0 top-14"
      :label="listboxLabel"
      :options="options"
      :active="active"
      :open="visible && !disabled"
      :mono="mono"
      @choose="choose"
    />
  </div>
</template>
