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
import { mount } from '@vue/test-utils'
import ProblemAlert from './ProblemAlert.vue'
import MdAlert from '../md/MdAlert.vue'
import type { Problem } from './types'

const problem: Problem = { title: 'Bad Request', detail: 'The URL carries user information', status: 400 }

describe('the problem alert', () => {
  it('draws the problem with the shared MdAlert in the error tone', () => {
    const wrapper = mount(ProblemAlert, { props: { problem } })

    const alert = wrapper.findComponent(MdAlert)
    expect(alert.exists(), 'the problem is drawn by MdAlert, not a box of its own').toBe(true)
    expect(alert.props('tone'), 'a failed call is an error').toBe('error')
  })

  it('shows the title and the detail of the problem', () => {
    const wrapper = mount(ProblemAlert, { props: { problem } })

    expect(wrapper.text()).toContain('Bad Request')
    expect(wrapper.text()).toContain('The URL carries user information')
  })

  it('draws nothing without a problem', () => {
    const wrapper = mount(ProblemAlert, { props: { problem: null } })

    expect(wrapper.findComponent(MdAlert).exists(), 'no alert is drawn when nothing failed').toBe(false)
  })
})
