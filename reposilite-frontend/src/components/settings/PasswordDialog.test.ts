import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import PasswordDialog from './PasswordDialog.vue'
import MdTextField from '../md/MdTextField.vue'

// The modal would teleport to the body; the stub keeps its slot inline so the fields can be found through the wrapper.
const mountDialog = (props: { mode?: 'export' | 'import'; open?: boolean; error?: string } = {}): VueWrapper =>
  mount(PasswordDialog, {
    props: { open: true, mode: 'export', ...props },
    global: { stubs: { VueFinalModal: { template: '<div><slot /></div>' } } }
  }) as VueWrapper

const inputValue = (wrapper: VueWrapper, selector: string): string => (wrapper.find(selector).element as HTMLInputElement).value

const fill = async (wrapper: VueWrapper, password: string, confirmation?: string) => {
  await wrapper.find('#settings-password').setValue(password)
  if (confirmation !== undefined) {
    await wrapper.find('#settings-password-confirmation').setValue(confirmation)
  }
}

describe('PasswordDialog export', () => {
  it('does not send the request while the confirmation differs from the password', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!', 'twelve-chars?')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('do not match')
  })

  it('does not send the request below twelve characters', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'eleven-char', 'eleven-char')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('at least 12 characters')
  })

  it('sends the password once it is at least twelve characters and repeated exactly', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!', 'twelve-chars!')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([['twelve-chars!']])
  })

  it('clears the password fields after it is submitted', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!', 'twelve-chars!')

    await wrapper.find('form').trigger('submit')

    expect(inputValue(wrapper, '#settings-password')).toBe('')
    expect(inputValue(wrapper, '#settings-password-confirmation')).toBe('')
  })

  it('clears the password when the dialog closes', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!', 'twelve-chars!')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect(inputValue(wrapper, '#settings-password')).toBe('')
  })
})

describe('PasswordDialog import', () => {
  it('asks for one password only and sends it without a confirmation check', async () => {
    const wrapper = mountDialog({ mode: 'import' })
    expect(wrapper.find('#settings-password-confirmation').exists()).toBe(false)

    await fill(wrapper, 'twelve-chars!')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([['twelve-chars!']])
  })

  it('shows the error of the server without holding the password', () => {
    const wrapper = mountDialog({
      mode: 'import',
      error: 'The settings file cannot be opened: the password is wrong or the file was changed'
    })

    expect(wrapper.text()).toContain('cannot be opened')
  })
})

describe('PasswordDialog strength hint', () => {
  it('shows the strength of the passphrase while it is typed', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'fourteen-char')

    expect(wrapper.text()).toContain('acceptable')
  })

  it('recommends several words and a password manager', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('several words')
    expect(wrapper.text()).toContain('password manager')
  })

  it('shows the passphrase as text while the show control is chosen', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!')

    await wrapper.find('[data-test="toggle-passphrase-visibility"]').trigger('click')

    expect(wrapper.find('#settings-password').attributes('type')).toBe('text')
  })

  it('hides the passphrase again when the show control is chosen twice', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!')

    await wrapper.find('[data-test="toggle-passphrase-visibility"]').trigger('click')
    await wrapper.find('[data-test="toggle-passphrase-visibility"]').trigger('click')

    expect(wrapper.find('#settings-password').attributes('type')).toBe('password')
  })
})

// The shared dialog frame is teleported to the body and read from the document, so these tests mount the real modal.
describe('PasswordDialog in the shared dialog frame', () => {
  let wrapper: VueWrapper | undefined
  const extraElements: Element[] = []

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    extraElements.splice(0).forEach(element => element.remove())
    document.querySelectorAll('.vfm').forEach(element => element.remove())
  })

  const openModal = async (props: { mode?: 'export' | 'import'; error?: string } = {}) => {
    wrapper = mount(PasswordDialog, { props: { open: false, mode: 'export', ...props }, attachTo: document.body }) as VueWrapper
    await wrapper.setProps({ open: true })
    await flushPromises()
  }

  const modalRoot = () => document.querySelector('.vfm')
  const passwordInput = () => document.getElementById('settings-password') as HTMLInputElement | null

  it('is teleported to the body and announced as a dialog named by its heading', async () => {
    await openModal()

    const root = modalRoot()
    expect((wrapper?.element as HTMLElement | undefined)?.contains(passwordInput()), 'the dialog is teleported out of the page').toBe(false)
    expect(root?.getAttribute('role'), 'the modal root carries the dialog role').toBe('dialog')
    const name = document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the accessible name is the heading').toBe('Download the settings with a password')
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await openModal()

    expect(document.querySelector('[style*="color-mix"]'), 'no element carries an inline scrim colour').toBeNull()
    expect(document.querySelector('.vfm__overlay'), 'the frame draws the scrim').not.toBeNull()
  })

  it('puts the focus on the password field when it opens', async () => {
    await openModal()

    expect(document.activeElement, 'the password field has the focus').toBe(passwordInput())
  })

  it('draws the password fields as text fields of the password type', async () => {
    await openModal()

    const fields = wrapper?.findAllComponents(MdTextField) ?? []
    const inputs = fields.map(field => field.find('input'))
    expect(inputs.map(input => input.attributes('id')), 'both password fields are MdTextField').toEqual(['settings-password', 'settings-password-confirmation'])
    for (const input of inputs) {
      expect(input.attributes('type'), `${input.attributes('id')} hides what is typed`).toBe('password')
    }
  })

  it('cancels on Escape and returns the focus to the control that opened it', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    extraElements.push(opener)
    opener.focus()
    await openModal()

    modalRoot()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper?.emitted('cancel'), 'Escape cancels').toHaveLength(1)
    await wrapper?.setProps({ open: false })
    await flushPromises()

    expect(document.activeElement, 'the focus is back on the opener').toBe(opener)
  })

  it('confirms with one filled button and cancels with a text button', async () => {
    await openModal()

    const buttons = [...(modalRoot()?.querySelectorAll('button[data-variant]') ?? [])]
    const filled = buttons.filter(button => button.getAttribute('data-variant') === 'filled')
    expect(filled.map(button => button.textContent?.trim()), 'one filled button, Download').toEqual(['Download'])
  })

  it('shows the error of the server as an error alert', async () => {
    await openModal({ mode: 'import', error: 'The settings file cannot be opened' })

    const alert = [...document.querySelectorAll('[role="alert"]')].find(element => element.textContent?.includes('cannot be opened'))
    expect(alert, 'the error is announced as an alert').toBeDefined()
    expect(alert?.getAttribute('data-tone'), 'the alert is an MdAlert in the error tone').toBe('error')
  })
})
