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

/*
 * An input chip field: a list of values, each shown as a chip with a close button, and a text
 * field at the end that turns what is typed into a new chip. Enter, or a comma, adds the text.
 * A comma list typed or pasted at once adds every piece. Backspace in an empty field removes the
 * last chip. Each new value goes through `validate(text, existing)`, which returns the problem to
 * show or an empty string; a value with a problem stays in the field so it can be fixed.
 *
 * `suggestions` are offered under the field while it has focus: the values that are not chips yet
 * and that contain the text typed after the last comma. Pressing one adds it. The field knows
 * nothing about tags; the renderer that uses it supplies the rules and the suggestions.
 */
const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  label: {
    type: String,
    required: true
  },
  id: {
    type: String,
    default: ''
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
  suggestions: {
    type: Array,
    default: () => []
  },
  validate: {
    type: Function,
    default: () => ''
  },
  // The limit shown in the counter. Zero shows no counter.
  maxItems: {
    type: Number,
    default: 0
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const SUGGESTION_LIMIT = 8

const generatedId = useId()
const inputId = computed(() => props.id || `md-chip-input-${generatedId}`)
const helperId = computed(() => `${inputId.value}-helper`)

const input = ref(null)
const draft = ref('')
const draftError = ref('')
const focused = ref(false)

const problem = computed(() => draftError.value || (props.error ? props.errorText : ''))
const helperText = computed(() => problem.value || props.supportingText)
const invalid = computed(() => problem.value !== '')

/* The text typed after the last comma, which is the one the suggestions match. */
const needle = computed(() => draft.value.split(',').pop().trim().toLowerCase())

const visibleSuggestions = computed(() => {
  if (!focused.value) return []
  return props.suggestions
    .filter(suggestion => !props.modelValue.includes(suggestion) && suggestion.toLowerCase().includes(needle.value))
    .slice(0, SUGGESTION_LIMIT)
})

/*
 * Checks each piece in turn against the chips and the pieces already accepted. Stops at the first
 * problem: the accepted pieces are kept, and the failing piece and everything after it are returned
 * as the rest.
 */
const addPieces = (pieces) => {
  const accepted = []
  for (let index = 0; index < pieces.length; index++) {
    const text = pieces[index].trim()
    if (text === '') continue
    const message = props.validate(text, [...props.modelValue, ...accepted])
    if (message) return { accepted, problem: message, rest: pieces.slice(index) }
    accepted.push(text)
  }
  return { accepted, problem: '', rest: [] }
}

/* Adds what can be added, and leaves the rest in the field with its problem, or clears the field. */
const commit = (pieces, tail = null) => {
  const result = addPieces(pieces)
  if (result.accepted.length > 0) {
    emit('update:modelValue', [...props.modelValue, ...result.accepted])
  }
  if (result.problem) {
    draft.value = [...result.rest, ...(tail === null ? [] : [tail])].join(',')
    draftError.value = result.problem
  } else {
    draft.value = tail ?? ''
    draftError.value = ''
  }
}

const onInput = () => {
  if (!draft.value.includes(',')) return
  const pieces = draft.value.split(',')
  const tail = pieces.pop()
  commit(pieces, tail)
}

const onKeydown = (event) => {
  if (event.key === 'Enter') {
    event.preventDefault()
    commit(draft.value.split(','))
  } else if (event.key === 'Backspace' && draft.value === '' && props.modelValue.length > 0) {
    emit('update:modelValue', props.modelValue.slice(0, -1))
  }
}

const remove = (index) => {
  emit('update:modelValue', props.modelValue.filter((_, position) => position !== index))
  input.value?.focus()
}

const addSuggestion = (suggestion) => {
  const message = props.validate(suggestion, props.modelValue)
  if (message) {
    draftError.value = message
    return
  }
  emit('update:modelValue', [...props.modelValue, suggestion])
  draftError.value = ''
}

// The classes are spelled out in full so that Tailwind finds them in this file.
const FIELD = {
  default: 'border-outline focus-within:border-primary focus-within:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)]',
  error: 'border-error shadow-[inset_0_0_0_1px_var(--md-sys-color-error)]',
  disabled: 'border-on-surface/12'
}
const fieldClass = computed(() => {
  if (props.disabled) return FIELD.disabled
  return invalid.value ? FIELD.error : FIELD.default
})
const helperClass = computed(() => (invalid.value ? 'text-error' : 'text-on-surface-variant'))
</script>

<template>
  <div class="flex min-w-0 w-full max-w-xl flex-col gap-1">
    <label
      :for="inputId"
      class="px-1 md-label-large text-on-surface-variant"
      :class="disabled ? 'text-on-surface/38' : ''"
    >
      {{ label }}
    </label>
    <div
      class="flex min-h-14 flex-wrap items-center gap-2 rounded-corner-extra-small border bg-transparent px-2 py-2"
      :class="fieldClass"
      @click="input?.focus()"
    >
      <ul
        class="m-0 flex list-none flex-wrap items-center gap-2 p-0"
        :aria-label="`${label} chips`"
      >
        <li
          v-for="(tag, index) in modelValue"
          :key="`${index}:${tag}`"
          data-test="chip"
          class="inline-flex h-8 items-center gap-1 rounded-corner-small border pl-3 pr-1 md-label-large"
          :class="disabled ? 'border-on-surface/12 text-on-surface/38' : 'border-outline bg-surface-container-low text-on-surface'"
        >
          <span class="[overflow-wrap:anywhere]">{{ tag }}</span>
          <button
            type="button"
            class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-6 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant"
            :aria-label="`Remove ${tag}`"
            :disabled="disabled"
            @click.stop="remove(index)"
          >
            <svg
              viewBox="0 0 24 24"
              class="size-[18px]"
              fill="currentColor"
              aria-hidden="true"
              focusable="false"
            >
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        </li>
      </ul>
      <input
        :id="inputId"
        ref="input"
        v-model="draft"
        type="text"
        autocomplete="off"
        class="min-w-[8rem] flex-1 bg-transparent px-2 md-body-large text-on-surface outline-none disabled:text-on-surface/38"
        :disabled="disabled"
        :aria-invalid="invalid ? 'true' : undefined"
        :aria-describedby="helperText ? helperId : undefined"
        @input="onInput"
        @keydown="onKeydown"
        @focus="focused = true"
        @blur="focused = false"
      >
    </div>
    <ul
      v-if="visibleSuggestions.length > 0"
      class="m-0 flex list-none flex-wrap gap-2 p-0 pt-1"
      :aria-label="`Suggested ${label.toLowerCase()}`"
    >
      <li
        v-for="suggestion in visibleSuggestions"
        :key="suggestion"
      >
        <button
          type="button"
          data-test="suggestion"
          class="md-state-layer md-focus-ring md-touch-target relative inline-flex h-8 items-center rounded-corner-small border border-outline bg-surface-container-low px-3 md-label-large text-on-surface"
          @mousedown.prevent
          @click="addSuggestion(suggestion)"
        >
          {{ suggestion }}
        </button>
      </li>
    </ul>
    <div class="flex items-start justify-between gap-2 px-1">
      <p
        :id="helperId"
        class="md-body-small"
        :class="helperClass"
      >
        {{ helperText }}
      </p>
      <p
        v-if="maxItems > 0"
        class="shrink-0 md-body-small text-on-surface-variant"
      >
        {{ modelValue.length }} / {{ maxItems }}
      </p>
    </div>
  </div>
</template>
