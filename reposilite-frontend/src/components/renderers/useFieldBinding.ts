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

import { computed } from 'vue'
import type { Ref } from 'vue'
import { descriptionText } from './description'

/* The part of a JSON Forms control that a field primitive needs. */
export interface FieldControl {
  id: string
  path: string
  label: string
  description?: string | null
  errors?: string
  enabled: boolean
  required?: boolean
  data?: string | number | boolean | null
  schema?: { type?: string | string[] }
}

export interface FieldBindingInput {
  control: Ref<FieldControl>
  handleChange: (path: string, value: unknown) => void
}

/*
 * The mapping from JSON Forms props to the props of a field primitive, shared by the renderers. It
 * returns the props every primitive takes, the value as text, and the handlers that write back. An
 * emptied field removes the value instead of storing an empty string, so the property is left out
 * of the saved configuration.
 */
export const useFieldBinding = ({ control, handleChange }: FieldBindingInput) => {
  const path = computed(() => control.value.path)
  const numeric = computed(() => ['number', 'integer'].includes(control.value.schema?.type as string))

  const text = computed(() => {
    const data = control.value.data
    return data === undefined || data === null ? '' : String(data)
  })

  const fieldProps = computed(() => ({
    id: `${control.value.id}-input`,
    label: control.value.label,
    supportingText: descriptionText(control.value.description),
    error: Boolean(control.value.errors),
    errorText: control.value.errors || '',
    disabled: !control.value.enabled,
    required: Boolean(control.value.required)
  }))

  const change = (value: unknown) => {
    handleChange(path.value, value)
  }

  const onInput = (raw: string) => {
    if (raw === '') {
      change(undefined)
    } else if (numeric.value) {
      const parsed = Number(raw)
      change(Number.isNaN(parsed) ? undefined : parsed)
    } else {
      change(raw)
    }
  }

  return { path, numeric, text, fieldProps, change, onInput }
}
