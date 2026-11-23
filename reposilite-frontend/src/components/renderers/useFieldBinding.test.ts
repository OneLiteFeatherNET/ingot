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
import { ref } from 'vue'
import { useFieldBinding } from './useFieldBinding'
import type { FieldControl } from './useFieldBinding'

const controlOf = (overrides: Partial<FieldControl> = {}) => ref<FieldControl>({
  id: '#/properties/name',
  path: 'name',
  label: 'Name',
  description: '',
  errors: '',
  enabled: true,
  required: false,
  data: undefined,
  schema: { type: 'string' },
  ...overrides
})

const bind = (overrides: Partial<FieldControl> = {}) => {
  const handleChange = vi.fn()
  const binding = useFieldBinding({ control: controlOf(overrides), handleChange })
  return { binding, handleChange }
}

describe('useFieldBinding props', () => {
  it('maps the control to the props of a field primitive', () => {
    const { binding } = bind({ description: 'First<br>second', errors: 'Too short', required: true })

    expect(binding.fieldProps.value).toEqual({
      id: '#/properties/name-input',
      label: 'Name',
      supportingText: 'First\nsecond',
      error: true,
      errorText: 'Too short',
      disabled: false,
      required: true
    })
  })

  it('reports no error and an empty error text for a valid control', () => {
    const { binding } = bind()

    expect(binding.fieldProps.value.error).toBe(false)
    expect(binding.fieldProps.value.errorText).toBe('')
  })

  it('disables the primitive when the control is not enabled', () => {
    const { binding } = bind({ enabled: false })

    expect(binding.fieldProps.value.disabled).toBe(true)
  })

  it('shows a missing value as an empty text and any other value as text', () => {
    expect(bind({ data: undefined }).binding.text.value).toBe('')
    expect(bind({ data: null }).binding.text.value).toBe('')
    expect(bind({ data: 42, schema: { type: 'integer' } }).binding.text.value).toBe('42')
  })

  it('treats number and integer schemas as numeric', () => {
    expect(bind({ schema: { type: 'number' } }).binding.numeric.value).toBe(true)
    expect(bind({ schema: { type: 'integer' } }).binding.numeric.value).toBe(true)
    expect(bind({ schema: { type: 'string' } }).binding.numeric.value).toBe(false)
  })
})

describe('useFieldBinding onInput', () => {
  it('stores typed text as it is', () => {
    const { binding, handleChange } = bind()

    binding.onInput('alpha')

    expect(handleChange).toHaveBeenCalledWith('name', 'alpha')
  })

  it('removes the value when the field is emptied', () => {
    const { binding, handleChange } = bind()

    binding.onInput('')

    expect(handleChange).toHaveBeenCalledWith('name', undefined)
  })

  it('stores a number for a numeric field', () => {
    const { binding, handleChange } = bind({ schema: { type: 'integer' } })

    binding.onInput('8080')

    expect(handleChange).toHaveBeenCalledWith('name', 8080)
  })

  it('removes the value when a numeric field holds no number', () => {
    const { binding, handleChange } = bind({ schema: { type: 'number' } })

    binding.onInput('abc')

    expect(handleChange).toHaveBeenCalledWith('name', undefined)
  })
})

describe('useFieldBinding change', () => {
  it('writes a value to the path of the control, even an empty list', () => {
    const { binding, handleChange } = bind({ path: 'tags', schema: { type: 'array' } })

    binding.change([])

    expect(handleChange).toHaveBeenCalledWith('tags', [])
  })
})
