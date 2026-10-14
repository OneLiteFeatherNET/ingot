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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import FileUpload from './FileUpload.vue'

// Every deploy stays pending, so a test can look at the form while the upload is in flight.
const { deploy } = vi.hoisted(() => ({ deploy: { calls: [] } }))

vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const client = ref({
    maven: {
      deploy: (...args) => {
        deploy.calls.push(args)
        return new Promise(() => {})
      },
      generatePom: () => new Promise(() => {})
    }
  })
  return { useSession: () => ({ client }) }
})

vi.mock('../../store/qualifier', () => ({
  default: () => ({
    qualifier: { path: 'releases/net/example' },
    refreshQualifier: () => {}
  })
}))

let wrapper

const mountUpload = async () => {
  wrapper = mount(FileUpload)
  await flushPromises()
  return wrapper
}

const uploadButton = () =>
  wrapper.findAll('button').find(button => button.text() === 'Upload files')

const selectFile = async (name) => {
  wrapper.vm.files = [{ name, file: new Blob(['content']) }]
  await nextTick()
}

describe('FileUpload while an upload is in flight', () => {
  beforeEach(() => {
    deploy.calls.length = 0
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('sends a file once when Upload is pressed twice', async () => {
    await mountUpload()
    await selectFile('example-1.0.jar')

    uploadButton().trigger('click')
    uploadButton().trigger('click')
    await flushPromises()

    expect(deploy.calls, 'a double click must not send the file again').toHaveLength(1)
  })

  it('disables the upload button until the batch has settled', async () => {
    await mountUpload()
    await selectFile('example-1.0.jar')

    uploadButton().trigger('click')
    await flushPromises()

    expect(uploadButton().attributes('disabled'), 'the button cannot be pressed while busy').toBeDefined()
  })

  it('shows each file as uploading while it is sent', async () => {
    await mountUpload()
    await selectFile('example-1.0.jar')

    uploadButton().trigger('click')
    await flushPromises()

    expect(wrapper.text(), 'the file reports its progress').toContain('Uploading')
  })
})

describe('FileUpload controls', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('wraps each option checkbox in a label that names it', async () => {
    await mountUpload()
    await selectFile('example-1.0.jar')

    const checksums = wrapper.findAll('input[type="checkbox"]')
      .map(checkbox => checkbox.element.closest('label'))
      .find(label => label?.textContent.includes('Generate default checksums'))

    expect(checksums, 'the checksum option is a labelled checkbox').toBeDefined()
    expect(checksums.querySelector('input[type="checkbox"]'), 'the label contains the checkbox itself').not.toBeNull()
  })

  it('names the remove button after the file it removes', async () => {
    await mountUpload()
    await selectFile('example-1.0.jar')

    const remove = wrapper.find('button[aria-label="Remove example-1.0.jar"]')

    expect(remove.exists(), 'every remove control says which file it removes').toBe(true)
  })
})
