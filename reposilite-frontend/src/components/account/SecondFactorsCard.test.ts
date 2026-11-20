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

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import SecondFactorsCard from './SecondFactorsCard.vue'

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
const passkeys = vi.hoisted(() => ({ supported: true, create: vi.fn() }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: toast.success,
  createErrorToast: toast.error,
  errorMessage: (error: unknown) => {
    const failure = error as { response?: { data?: { message?: string } }; message?: string } | undefined
    return failure?.response?.data?.message ?? failure?.message ?? 'unknown error'
  }
}))

// The browser's WebAuthn support is a switch the tests set. The ceremony itself is covered by the helper's tests.
vi.mock('../../store/webauthn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../store/webauthn')>()),
  isWebAuthnSupported: () => passkeys.supported,
  createPasskey: (...args: unknown[]): Promise<unknown> => passkeys.create(...args) as Promise<unknown>
}))

const SEED = 'JBSWY3DPEHPK3PXP'
const OTPAUTH_URI = `otpauth://totp/Ingot:alice?secret=${SEED}&issuer=Ingot`
const RECOVERY_CODES = ['ABCD-EFGH', 'JKLM-NPQR', 'STUV-WXYZ']
const PASSKEY_OPTIONS = {
  challenge: 'Y2hhbGxlbmdl',
  rpId: 'localhost',
  userName: 'alice',
  userHandle: 'aGFuZGxl',
  allowCredentials: [],
  challengeId: 'challenge-1'
}
const PHONE_APP = { id: 1, name: 'Phone app', kind: 'AUTHENTICATOR', createdAt: '2026-11-01T10:00:00Z', lastUsedAt: null }
const LAPTOP_KEY = { id: 2, name: 'Laptop key', kind: 'PASSKEY', createdAt: '2026-11-02T10:00:00Z', lastUsedAt: null }
const DESK_KEY = { id: 3, name: 'Desk key', kind: 'PASSKEY', createdAt: '2026-11-03T10:00:00Z', lastUsedAt: null }

type Api = {
  factors: {
    list: Mock
    startAuthenticator: Mock
    confirmAuthenticator: Mock
    startPasskey: Mock
    registerPasskey: Mock
    rename: Mock
    remove: Mock
  }
}

const dialogs = () => [...document.querySelectorAll<HTMLElement>('[role="dialog"]')]
const bodyText = () => document.body.textContent ?? ''
const buttonLabelled = (label: string) =>
  [...document.querySelectorAll<HTMLButtonElement>('button')].find(node => node.textContent?.trim() === label)
const inputById = (id: string) => document.getElementById(id) as HTMLInputElement | null

const typeInto = async (id: string, value: string) => {
  const input = inputById(id)
  if (!input) throw new Error(`no field with id ${id}`)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

const click = async (label: string) => {
  const button = buttonLabelled(label)
  if (!button) throw new Error(`no button labelled ${label}`)
  button.click()
  await flushPromises()
}

describe('SecondFactorsCard', () => {
  let wrapper: VueWrapper
  let api: Api

  const mountCard = async () => {
    wrapper = mount(SecondFactorsCard, { props: { api: api as never, accountName: 'alice' }, attachTo: document.body }) as VueWrapper
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    toast.success.mockClear()
    toast.error.mockClear()
    passkeys.supported = true
    passkeys.create.mockReset().mockResolvedValue({ clientDataJSON: 'Y2xpZW50', attestationObject: 'YXR0ZXN0' })
    api = {
      factors: {
        list: vi.fn().mockResolvedValueOnce({ data: [PHONE_APP, LAPTOP_KEY] }).mockResolvedValue({ data: [PHONE_APP, LAPTOP_KEY, DESK_KEY] }),
        startAuthenticator: vi.fn().mockResolvedValue({ data: { otpauthUri: OTPAUTH_URI } }),
        confirmAuthenticator: vi.fn().mockResolvedValue({ data: { recoveryCodes: RECOVERY_CODES } }),
        startPasskey: vi.fn().mockResolvedValue({ data: PASSKEY_OPTIONS }),
        registerPasskey: vi.fn().mockResolvedValue({ data: { id: 3, name: 'Passkey' } }),
        rename: vi.fn((id: number, name: string) => Promise.resolve({ data: { ...PHONE_APP, id, name } })),
        remove: vi.fn().mockResolvedValue({ data: {} })
      }
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('lists each second factor with its name and kind', async () => {
    await mountCard()

    expect(bodyText(), 'the authenticator app is listed by its name').toContain('Phone app')
    expect(bodyText(), 'the passkey is listed by its name').toContain('Laptop key')
    expect(bodyText(), 'the kind is shown in words, not as the wire value').toContain('Authenticator app')
    expect(bodyText(), 'the kind is shown in words, not as the wire value').toContain('Passkey')
  })

  it('says so when the account has no second factor yet', async () => {
    api.factors.list.mockReset().mockResolvedValue({ data: [] })
    await mountCard()

    expect(bodyText(), 'an empty list is explained, not left blank').toContain('No second factor is set up')
  })

  it('renames a factor to the name typed in the field', async () => {
    await mountCard()

    await click('Rename')
    await typeInto('second-factor-name', 'Work phone')
    await click('Save name')

    expect(api.factors.rename, 'the rename call names the factor and the new name').toHaveBeenCalledWith(1, 'Work phone')
    expect(bodyText(), 'the list shows the new name at once').toContain('Work phone')
  })

  it('removes a factor with the current code', async () => {
    await mountCard()

    await click('Remove')
    await typeInto('second-factor-remove-code', '123456')
    await click('Remove factor')

    expect(api.factors.remove, 'the server needs a current code to remove a factor').toHaveBeenCalledWith(1, '123456')
    expect(bodyText(), 'the removed factor leaves the list').not.toContain('Phone app')
  })

  it('does not remove a factor while the code is empty', async () => {
    await mountCard()

    await click('Remove')
    await click('Remove factor')

    expect(api.factors.remove, 'an empty code is never sent').not.toHaveBeenCalled()
  })

  it('shows the authenticator seed as text once the enrolment starts', async () => {
    await mountCard()

    await click('Add an authenticator app')
    await click('Start')

    expect(api.factors.startAuthenticator.mock.calls[0]?.[0], 'the start call asks for the first factor without a code').toBeUndefined()
    expect(dialogs().map(d => d.textContent).join(' '), 'the enrolment shows the otpauth address as text').toContain(OTPAUTH_URI)
  })

  it('confirms the authenticator with the code and then shows the recovery codes', async () => {
    await mountCard()

    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '654321')
    await click('Confirm')

    expect(api.factors.confirmAuthenticator).toHaveBeenCalledWith('654321')
    expect(bodyText(), 'the recovery codes are shown after the factor is confirmed').toContain('ABCD-EFGH')
    expect(bodyText(), 'the new factor appears in the list').toContain('Desk key')
  })

  it('keeps the enrolment open when the confirmation code is wrong', async () => {
    api.factors.confirmAuthenticator.mockRejectedValue(Object.assign(new Error('Invalid code'), { response: { status: 401, data: { message: 'Invalid code' } } }))
    await mountCard()

    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '000000')
    await click('Confirm')

    expect(bodyText(), 'the error explains the wrong code').toContain('Invalid code')
    expect(bodyText(), 'the seed stays on screen so the user can try again').toContain(OTPAUTH_URI)
    expect(bodyText(), 'no recovery code is issued for a wrong code').not.toContain('ABCD-EFGH')
  })

  it('keeps the recovery codes dialog open until the stored check box is ticked', async () => {
    await mountCard()

    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '654321')
    await click('Confirm')

    expect(buttonLabelled('Done')?.disabled, 'the dialog cannot be closed before the user confirms the codes are stored').toBe(true)
  })

  it('copies the recovery codes, one per line', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    await mountCard()
    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '654321')
    await click('Confirm')

    await click('Copy codes')

    expect(writeText).toHaveBeenCalledWith(RECOVERY_CODES.join('\n'))
  })

  it('downloads the recovery codes as a text file', async () => {
    const createObjectURL = vi.fn().mockReturnValue('blob:codes')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURL, configurable: true })
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    await mountCard()
    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '654321')
    await click('Confirm')

    await click('Download codes')

    const blob = createObjectURL.mock.calls[0]?.[0] as Blob
    expect(blob?.type, 'the codes are saved as plain text').toBe('text/plain')
    expect(anchorClick, 'the download starts from a link').toHaveBeenCalled()
  })

  it('removes the seed from the screen when the enrolment dialog closes', async () => {
    await mountCard()
    await click('Add an authenticator app')
    await click('Start')

    await click('Cancel')

    expect(bodyText(), 'the seed is not kept after the enrolment dialog closes').not.toContain(SEED)
  })

  it('removes the recovery codes from the screen when their dialog closes', async () => {
    await mountCard()
    await click('Add an authenticator app')
    await click('Start')
    await typeInto('second-factor-enrol-code', '654321')
    await click('Confirm')
    const check = inputById('second-factor-recovery-check') as HTMLInputElement
    check.checked = true
    check.dispatchEvent(new Event('change'))
    await flushPromises()

    await click('Done')

    expect(bodyText(), 'the codes are never shown again once the dialog is closed').not.toContain('ABCD-EFGH')
  })

  it('enrols a passkey after the ceremony and lists it', async () => {
    await mountCard()

    await click('Add a passkey')
    await typeInto('second-factor-passkey-name', 'Desk key')
    await click('Create passkey')

    expect(passkeys.create, 'the registration ceremony runs with the server options').toHaveBeenCalledWith(
      expect.objectContaining({ challenge: PASSKEY_OPTIONS.challenge, rpId: 'localhost', userName: 'alice' })
    )
    expect(api.factors.registerPasskey, 'the attestation is sent with its challenge').toHaveBeenCalledWith(
      expect.objectContaining({ clientDataJSON: 'Y2xpZW50', attestationObject: 'YXR0ZXN0', challengeId: 'challenge-1' })
    )
    expect(api.factors.rename, 'the typed name is given to the new passkey after registration').toHaveBeenCalledWith(3, 'Desk key')
    expect(bodyText(), 'the new passkey is listed').toContain('Desk key')
  })

  it('offers no passkey action where WebAuthn is not supported', async () => {
    passkeys.supported = false
    await mountCard()

    expect(buttonLabelled('Add a passkey'), 'a browser without WebAuthn cannot run the ceremony').toBeUndefined()
    expect(bodyText()).toContain('Passkeys are not supported in this browser')
  })
})
