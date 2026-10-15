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
import { mount, flushPromises } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as selectTester } from './MdSelectRenderer.vue'
import MdSelectRenderer from './MdSelectRenderer.vue'
import { tester as constantTester } from './ConstantRenderer.vue'
import ConstantRenderer from './ConstantRenderer.vue'
import { tester as textTester } from './MdTextFieldRenderer.vue'
import MdTextFieldRenderer from './MdTextFieldRenderer.vue'
import { tester as oneOfTester } from './OneOfRenderer.vue'
import OneOfRenderer, { variantIndexOf, defaultValueOf, holdsData } from './OneOfRenderer.vue'

/*
 * The storage provider of a repository: the discriminator `type` is a constant in each branch,
 * as the backend schema writes it. The file system branch has a default; S3 needs a bucket.
 */
const fileSystem = {
  type: 'object',
  title: 'File system Storage Provider',
  properties: {
    type: { type: 'string', const: 'fs' },
    mount: { type: 'string', default: '/data' }
  },
  required: ['type']
}

const s3 = {
  type: 'object',
  title: 'S3 Storage Provider',
  properties: {
    type: { type: 'string', const: 's3' },
    bucketName: { type: 'string', title: 'Bucket' },
    endpoint: { type: 'string', title: 'Endpoint' }
  },
  required: ['type', 'bucketName']
}

const storageSchema = { type: 'object', properties: { storageProvider: { oneOf: [fileSystem, s3] } } }

const renderers = markRaw([
  { tester: selectTester, renderer: MdSelectRenderer },
  { tester: constantTester, renderer: ConstantRenderer },
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: oneOfTester, renderer: OneOfRenderer },
  ...vanillaRenderers
])

let wrapper

const mountStorage = (data) => {
  wrapper = mount(JsonForms, {
    props: {
      data,
      schema: storageSchema,
      uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/storageProvider', label: 'Storage Provider' }] },
      renderers,
      config: { showUnfocusedDescription: true },
      ajv: markRaw(createAjv({ useDefaults: true }))
    },
    attachTo: document.body
  })
  return wrapper
}

const select = () => wrapper.find('select')
const lastData = () => wrapper.emitted('change').at(-1)[0].data.storageProvider
const dialogCard = () => document.querySelector('[role="alertdialog"]')
const dialogButton = (text) => [...document.querySelectorAll('[role="alertdialog"] button')]
  .find(button => button.textContent.trim() === text)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('variant detection from the discriminator', () => {
  const branches = [fileSystem, s3]

  it('finds the S3 variant from its type constant, even when the bucket is still missing', () => {
    expect(variantIndexOf(branches, { type: 's3' })).toBe(1)
  })

  it('finds the file system variant from its type constant', () => {
    expect(variantIndexOf(branches, { type: 'fs', mount: '/srv' })).toBe(0)
  })

  it('finds nothing when the data has no discriminator', () => {
    expect(variantIndexOf(branches, { bucketName: 'test' })).toBeUndefined()
  })

  it('finds nothing when the data is not an object', () => {
    expect(variantIndexOf(branches, 's3')).toBeUndefined()
    expect(variantIndexOf(branches, undefined)).toBeUndefined()
  })

  it('never matches a branch without a constant', () => {
    expect(variantIndexOf([{ type: 'object', properties: { type: { type: 'string' } } }], { type: 'x' })).toBeUndefined()
  })

  it('writes the discriminator into the data a variant starts from', () => {
    expect(defaultValueOf(s3)).toEqual({ type: 's3' })
    expect(defaultValueOf(fileSystem)).toEqual({ type: 'fs', mount: '/data' })
  })
})

describe('data that would be lost by a switch', () => {
  it('holds nothing when only the discriminator and the defaults are set', () => {
    expect(holdsData({ type: 'fs', mount: '/data' }, fileSystem)).toBe(false)
    expect(holdsData({ type: 'fs' }, fileSystem)).toBe(false)
  })

  it('holds nothing when the fields are blank', () => {
    expect(holdsData({ type: 's3', bucketName: '' }, s3)).toBe(false)
    expect(holdsData({ type: 's3', endpoint: undefined }, s3)).toBe(false)
  })

  it('holds data when a field carries a value', () => {
    expect(holdsData({ type: 's3', bucketName: 'test' }, s3)).toBe(true)
    expect(holdsData({ type: 'fs', mount: '/srv' }, fileSystem)).toBe(true)
  })
})

describe('switching the storage provider', () => {
  it('opens the confirmation dialog when the current variant holds data', async () => {
    mountStorage({ storageProvider: { type: 'fs', mount: '/srv' } })

    await select().setValue('1')
    await flushPromises()

    expect(dialogCard(), 'the switch must ask before it throws the data away').not.toBeNull()
  })

  it('switches at once, without a dialog, when the current variant holds no data', async () => {
    mountStorage({ storageProvider: { type: 'fs', mount: '/data' } })

    await select().setValue('1')
    await flushPromises()

    expect(dialogCard(), 'nothing would be lost, so nothing is asked').toBeNull()
    expect(lastData()).toMatchObject({ type: 's3' })
  })

  it('leaves the data and the select on the previous variant when the switch is cancelled', async () => {
    mountStorage({ storageProvider: { type: 'fs', mount: '/srv' } })
    await select().setValue('1')
    await flushPromises()
    const writesBeforeCancel = JSON.stringify(wrapper.emitted('change'))

    dialogButton('Cancel').click()
    await flushPromises()

    expect(dialogCard(), 'the dialog closes on cancel').toBeNull()
    expect(select().element.value, 'the select must not stay on the new variant').toBe('0')
    expect(JSON.stringify(wrapper.emitted('change')), 'a cancelled switch writes nothing').toBe(writesBeforeCancel)
  })

  it('replaces the data with the new variant when the switch is confirmed', async () => {
    mountStorage({ storageProvider: { type: 'fs', mount: '/srv' } })
    await select().setValue('1')
    await flushPromises()

    dialogButton('Switch').click()
    await flushPromises()

    expect(dialogCard()).toBeNull()
    expect(select().element.value).toBe('1')
    expect(lastData(), 'the data now belongs to the S3 variant').toMatchObject({ type: 's3' })
    expect(lastData().mount, 'the file system setting is not kept as a bucket setting').not.toBe('/srv')
  })

  it('shows the headline and the supporting text with the current variant name', async () => {
    mountStorage({ storageProvider: { type: 's3', bucketName: 'test' } })

    await select().setValue('0')
    await flushPromises()

    expect(dialogCard().querySelector('h2').textContent.trim()).toBe('Switch storage provider?')
    expect(dialogCard().querySelector('p').textContent).toContain('The settings of S3 Storage Provider will be removed.')
  })

  it('reads the variant from the discriminator, not from the position of the schema', async () => {
    mountStorage({ storageProvider: { type: 's3' } })

    expect(select().element.value, 'an S3 variant without a bucket is still S3').toBe('1')
  })
})
