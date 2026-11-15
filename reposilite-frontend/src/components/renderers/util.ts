import {inject, provide} from 'vue'

/*
 * Set by an optional object's switch row for the path of its value. The object renderer at that
 * path draws its fields without a title, because the switch row already names them.
 */
export const OPTIONAL_PATH_KEY = 'ingot.optionalObjectPath'

export const useNested = (element) => {
  const nestedInfo = inject('jsonforms.nestedInfo', { level: 0 })
  if (element) {
    provide('jsonforms.nestedInfo', {
      level: nestedInfo.level + 1,
      parentElement: element,
    })
  }
  return nestedInfo
}
