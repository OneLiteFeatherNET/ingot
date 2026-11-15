import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SettingsEncryptionAlert from './SettingsEncryptionAlert.vue'

// The client is swapped per test through this holder. The alert reads it through the session.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

const clientReturning = (state) => ({
  settings: {
    encryptionState: async () => ({ data: state })
  }
})

let wrapper

const mountAlert = async (client) => {
  session.client = client
  wrapper = mount(SettingsEncryptionAlert)
  await flushPromises()
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  session.client = null
})

describe('SettingsEncryptionAlert', () => {
  it('warns while secret values are stored in clear and no key is active', async () => {
    const view = await mountAlert(clientReturning({ mode: 'optional', secretsEncrypted: false, plaintextSecrets: 2 }))

    expect(view.find('[role="alert"]').exists()).toBe(true)
    expect(view.text()).toContain('2')
    expect(view.text()).toContain('INGOT_SETTINGS_KEY')
  })

  it('names the count and the key setting only, never a value', async () => {
    const view = await mountAlert(clientReturning({ mode: 'optional', secretsEncrypted: false, plaintextSecrets: 2 }))

    expect(view.text()).not.toMatch(/secret-|password=|enc:v1:/)
  })

  it('stays hidden when a key is active', async () => {
    const view = await mountAlert(clientReturning({ mode: 'optional', secretsEncrypted: true, plaintextSecrets: 2 }))

    expect(view.find('[role="alert"]').exists()).toBe(false)
  })

  it('stays hidden when no secret value is in clear', async () => {
    const view = await mountAlert(clientReturning({ mode: 'optional', secretsEncrypted: false, plaintextSecrets: 0 }))

    expect(view.find('[role="alert"]').exists()).toBe(false)
  })

  it('stays hidden without the settings permission and shows no error', async () => {
    const forbidden = {
      settings: {
        encryptionState: async () => {
          throw Object.assign(new Error('Forbidden'), { response: { status: 403 } })
        }
      }
    }
    const view = await mountAlert(forbidden)

    expect(view.find('[role="alert"]').exists()).toBe(false)
    expect(view.text()).toBe('')
  })
})
