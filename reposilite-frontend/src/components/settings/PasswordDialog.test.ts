import { describe, it, expect } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import PasswordDialog from './PasswordDialog.vue'

const mountDialog = (props: { mode?: 'export' | 'import'; open?: boolean; error?: string } = {}): VueWrapper =>
  mount(PasswordDialog, { props: { open: true, mode: 'export', ...props } }) as VueWrapper

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
