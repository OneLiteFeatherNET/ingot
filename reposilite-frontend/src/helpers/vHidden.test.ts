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

import { describe, expect, it } from 'vitest'
import type { DirectiveBinding, TransitionHooks, VNode } from 'vue'
import { vHidden } from './vHidden'

// The directive is driven by hand with a recorded transition, so no test needs a browser, a timer or a mounted tree.
interface Recorder {
  calls: string[]
  finishLeave: (() => void) | undefined
}

const recorder = (): Recorder => ({ calls: [], finishLeave: undefined })

const transitionOf = (record: Recorder): TransitionHooks =>
  ({
    beforeEnter: () => {
      record.calls.push('beforeEnter')
      // A leave that is still running is ended by the next enter, as the Vue transition does for an element it keeps.
      record.finishLeave?.()
    },
    enter: () => record.calls.push('enter'),
    leave: (_element: Element, remove: () => void) => {
      record.calls.push('leave')
      record.finishLeave = () => {
        record.finishLeave = undefined
        remove()
      }
    },
  }) as unknown as TransitionHooks

const vnodeWith = (transition: TransitionHooks | null) => ({ transition }) as unknown as VNode<unknown, HTMLElement>
const binding = (value: boolean, oldValue: boolean | null = null) => ({ value, oldValue }) as DirectiveBinding<boolean>

const mounted = (hidden: boolean, transition: TransitionHooks | null) => {
  const element = document.createElement('div')
  vHidden.beforeMount?.(element, binding(hidden), vnodeWith(transition), null)
  vHidden.mounted?.(element, binding(hidden), vnodeWith(transition), null)
  return element
}

const update = (element: HTMLElement, hidden: boolean, transition: TransitionHooks | null) =>
  vHidden.updated?.(element, binding(hidden, !hidden), vnodeWith(transition), vnodeWith(transition))

describe('vHidden without a transition', () => {
  it('sets the hidden attribute from its value on mount', () => {
    const element = mounted(true, null)

    expect(element.hidden, 'a hidden panel carries the hidden attribute').toBe(true)
  })

  it('toggles the hidden attribute at once when the value changes', () => {
    const element = mounted(false, null)

    update(element, true, null)

    expect(element.hidden, 'with no transition the panel is hidden in the same update').toBe(true)
  })
})

describe('vHidden with a transition', () => {
  it('keeps a leaving panel drawn and inert until its leave has ended, then hides it', () => {
    const record = recorder()
    const element = mounted(false, transitionOf(record))

    update(element, true, transitionOf(record))

    expect(element.hidden, 'the panel stays drawn while it leaves').toBe(false)
    expect(element.inert, 'a leaving panel takes no focus and no click').toBe(true)

    record.finishLeave?.()

    expect(element.hidden, 'the panel is hidden once the leave has ended').toBe(true)
    expect(element.inert, 'a hidden panel is no longer marked inert').toBe(false)
  })

  it('draws an entering panel between the before-enter and the enter hook', () => {
    const record = recorder()
    const element = mounted(true, transitionOf(record))

    update(element, false, transitionOf(record))

    expect(record.calls, 'the enter classes are set before the panel is drawn').toEqual(['beforeEnter', 'enter'])
    expect(element.hidden, 'the entering panel is drawn').toBe(false)
  })

  it('draws a panel that is chosen again while it leaves, with no hidden attribute left behind', () => {
    const record = recorder()
    const element = mounted(false, transitionOf(record))
    update(element, true, transitionOf(record))

    update(element, false, transitionOf(record))

    expect(element.hidden, 'the panel that came back is drawn').toBe(false)
    expect(element.inert, 'the panel that came back takes focus again').toBe(false)
  })

  it('runs no hook when the value did not change', () => {
    const record = recorder()
    const element = mounted(true, transitionOf(record))

    vHidden.updated?.(element, binding(true, true), vnodeWith(transitionOf(record)), vnodeWith(transitionOf(record)))

    expect(record.calls, 'an unchanged panel does not move').toEqual([])
    expect(element.hidden, 'the panel stays hidden').toBe(true)
  })
})
