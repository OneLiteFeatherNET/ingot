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
import { computed, nextTick, onBeforeUnmount, ref, useAttrs, useId, watch } from 'vue'

/*
 * Material Design 3 outlined select. The field is a select-only combobox, the WAI-ARIA pattern:
 * it keeps the focus, and the options open in a listbox that is drawn in the body, so a dialog
 * around the field cannot clip it. The menu is an MD3 exposed menu in the surface-container role.
 * It opens below the field, or above it when there is no room below, and it scrolls when neither
 * side has room for every option.
 *
 * Keyboard, as on a native select: ArrowDown and ArrowUp move through the options, Home and End
 * jump to the ends, Enter and Space choose the active option, Escape closes without choosing, Tab
 * closes, and typed letters find the next option that starts with them. A closed field takes a
 * typed letter as the value, the way a native select does. Pointer and touch choose by click.
 *
 * A native <select> stays in the DOM, visually hidden and out of the tab order. It holds the value
 * for forms, and code that reads or changes the select by its id or name still works. The id and
 * the attributes that are not props go to it. The label names the combobox, not the native select.
 *
 * The label always floats on the top edge, because a select always shows a value or an empty box.
 * Supporting text sits under the field, and error text replaces it, as in MdTextField.
 *
 * Attributes that are not props go to the native select. Class and style stay on the wrapper.
 */
defineOptions({ inheritAttrs: false })

const attrs = useAttrs()
const forwardedAttrs = () =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'))

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    required: true
  },
  /* Each option is { value, label }. The value is what modelValue holds. */
  options: {
    type: Array,
    required: true
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
  id: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  required: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const generatedId = useId()
const baseId = computed(() => props.id || `md-select-${generatedId}`)
const comboboxId = computed(() => `${baseId.value}-combobox`)
const labelId = computed(() => `${baseId.value}-label`)
const listboxId = computed(() => `${baseId.value}-listbox`)
const optionId = (index) => `${baseId.value}-option-${index}`
const helperId = computed(() => `${baseId.value}-helper`)
const helperText = computed(() => (props.error ? props.errorText : props.supportingText))
const describedBy = computed(() => (helperText.value ? helperId.value : undefined))

// Menu geometry in px. A row is 48px, the touch target MD3 asks for. The menu keeps a margin from
// the window edge and sits a gap below or above the field.
const ROW_HEIGHT = 48
const MENU_PADDING = 8
const MENU_MAX_HEIGHT = 320
const MENU_GAP = 4
const WINDOW_MARGIN = 8
// A pause this long ends a type-ahead search, so the next letter starts a new one.
const TYPEAHEAD_PAUSE = 500

const comboboxRef = ref(null)
const listboxRef = ref(null)
const isOpen = ref(false)
const active = ref(-1)
const menuStyle = ref({})
let typed = ''
let typedTimer = null

const selectedIndex = computed(() => props.options.findIndex((option) => option.value === props.modelValue))
const selectedLabel = computed(() => (selectedIndex.value >= 0 ? props.options[selectedIndex.value].label : ''))
const activeDescendant = computed(() => (isOpen.value && active.value >= 0 ? optionId(active.value) : undefined))

// Every class name is spelled out in full so that Tailwind finds it in this file. The default
// field has a 1px outline and a 1px inset shadow in primary when focused. The error field is
// drawn in the error role, focused or not, and the disabled field in on-surface at 12% and 38%.
const FIELD_CLASS = {
  default: 'border-outline text-on-surface focus:border-primary focus:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)]',
  error: 'border-error text-on-surface shadow-[inset_0_0_0_1px_var(--md-sys-color-error)]',
  disabled: 'border-on-surface/12 text-on-surface/38'
}

const fieldClass = computed(() => {
  if (props.disabled) return FIELD_CLASS.disabled
  return props.error ? FIELD_CLASS.error : FIELD_CLASS.default
})

const labelClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return props.error ? 'text-error' : 'text-on-surface-variant peer-focus:text-primary'
})

const iconClass = computed(() => (props.disabled ? 'text-on-surface/38' : 'text-on-surface-variant'))

const helperClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return props.error ? 'text-error' : 'text-on-surface-variant'
})

// The menu is placed from the field's box on the screen. It is read once, when the menu opens, and
// again on scroll and resize while it is open.
function place() {
  const anchor = comboboxRef.value
  if (!anchor) return
  const rect = anchor.getBoundingClientRect()
  const rows = props.options.length * ROW_HEIGHT + MENU_PADDING * 2
  const natural = Math.min(MENU_MAX_HEIGHT, rows)
  const spaceBelow = window.innerHeight - rect.bottom - WINDOW_MARGIN
  const spaceAbove = rect.top - WINDOW_MARGIN
  const below = natural <= spaceBelow || spaceBelow >= spaceAbove
  const room = below ? spaceBelow : spaceAbove
  const left = Math.max(WINDOW_MARGIN, Math.min(rect.left, window.innerWidth - rect.width - WINDOW_MARGIN))
  const style = {
    left: `${left}px`,
    width: `${rect.width}px`,
    maxHeight: `${Math.max(0, Math.min(natural, room))}px`
  }
  if (below) {
    style.top = `${rect.bottom + MENU_GAP}px`
  } else {
    style.bottom = `${window.innerHeight - rect.top + MENU_GAP}px`
  }
  menuStyle.value = style
}

function scrollActiveIntoView() {
  nextTick(() => listboxRef.value?.children[active.value]?.scrollIntoView?.({ block: 'nearest' }))
}

function openMenu() {
  if (props.disabled || props.options.length === 0 || isOpen.value) return
  active.value = selectedIndex.value >= 0 ? selectedIndex.value : 0
  place()
  isOpen.value = true
  window.addEventListener('resize', place)
  window.addEventListener('scroll', place, true)
  scrollActiveIntoView()
}

function closeMenu() {
  if (!isOpen.value) return
  isOpen.value = false
  active.value = -1
  window.removeEventListener('resize', place)
  window.removeEventListener('scroll', place, true)
}

// Choosing the value that is already set emits nothing, as a native select does.
function chooseIndex(index) {
  const option = props.options[index]
  if (!option) return
  if (option.value !== props.modelValue) emit('update:modelValue', option.value)
  closeMenu()
}

function moveTo(index) {
  const count = props.options.length
  if (count === 0) return
  active.value = Math.min(Math.max(index, 0), count - 1)
  scrollActiveIntoView()
}

function findMatch(text, start) {
  const count = props.options.length
  for (let step = 0; step < count; step += 1) {
    const index = (start + step) % count
    if (props.options[index].label.toLowerCase().startsWith(text)) return index
  }
  return -1
}

function typeAhead(letter) {
  clearTimeout(typedTimer)
  typed += letter.toLowerCase()
  typedTimer = setTimeout(() => {
    typed = ''
  }, TYPEAHEAD_PAUSE)
  const from = isOpen.value ? active.value : Math.max(selectedIndex.value, 0)
  // One letter typed again moves on to the next option with that letter, as a native select does.
  const repeated = typed.length > 1 && [...typed].every((char) => char === typed[0])
  const index = findMatch(typed, repeated ? from + 1 : from)
  if (index < 0) return
  if (isOpen.value) {
    moveTo(index)
  } else {
    chooseIndex(index)
  }
}

const SINGLE_CHARACTER = /^\S$/

function onKeydown(event) {
  if (props.disabled) return
  const key = event.key
  const plain = !event.ctrlKey && !event.metaKey && !event.altKey

  if (!isOpen.value) {
    if (key === 'ArrowDown' || key === 'ArrowUp' || key === 'Enter' || key === ' ') {
      event.preventDefault()
      openMenu()
    } else if (key === 'Home' || key === 'End') {
      event.preventDefault()
      chooseIndex(key === 'Home' ? 0 : props.options.length - 1)
    } else if (SINGLE_CHARACTER.test(key) && plain) {
      typeAhead(key)
    }
    return
  }

  switch (key) {
    case 'ArrowDown':
      event.preventDefault()
      moveTo(active.value + 1)
      break
    case 'ArrowUp':
      event.preventDefault()
      moveTo(active.value - 1)
      break
    case 'Home':
      event.preventDefault()
      moveTo(0)
      break
    case 'End':
      event.preventDefault()
      moveTo(props.options.length - 1)
      break
    case 'Enter':
    case ' ':
      event.preventDefault()
      chooseIndex(active.value)
      break
    case 'Escape':
      // Escape closes the menu only. Without stopPropagation a dialog around the field would close too.
      event.preventDefault()
      event.stopPropagation()
      closeMenu()
      break
    case 'Tab':
      closeMenu()
      break
    default:
      if (SINGLE_CHARACTER.test(key) && plain) typeAhead(key)
  }
}

function onClick() {
  if (props.disabled) return
  if (isOpen.value) {
    closeMenu()
  } else {
    openMenu()
  }
}

watch(() => props.disabled, (disabled) => {
  if (disabled) closeMenu()
})

onBeforeUnmount(() => {
  clearTimeout(typedTimer)
  closeMenu()
})
</script>

<template>
  <div
    class="flex w-full flex-col gap-1"
    :class="attrs.class"
    :style="attrs.style"
  >
    <div class="relative">
      <!--
        The native select is the form control: its value, its name and the change event that
        forms and tests use. It is hidden from the page and from the keyboard. Its appearance is
        reset, so no browser chevron can ever be drawn for it.
      -->
      <select
        v-bind="forwardedAttrs()"
        :id="baseId"
        tabindex="-1"
        aria-hidden="true"
        class="sr-only appearance-none bg-none"
        :value="modelValue"
        :disabled="disabled"
        @change="emit('update:modelValue', $event.target.value)"
      >
        <option
          v-for="option in options"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </select>
      <div
        :id="comboboxId"
        ref="comboboxRef"
        role="combobox"
        aria-haspopup="listbox"
        :aria-expanded="isOpen ? 'true' : 'false'"
        :aria-controls="isOpen ? listboxId : undefined"
        :aria-activedescendant="activeDescendant"
        :aria-labelledby="labelId"
        :aria-describedby="describedBy"
        :aria-invalid="error ? 'true' : undefined"
        :aria-required="required ? 'true' : undefined"
        :aria-disabled="disabled ? 'true' : undefined"
        :tabindex="disabled ? -1 : 0"
        class="peer relative flex h-14 w-full cursor-pointer select-none items-center rounded-corner-extra-small border bg-transparent pl-4 pr-12 md-body-large outline-none"
        :class="fieldClass"
        @click="onClick"
        @keydown="onKeydown"
        @blur="closeMenu"
      >
        <span class="min-w-0 truncate">{{ selectedLabel }}</span>
      </div>
      <label
        :id="labelId"
        class="pointer-events-none absolute left-3 top-0 -translate-y-1/2 bg-[var(--md-field-container,var(--md-sys-color-surface))] px-1 md-label-medium"
        :class="labelClass"
      >{{ label }}</label>
      <span
        class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 [&>svg]:size-6"
        :class="iconClass"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path d="M7 10l5 5 5-5z" />
        </svg>
      </span>
    </div>
    <p
      v-if="helperText"
      :id="helperId"
      class="md-body-small whitespace-pre-line px-4"
      :class="helperClass"
    >
      {{ helperText }}
    </p>
    <Teleport to="body">
      <ul
        v-if="isOpen"
        :id="listboxId"
        ref="listboxRef"
        role="listbox"
        :aria-labelledby="labelId"
        class="fixed overflow-y-auto rounded-corner-extra-small bg-surface-container py-2 shadow-elevation-2"
        :style="{ ...menuStyle, zIndex: 'calc(var(--md-sys-z-dialog) + 1)' }"
        @mousedown.prevent
      >
        <li
          v-for="(option, index) in options"
          :id="optionId(index)"
          :key="option.value"
          role="option"
          :aria-selected="index === selectedIndex ? 'true' : 'false'"
          class="md-state-layer relative flex min-h-12 cursor-pointer items-center gap-3 px-4 md-label-large"
          :class="index === selectedIndex ? 'text-primary' : 'text-on-surface'"
          :style="index === active ? { '--md-state-opacity': 0.12 } : undefined"
          @click="chooseIndex(index)"
        >
          <span class="flex size-6 shrink-0 items-center justify-center">
            <svg
              v-if="index === selectedIndex"
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
              class="size-6"
            >
              <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" />
            </svg>
          </span>
          <span class="min-w-0 truncate">{{ option.label }}</span>
        </li>
      </ul>
    </Teleport>
  </div>
</template>
