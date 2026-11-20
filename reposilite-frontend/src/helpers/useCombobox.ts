/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The keyboard and selection logic of a combobox, the pattern of the WAI-ARIA combobox with a listbox popup. It has no DOM:
 * the reducer maps a state and a key to the next state, and the composable keeps that state for a component. The text of the
 * input belongs to the consumer; a choice is handed back as a value, and Escape never chooses.
 *
 * Keys: ArrowDown and ArrowUp open the list and move through it, and wrap at both ends. Home and End go to the first and the
 * last enabled option while the list is open. Enter chooses the active option. Escape closes the list. Disabled options are
 * skipped. There is no typeahead.
 */

import { computed, ref } from 'vue'

export interface ComboboxOption {
  value: string
  description?: string
  disabled?: boolean
  // The heading the option is listed under. Consecutive options with the same group form one group of the listbox.
  group?: string
}

export interface ComboboxState {
  open: boolean
  // The index of the active option, or -1 when none is active.
  active: number
}

export interface ComboboxStep {
  state: ComboboxState
  // Whether the key was consumed. A key that is not consumed belongs to the text field or to the form.
  handled: boolean
  chosen?: ComboboxOption
}

const CLOSED: ComboboxState = Object.freeze({ open: false, active: -1 })

const mod = (value: number, count: number): number => ((value % count) + count) % count

// The next enabled option from `from` in the direction of `step`, wrapping at both ends. From -1 the walk starts before the
// first option going down, and after the last one going up. Returns -1 when no option is enabled.
const stepEnabled = (options: ComboboxOption[], from: number, step: 1 | -1): number => {
  const count = options.length
  const start = from < 0 || from >= count ? (step > 0 ? -1 : count) : from
  for (let offset = 1; offset <= count; offset++) {
    const index = mod(start + step * offset, count)
    if (!options[index].disabled) return index
  }
  return -1
}

const firstEnabled = (options: ComboboxOption[]): number => stepEnabled(options, -1, 1)
const lastEnabled = (options: ComboboxOption[]): number => stepEnabled(options, -1, -1)

const moved = (state: ComboboxState, index: number): ComboboxStep =>
  index < 0 ? { state, handled: false } : { state: { open: true, active: index }, handled: true }

export const reduceCombobox = (state: ComboboxState, key: string, options: ComboboxOption[]): ComboboxStep => {
  switch (key) {
    case 'ArrowDown':
    case 'ArrowUp': {
      const step = key === 'ArrowDown' ? 1 : -1
      const from = state.open ? state.active : -1
      return moved(state, stepEnabled(options, from, step))
    }
    case 'Home':
      return state.open ? moved(state, firstEnabled(options)) : { state, handled: false }
    case 'End':
      return state.open ? moved(state, lastEnabled(options)) : { state, handled: false }
    case 'Enter': {
      const active = state.open && state.active >= 0 ? options[state.active] : undefined
      if (active && !active.disabled) return { state: CLOSED, handled: true, chosen: active }
      return { state: CLOSED, handled: false }
    }
    case 'Escape':
      return state.open ? { state: CLOSED, handled: true } : { state, handled: false }
    default:
      return { state, handled: false }
  }
}

// Keeps the options whose value contains the query, ignoring case. An empty query keeps every option.
export const filterOptions = (options: ComboboxOption[], query: string): ComboboxOption[] => {
  const needle = query.toLocaleLowerCase()
  return needle === '' ? options : options.filter((option) => option.value.toLocaleLowerCase().includes(needle))
}

// The DOM id of an option. The input's aria-activedescendant and the option's id are both built here.
export const optionId = (listboxId: string, index: number): string => `${listboxId}-option-${index}`

export interface UseComboboxOptions {
  // The id of the listbox. The options take their ids from it.
  id: string
  options: () => ComboboxOption[]
  onChoose?: (value: string) => void
}

// The state of one combobox for a component. The consumer binds `inputAttrs` and `onKeydown` to its input and `activeDescendant`
// and `visible` to the listbox.
export const useCombobox = (config: UseComboboxOptions) => {
  const open = ref(false)
  const active = ref(-1)
  const visible = computed(() => open.value && config.options().length > 0)
  const activeDescendant = computed(() => (visible.value && active.value >= 0 ? optionId(config.id, active.value) : undefined))

  const inputAttrs = computed(() => ({
    role: 'combobox',
    'aria-autocomplete': 'list',
    'aria-expanded': visible.value ? 'true' : 'false',
    'aria-controls': config.id,
    'aria-activedescendant': activeDescendant.value
  }))

  // Returns whether the key was consumed, so the caller can prevent its default action.
  const onKeydown = (key: string): boolean => {
    const step = reduceCombobox({ open: open.value, active: active.value }, key, config.options())
    open.value = step.state.open
    active.value = step.state.active
    if (step.chosen) config.onChoose?.(step.chosen.value)
    return step.handled
  }

  const onInput = (): void => {
    open.value = true
    active.value = -1
  }

  const onBlur = (): void => {
    open.value = false
    active.value = -1
  }

  // A disabled option is never chosen, so a click on it does nothing.
  const choose = (value: string): void => {
    const option = config.options().find((candidate) => candidate.value === value)
    if (!option || option.disabled) return
    open.value = false
    active.value = -1
    config.onChoose?.(option.value)
  }

  return { open, active, visible, activeDescendant, inputAttrs, onKeydown, onInput, onBlur, choose }
}
