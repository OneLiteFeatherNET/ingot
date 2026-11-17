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

import { describe, it, expect, vi } from 'vitest'
import { reduceCombobox, filterOptions, optionId, useCombobox, type ComboboxOption, type ComboboxState } from './useCombobox'

// Pure: the reducer and the filter take plain input and return plain output, and the composable runs without a mount.

const CLOSED: ComboboxState = { open: false, active: -1 }
const OPEN_AT = (active: number): ComboboxState => ({ open: true, active })

const plain: ComboboxOption[] = [{ value: 'alpha' }, { value: 'beta' }, { value: 'gamma' }]
const withDisabled: ComboboxOption[] = [{ value: 'alpha' }, { value: 'beta', disabled: true }, { value: 'gamma' }]

describe('reduceCombobox arrow keys', () => {
  it('opens the list on the first option when ArrowDown is pressed on a closed list', () => {
    const step = reduceCombobox(CLOSED, 'ArrowDown', plain)

    expect(step.state, 'the list opens on the first option').toEqual({ open: true, active: 0 })
    expect(step.handled, 'the key is consumed').toBe(true)
  })

  it('opens the list on the last option when ArrowUp is pressed on a closed list', () => {
    const step = reduceCombobox(CLOSED, 'ArrowUp', plain)

    expect(step.state, 'the list opens on the last option').toEqual({ open: true, active: 2 })
  })

  it('moves the active option down by one', () => {
    expect(reduceCombobox(OPEN_AT(0), 'ArrowDown', plain).state.active, 'ArrowDown moves to the next option').toBe(1)
  })

  it('wraps from the last option to the first on ArrowDown', () => {
    expect(reduceCombobox(OPEN_AT(2), 'ArrowDown', plain).state.active, 'ArrowDown wraps to the first option').toBe(0)
  })

  it('wraps from the first option to the last on ArrowUp', () => {
    expect(reduceCombobox(OPEN_AT(0), 'ArrowUp', plain).state.active, 'ArrowUp wraps to the last option').toBe(2)
  })

  it('skips disabled options on ArrowDown', () => {
    expect(reduceCombobox(OPEN_AT(0), 'ArrowDown', withDisabled).state.active, 'the disabled beta is passed over').toBe(2)
  })

  it('skips disabled options on ArrowUp', () => {
    expect(reduceCombobox(OPEN_AT(2), 'ArrowUp', withDisabled).state.active, 'the disabled beta is passed over').toBe(0)
  })

  it('is not handled when every option is disabled, so the caret keeps the key', () => {
    const allDisabled: ComboboxOption[] = [{ value: 'a', disabled: true }]

    expect(reduceCombobox(CLOSED, 'ArrowDown', allDisabled).handled, 'there is nothing to move to').toBe(false)
  })

  it('is not handled when the list is empty', () => {
    expect(reduceCombobox(CLOSED, 'ArrowDown', []).handled, 'an empty list has no option to move to').toBe(false)
  })
})

describe('reduceCombobox Home and End', () => {
  it('moves to the first enabled option on Home while the list is open', () => {
    expect(reduceCombobox(OPEN_AT(2), 'Home', withDisabled).state.active, 'Home skips the disabled option').toBe(0)
  })

  it('moves to the last enabled option on End while the list is open', () => {
    expect(reduceCombobox(OPEN_AT(0), 'End', withDisabled).state.active, 'End is the last enabled option').toBe(2)
  })

  it('is not handled while the list is closed, so the caret moves in the text', () => {
    expect(reduceCombobox(CLOSED, 'Home', plain).handled, 'Home belongs to the text field when closed').toBe(false)
    expect(reduceCombobox(CLOSED, 'End', plain).handled, 'End belongs to the text field when closed').toBe(false)
  })
})

describe('reduceCombobox Enter and Escape', () => {
  it('chooses the active option on Enter and closes the list', () => {
    const step = reduceCombobox(OPEN_AT(1), 'Enter', plain)

    expect(step.chosen, 'the active option is chosen').toEqual({ value: 'beta' })
    expect(step.state, 'the list closes after a choice').toEqual(CLOSED)
    expect(step.handled, 'Enter is consumed when it chooses').toBe(true)
  })

  it('lets Enter reach the form when no option is active, and closes the list', () => {
    const step = reduceCombobox(OPEN_AT(-1), 'Enter', plain)

    expect(step.chosen, 'nothing is chosen without an active option').toBeUndefined()
    expect(step.handled, 'the form keeps Enter').toBe(false)
    expect(step.state.open, 'the list still closes').toBe(false)
  })

  it('closes the list and clears the active option on Escape', () => {
    const step = reduceCombobox(OPEN_AT(1), 'Escape', plain)

    expect(step.state, 'Escape closes the list and clears the index').toEqual(CLOSED)
    expect(step.chosen, 'Escape never chooses, so the typed text stays as it is').toBeUndefined()
  })

  it('is not handled on Escape while the list is closed, so the surrounding dialog can close', () => {
    expect(reduceCombobox(CLOSED, 'Escape', plain).handled, 'a closed list leaves Escape alone').toBe(false)
  })

  it('ignores keys it does not own', () => {
    expect(reduceCombobox(OPEN_AT(0), 'a', plain), 'a letter is not a combobox key').toEqual({ state: OPEN_AT(0), handled: false })
  })
})

describe('filterOptions', () => {
  const options: ComboboxOption[] = [{ value: 'Alpha' }, { value: 'beta-team' }, { value: 'gamma' }]

  it('keeps every option for an empty query', () => {
    expect(filterOptions(options, ''), 'an empty query filters nothing').toEqual(options)
  })

  it('keeps the options whose value contains the query, ignoring case', () => {
    expect(filterOptions(options, 'AM'), 'the match is a case-insensitive substring').toEqual([{ value: 'beta-team' }, { value: 'gamma' }])
  })
})

describe('optionId', () => {
  it('builds the id of an option from the base id and its index', () => {
    expect(optionId('access-combo', 3), 'the id is what aria-activedescendant points at').toBe('access-combo-option-3')
  })
})

describe('useCombobox', () => {
  it('points aria-activedescendant at the active option', () => {
    const combobox = useCombobox({ id: 'nodes', options: () => plain })

    combobox.onKeydown('ArrowDown')
    combobox.onKeydown('ArrowDown')

    expect(combobox.activeDescendant.value, 'the second option is the active descendant').toBe('nodes-option-1')
  })

  it('has no active descendant while the list is closed', () => {
    const combobox = useCombobox({ id: 'nodes', options: () => plain })

    expect(combobox.activeDescendant.value, 'a closed list has no active option').toBeUndefined()
  })

  it('reports aria-expanded as false when there are no options to show', () => {
    const combobox = useCombobox({ id: 'nodes', options: () => [] })

    combobox.onInput()

    expect(combobox.inputAttrs.value['aria-expanded'], 'an empty list is not expanded').toBe('false')
  })

  it('tells the choice to the caller with the chosen value', () => {
    const onChoose = vi.fn()
    const combobox = useCombobox({ id: 'nodes', options: () => plain, onChoose })

    combobox.onKeydown('ArrowDown')
    combobox.onKeydown('Enter')

    expect(onChoose, 'the caller receives the value of the active option').toHaveBeenCalledWith('alpha')
  })

  it('opens the list and clears the active option when the text changes', () => {
    const combobox = useCombobox({ id: 'nodes', options: () => plain })

    combobox.onKeydown('ArrowDown')
    combobox.onInput()

    expect(combobox.open.value, 'typing opens the list').toBe(true)
    expect(combobox.active.value, 'typing clears the active option').toBe(-1)
  })

  it('does not choose a disabled option when it is picked directly', () => {
    const onChoose = vi.fn()
    const combobox = useCombobox({ id: 'nodes', options: () => withDisabled, onChoose })

    combobox.choose('beta')

    expect(onChoose, 'a disabled option cannot be chosen').not.toHaveBeenCalled()
  })
})
