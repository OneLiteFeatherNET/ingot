import {inject, provide} from 'vue'
import {createDefaultValue} from '@jsonforms/core'
import type {JsonSchema} from '@jsonforms/core'

/*
 * The value a new entry starts from. createDefaultValue takes the root schema for references, and these
 * callers have never passed one, so none is passed here either.
 */
export const createEntryValue = (schema: JsonSchema): unknown => createDefaultValue(schema, undefined as unknown as JsonSchema)

/*
 * Set by an optional object's switch row for the path of its value. The object renderer at that
 * path draws its fields without a title, because the switch row already names them.
 */
export const OPTIONAL_PATH_KEY = 'ingot.optionalObjectPath'

/* How deep the renderer stands inside the objects and groups above it, and which kind of element that is. */
export interface NestedInfo {
  level: number
  parentElement?: string
}

export const useNested = (element?: string): NestedInfo => {
  const nestedInfo = inject<NestedInfo>('jsonforms.nestedInfo', { level: 0 })
  if (element) {
    provide('jsonforms.nestedInfo', {
      level: nestedInfo.level + 1,
      parentElement: element,
    })
  }
  return nestedInfo
}
