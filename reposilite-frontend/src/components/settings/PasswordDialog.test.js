import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PasswordDialog from './PasswordDialog.vue'

const mountDialog = (props = {}) =>
  mount(PasswordDialog, { props: { open: true, mode: 'export', ...props } })

const fill = async (wrapper, password, confirmation) => {
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

    expect(wrapper.find('#settings-password').element.value).toBe('')
    expect(wrapper.find('#settings-password-confirmation').element.value).toBe('')
  })

  it('clears the password when the dialog closes', async () => {
    const wrapper = mountDialog()
    await fill(wrapper, 'twelve-chars!', 'twelve-chars!')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect(wrapper.find('#settings-password').element.value).toBe('')
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

  it('shows the error of the server without holding the password', async () => {
    const wrapper = mountDialog({
      mode: 'import',
      error: 'The settings file cannot be opened: the password is wrong or the file was changed'
    })

    expect(wrapper.text()).toContain('cannot be opened')
  })
})
