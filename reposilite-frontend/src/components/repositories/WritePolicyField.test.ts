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

import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WritePolicyField from './WritePolicyField.vue'

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// The help line of each choice, in the order of the choices: Allow, Allow once, Read only.
const helpLines = () => {
  wrapper = mount(WritePolicyField, { props: { modelValue: 'allow', offerReadOnly: true } })
  return wrapper.findAll('fieldset p').map(line => line.text())
}

describe('the write policy help text', () => {
  it('gives each choice the exact help line of its status code', () => {
    expect(helpLines(), 'the three help lines, in the order of the choices').toEqual([
      'Deploys are accepted.',
      'The first deploy of each file is accepted; a later deploy of the same file is refused with 409.',
      'Deploys and deletes are refused with 405 Method Not Allowed. Reads are not affected. If the instance is in read-only mode, every repository answers 503 first.'
    ])
  })

  it('names the 409 status code in the Allow once help line', () => {
    expect(helpLines()[1], 'Allow once is refused with 409 on a second deploy').toContain('409')
  })

  it('names the 405 and the 503 status codes in the Read only help line', () => {
    const readOnly = helpLines()[2]
    expect(readOnly, 'Read only is refused with 405').toContain('405 Method Not Allowed')
    expect(readOnly, 'the global read-only mode answers 503 first').toContain('503')
  })
})
