import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { Transition, h, type VNode } from 'vue'
import MdTransition from './MdTransition.vue'

// The child of the wrapper is a plain element, so the tests can see what the wrapper renders around it. The real
// Transition is used, not the stub of the test utilities, so the props the wrapper passes to it are the ones asserted.
const child = () => h('div', { 'data-test': 'child' }, 'content')

const mountWith = (props: Record<string, unknown>) =>
  mount(MdTransition, {
    props: props as never,
    slots: { default: child },
    global: { stubs: { transition: false } },
  }) as VueWrapper

// The props of the Transition that the wrapper renders at its root, or undefined when it renders the slot alone. The
// Transition is a functional component, so its props are read from the root vnode of the wrapper.
const transitionOf = (wrapper: VueWrapper): Record<string, unknown> | undefined => {
  const root = (wrapper.vm as unknown as { $: { subTree: VNode } }).$.subTree
  return root.type === Transition ? { ...root.props } : undefined
}

// The element the wrapper renders at its root.
const rootOf = (wrapper: VueWrapper) => wrapper.element as HTMLElement

describe('MdTransition', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it.each([
    ['fade-through', 'motion-fade-through'],
    ['shared-x', 'motion-shared-x'],
    ['shared-y', 'motion-shared-y'],
    ['menu', 'motion-menu'],
    ['dialog', 'motion-dialog'],
    ['sheet', 'motion-sheet'],
    ['snackbar', 'motion-snackbar'],
    ['expand', 'motion-expand'],
    ['fade', 'motion-fade'],
  ])('names the Vue transition of the %s pattern %s', (pattern, name) => {
    const wrapper = mountWith({ pattern })

    expect(transitionOf(wrapper)?.name, `the transition name of ${pattern}`).toBe(name)
  })

  it.each([
    ['fade-through', 'out-in'],
    ['shared-x', 'out-in'],
    ['shared-y', 'out-in'],
    ['snackbar', 'out-in'],
    ['menu', 'default'],
    ['dialog', 'default'],
    ['sheet', 'default'],
    ['expand', 'default'],
    ['fade', 'default'],
  ])('uses the %s mode %s by default', (pattern, mode) => {
    const wrapper = mountWith({ pattern })

    expect(transitionOf(wrapper)?.mode, `the mode of ${pattern}`).toBe(mode)
  })

  it('takes the mode that the caller passes over the mode of the pattern', () => {
    const wrapper = mountWith({ pattern: 'fade-through', mode: 'in-out' })

    expect(transitionOf(wrapper)?.mode).toBe('in-out')
  })

  it('does not run the transition on first paint by default', () => {
    const wrapper = mountWith({ pattern: 'fade-through' })

    expect(transitionOf(wrapper)?.appear, 'appear is false unless the caller asks for it').toBe(false)
  })

  it('runs the transition on first paint when the caller asks for appear', () => {
    const wrapper = mountWith({ pattern: 'fade-through', appear: true })

    expect(transitionOf(wrapper)?.appear).toBe(true)
  })

  it('names the backward shared axis class when the direction is -1', () => {
    const wrapper = mountWith({ pattern: 'shared-x', direction: -1 })

    expect(transitionOf(wrapper)?.name, 'the class that moves the page the other way').toBe('motion-shared-x-back')
  })

  it('keeps the forward shared axis class when the direction is 1', () => {
    const wrapper = mountWith({ pattern: 'shared-x', direction: 1 })

    expect(transitionOf(wrapper)?.name).toBe('motion-shared-x')
  })

  it('adds no element of its own, so the rendered root is the slot child', () => {
    const wrapper = mountWith({ pattern: 'menu' })

    expect(rootOf(wrapper).tagName, 'the root element is the child of the slot').toBe('DIV')
    expect(wrapper.attributes('data-test')).toBe('child')
  })

  it('renders the slot without a transition, and says so, when the pattern is unknown', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const wrapper = mountWith({ pattern: 'slide-sideways' })

    expect(transitionOf(wrapper), 'no Transition for an unknown pattern').toBeUndefined()
    expect(wrapper.find('[data-test="child"]').exists(), 'the slot is still drawn').toBe(true)
    expect(warn, 'a message names the unknown pattern').toHaveBeenCalledWith(expect.stringContaining('slide-sideways'))
  })

  describe('under reduced motion', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('renders the same transition and root, and leaves the motion to the stylesheet rule', () => {
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: query === '(prefers-reduced-motion: reduce)',
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }))
      const wrapper = mountWith({ pattern: 'menu' })

      expect(transitionOf(wrapper)?.name, 'the transition of the pattern is unchanged').toBe('motion-menu')
      expect(rootOf(wrapper).tagName, 'the root is still the slot child').toBe('DIV')
      expect(wrapper.classes(), 'the wrapper adds no motion class of its own').toEqual([])
    })
  })
})
