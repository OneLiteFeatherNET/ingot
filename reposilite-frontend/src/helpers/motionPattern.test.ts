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

import { describe, it, expect } from 'vitest'
import { motionScrimTransitionName, motionTransitionName } from './motionPattern'

describe('motionTransitionName', () => {
  it('prefixes the pattern with motion-, the prefix of the classes in motion-transitions.css', () => {
    expect(motionTransitionName('dialog'), 'the dialog pattern').toBe('motion-dialog')
    expect(motionTransitionName('fade-through'), 'the fade through pattern').toBe('motion-fade-through')
  })

  it('takes the back class of shared-x for a sibling that comes earlier', () => {
    expect(motionTransitionName('shared-x', -1), 'an earlier sibling').toBe('motion-shared-x-back')
    expect(motionTransitionName('shared-x', 1), 'a later sibling').toBe('motion-shared-x')
  })

  it('ignores the direction for a pattern without a back class', () => {
    expect(motionTransitionName('dialog', -1), 'the dialog pattern has one direction').toBe('motion-dialog')
  })
})

describe('motionScrimTransitionName', () => {
  it('names the scrim fade of the dialog pattern after the pattern', () => {
    expect(motionScrimTransitionName('dialog'), 'the scrim of a dialog').toBe('motion-dialog-scrim')
  })
})
