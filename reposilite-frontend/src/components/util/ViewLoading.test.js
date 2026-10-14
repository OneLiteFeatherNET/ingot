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
import ViewLoading from './ViewLoading.vue'

describe('ViewLoading', () => {
  it('shows an indeterminate progress bar named for the view', () => {
    const wrapper = mount(ViewLoading)

    const bar = wrapper.find('[role="progressbar"]')

    expect(bar.exists(), 'a lazily loaded view must show that it is loading').toBe(true)
    expect(bar.attributes('aria-label')).toBe('Loading view')
    expect(bar.attributes('aria-valuenow'), 'an indeterminate bar has no value to announce').toBeUndefined()
  })
})
