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
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as tagsTester } from './TagsFieldRenderer.vue'
import TagsFieldRenderer from './TagsFieldRenderer.vue'
import { tester as arrayTester } from './ArrayListRenderer.vue'
import ArrayListRenderer from './ArrayListRenderer.vue'

// The vanilla renderers draw the layout around the control. The tags field and the generic list
// are the two candidates for the control itself, and the tags field must win.
const renderers = markRaw([
  { tester: tagsTester, renderer: TagsFieldRenderer },
  { tester: arrayTester, renderer: ArrayListRenderer },
  ...vanillaRenderers
])

const tagsSchema = {
  type: 'object',
  properties: {
    tags: { type: 'array', items: { type: 'string', pattern: '^[a-z0-9][a-z0-9-]{0,31}$' }, maxItems: 10, uniqueItems: true, title: 'Tags' }
  }
}

let wrapper

const mountTags = (data = { tags: [] }, config = {}) => {
  wrapper = mount(JsonForms, {
    props: {
      data,
      schema: tagsSchema,
      uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/tags' }] },
      renderers,
      config,
      ajv: markRaw(createAjv({ useDefaults: true }))
    },
    attachTo: document.body
  })
  return wrapper
}

const field = () => wrapper.find('input')
const chipTexts = () => wrapper.findAll('[data-test="chip"]').map(chip => chip.text())
const lastData = () => wrapper.emitted('change').at(-1)[0].data
const press = (key) => field().trigger('keydown', { key })

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('TagsFieldRenderer tester', () => {
  // JSON Forms hands a tester the root schema and resolves the scope itself, so the tests do the same.
  const rootWith = (name, schema) => ({ type: 'object', properties: { [name]: schema } })
  const control = { type: 'Control', scope: '#/properties/tags' }
  const stringArray = { type: 'array', items: { type: 'string' } }

  it('takes a string array named tags, above the array renderer', () => {
    const root = rootWith('tags', stringArray)

    expect(tagsTester(control, root), 'the tags field outranks the generic list').toBeGreaterThan(arrayTester(control, root))
  })

  it('leaves string arrays with other names to the list', () => {
    const root = rootWith('allowedGroups', stringArray)

    expect(tagsTester({ type: 'Control', scope: '#/properties/allowedGroups' }, root)).toBe(-1)
  })

  it('leaves an array of objects named tags to the list', () => {
    expect(tagsTester(control, rootWith('tags', { type: 'array', items: { type: 'object' } }))).toBe(-1)
  })
})

describe('TagsFieldRenderer', () => {
  it('shows the tags as chips instead of the generic list', () => {
    mountTags({ tags: ['team-core', 'ci'] })

    expect(chipTexts()).toEqual(['team-core', 'ci'])
    expect(wrapper.find('[data-test="array"]').exists(), 'the generic list is not drawn').toBe(false)
  })

  it('writes a new chip into the data of the form', async () => {
    mountTags({ tags: ['team-core'] })

    await field().setValue('ci')
    await press('Enter')

    expect(lastData().tags).toEqual(['team-core', 'ci'])
  })

  it('writes the removal of a chip into the data of the form', async () => {
    mountTags({ tags: ['team-core', 'ci'] })

    await wrapper.find('[aria-label="Remove team-core"]').trigger('click')

    expect(lastData().tags).toEqual(['ci'])
  })

  it('refuses a tag that is not valid and writes nothing', async () => {
    mountTags({ tags: [] })
    const changesBefore = wrapper.emitted('change')?.length ?? 0

    await field().setValue('Not Valid')
    await press('Enter')

    expect(wrapper.emitted('change')?.length ?? 0, 'no change reaches the form').toBe(changesBefore)
    expect(wrapper.text()).toContain('Use lower case letters, digits and hyphens')
  })

  it('offers the tags that other repositories use, from the form configuration', async () => {
    mountTags({ tags: ['team-core'] }, { tagSuggestions: ['team-core', 'ci', 'internal'] })

    await field().trigger('focus')

    const suggestions = wrapper.findAll('[data-test="suggestion"]').map(button => button.text())
    expect(suggestions).toEqual(['ci', 'internal'])
  })

  it('marks the field invalid when the form reports the limit on the number of tags', () => {
    const eleven = Array.from({ length: 11 }, (_, index) => `tag-${index}`)
    mountTags({ tags: eleven })

    expect(field().attributes('aria-invalid')).toBe('true')
  })
})
