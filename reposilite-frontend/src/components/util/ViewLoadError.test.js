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
import ViewLoadError from './ViewLoadError.vue'

describe('ViewLoadError', () => {
  it('says that the view could not be loaded and announces it', () => {
    const wrapper = mount(ViewLoadError)

    expect(wrapper.find('[role="alert"]').text()).toContain('This view could not be loaded.')
  })

  it('offers a reload button', () => {
    const wrapper = mount(ViewLoadError)

    const reload = wrapper.findAll('button').find(button => button.text() === 'Reload')

    expect(reload, 'a failed chunk is only retried by loading the page again').toBeDefined()
  })
})
