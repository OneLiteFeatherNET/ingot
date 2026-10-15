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

<script>
import { and, formatIs, isStringControl, or, rankWith } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed, ref } from 'vue'
import MdTextField from '../md/MdTextField.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdButton from '../md/MdButton.vue'
import { descriptionText } from './description.js'
import { COLOUR_PROPERTIES, isColour } from '../../theme/settings'

const COLOUR_HELP = 'Use a colour in the form #RRGGBB, such as #006a6a.'

/* A string named after one of the colour settings, or one that declares the colour format. */
const namesColourSetting = (uischema) => COLOUR_PROPERTIES.includes(uischema?.scope?.split('/').pop())

/*
 * A colour field: an outlined text field with a swatch of the colour in front, a native colour
 * picker behind an icon button, and a Clear action. The text is the value, so a colour can always be
 * typed; the picker writes #rrggbb, and an empty field means not set. The rank is above the text
 * field, which would otherwise take these strings.
 */
export const tester = rankWith(4, and(isStringControl, or(formatIs('color'), namesColourSetting)))

export default {
  name: 'MdColourFieldRenderer',
  components: {
    MdButton,
    MdIconButton,
    MdTextField
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const path = computed(() => input.control.value.path)
    const picker = ref(null)

    const value = computed(() => {
      const data = input.control.value.data
      return data === undefined || data === null ? '' : String(data)
    })
    const hasValue = computed(() => value.value !== '')
    const valid = computed(() => isColour(value.value))

    // The native picker needs #rrggbb, and it shows black while the field is empty or invalid.
    const pickerValue = computed(() => (hasValue.value && valid.value ? value.value.toLowerCase() : '#000000'))
    const swatch = computed(() => (hasValue.value && valid.value ? value.value : 'transparent'))
    const errorText = computed(() => input.control.value.errors || (valid.value ? '' : COLOUR_HELP))

    // An emptied field removes the value, so the property is left out of the saved settings. Anything
    // else is kept as typed, even when it is not a colour: the domain is then invalid and cannot be saved.
    const onInput = (raw) => {
      input.handleChange(path.value, raw === '' ? undefined : raw)
    }
    const onPicked = (event) => {
      input.handleChange(path.value, event.target.value)
    }
    const clear = () => {
      input.handleChange(path.value, undefined)
    }

    // The picker is hidden behind the icon button, so the button opens it. Browsers that cannot
    // show the picker programmatically fall back to a click on the input itself.
    const openPicker = () => {
      const element = picker.value
      if (!element) return
      try {
        element.showPicker()
      } catch {
        element.click()
      }
    }

    return {
      ...input,
      clear,
      descriptionText,
      errorText,
      hasValue,
      onInput,
      onPicked,
      openPicker,
      picker,
      pickerValue,
      swatch,
      value,
      valid
    }
  }
}
</script>

<template>
  <div
    v-if="control.visible"
    class="flex w-full flex-col gap-1"
  >
    <MdTextField
      :id="`${control.id}-input`"
      :model-value="value"
      :label="control.label"
      variant="outlined"
      autocomplete="off"
      spellcheck="false"
      :supporting-text="descriptionText(control.description)"
      :error="!valid || Boolean(control.errors)"
      :error-text="errorText"
      :disabled="!control.enabled"
      :required="control.required"
      @update:model-value="onInput"
    >
      <template #leading-icon>
        <span
          class="block size-6 rounded-corner-extra-small border border-outline"
          :class="{ 'border-dashed': !hasValue || !valid }"
          :style="{ backgroundColor: swatch }"
          :data-colour="swatch"
          data-test="colour-swatch"
          aria-hidden="true"
        />
      </template>
      <template #trailing-icon>
        <span class="relative inline-flex">
          <MdIconButton
            :aria-label="`Choose a colour for ${control.label}`"
            :disabled="!control.enabled"
            @click="openPicker"
          >
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M12 3a9 9 0 0 0 0 18c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
            </svg>
          </MdIconButton>
          <input
            :id="`${control.id}-picker`"
            ref="picker"
            type="color"
            class="pointer-events-none absolute inset-0 size-full cursor-pointer opacity-0"
            tabindex="-1"
            aria-hidden="true"
            :value="pickerValue"
            :disabled="!control.enabled"
            @input="onPicked"
          >
        </span>
      </template>
    </MdTextField>
    <div class="flex justify-end">
      <MdButton
        variant="text"
        :disabled="!control.enabled || !hasValue"
        @click="clear"
      >
        Clear
      </MdButton>
    </div>
  </div>
</template>
