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

import { afterEach, describe, expect, it } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import DeletePolicyDialog from './DeletePolicyDialog.vue'

let wrapper: VueWrapper | undefined

// Type contract, checked by vue-tsc: policyId is a string, so a number is rejected. Never called at runtime.
// @ts-expect-error a number is not a policy id
const mountWithNumericId = () => mount(DeletePolicyDialog, { props: { modelValue: false, policyId: 42 } })
void mountWithNumericId

const openDialog = async (props: { policyId: string }) => {
  wrapper = mount(DeletePolicyDialog, {
    props: { modelValue: false, ...props },
    attachTo: document.body
  }) as VueWrapper
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const confirmButton = () => document.getElementById('delete-policy-confirm') as HTMLElement
const cancelButton = () => document.getElementById('delete-policy-cancel') as HTMLElement

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('DeletePolicyDialog', () => {
  it('names the policy in its title', async () => {
    await openDialog({ policyId: 'snapshots-weekly' })

    expect(document.getElementById('delete-policy-title')?.textContent).toContain('Delete snapshots-weekly?')
  })

  it('emits confirm when the delete button is pressed', async () => {
    await openDialog({ policyId: 'snapshots-weekly' })

    confirmButton().click()
    await flushPromises()

    expect(wrapper?.emitted('confirm')).toHaveLength(1)
  })

  it('emits update:modelValue with false when cancel is pressed', async () => {
    await openDialog({ policyId: 'snapshots-weekly' })

    cancelButton().click()
    await flushPromises()

    expect(wrapper?.emitted('update:modelValue')).toEqual([[false]])
  })
})
