import type { FunctionalComponent } from 'vue'

/*
 * Stands where a MdTransition would be when a page opts out of its transition. It draws the slot and nothing else, so
 * the page is swapped at once and no element is added, the same as the wrapper.
 */
export const NoMotion: FunctionalComponent = (_props, { slots }) => slots.default?.()
