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

import { expect } from 'vitest'
import type { VueWrapper } from '@vue/test-utils'
import MdTextField from '../components/md/MdTextField.vue'
import MdRadio from '../components/md/MdRadio.vue'
import MdCheckbox from '../components/md/MdCheckbox.vue'

/*
 * The form rule of the dashboard, as an assertion for the test of a component: every text field is the
 * default `outlined` variant, and every radio button and checkbox on the screen is drawn by a primitive,
 * never by a hand-built native input. `root` is the element to look into; a dialog is teleported, so it
 * is `document.body` there.
 */
const expectPrimitiveControls = (wrapper: VueWrapper, scope?: ParentNode) => {
  const root: ParentNode = scope ?? (wrapper.element as Element)
  const variants = wrapper.findAllComponents(MdTextField).map(field => field.props('variant') as string)
  expect(variants.length, 'the component draws text fields').toBeGreaterThan(0)
  expect(variants.every(variant => variant === 'outlined'), `every text field is outlined, got ${variants.join(', ')}`).toBe(true)

  expect(
    root.querySelectorAll('input[type="radio"]').length,
    'every radio button is an MdRadio'
  ).toBe(wrapper.findAllComponents(MdRadio).length)
  expect(
    root.querySelectorAll('input[type="checkbox"]').length,
    'every checkbox is an MdCheckbox'
  ).toBe(wrapper.findAllComponents(MdCheckbox).length)
}

export { expectPrimitiveControls }
