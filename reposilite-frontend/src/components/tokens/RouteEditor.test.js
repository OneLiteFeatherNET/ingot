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
import RouteEditor from './RouteEditor.vue'

/*
 * The route picker names a repository from the list the server has saved, so a route cannot point at
 * a repository through a typo. A custom path is still possible for a sub-path, and it is checked by
 * its first segment only.
 */

const REPOSITORIES = ['releases', 'snapshots']

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const editor = (props = {}) => {
  wrapper = mount(RouteEditor, {
    props: {
      path: '',
      read: true,
      write: false,
      submitLabel: 'Save',
      error: null,
      repositories: REPOSITORIES,
      ...props
    },
    attachTo: document.body
  })
  return wrapper
}

const picker = () => wrapper.find('select')
const pathField = () => wrapper.find('input[name="route-path"]')
const optionLabels = () => wrapper.findAll('option').map(option => option.text())
const warning = () => wrapper.find('[role="status"]')
const selectedLabel = () => picker().element.selectedOptions[0].textContent.trim()
// The picker takes an option by its value; a test names the option it means by the label the user sees.
const chooseOption = async (label) => {
  const option = wrapper.findAll('option').find(entry => entry.text() === label)
  await picker().setValue(option.element.value)
}

describe('RouteEditor route picker', () => {
  it('offers each saved repository and a custom path', () => {
    editor()

    expect(optionLabels()).toEqual(['Choose a repository', 'releases', 'snapshots', 'Custom path'])
  })

  it('fills the route with the chosen repository, so the path cannot be mistyped', async () => {
    editor()

    await picker().setValue('snapshots')

    expect(wrapper.emitted('update:path')).toEqual([['/snapshots']])
    expect(pathField().exists()).toBe(false)
  })

  it('shows the repository of an existing route as the choice, without a path field', () => {
    editor({ path: '/releases' })

    expect(selectedLabel()).toBe('releases')
    expect(pathField().exists()).toBe(false)
  })

  it('shows the full path, and the custom choice, for a route below a repository', () => {
    editor({ path: '/releases/com/example' })

    expect(selectedLabel()).toBe('Custom path')
    expect(pathField().element.value).toBe('/releases/com/example')
  })

  it('opens a path field when Custom path is chosen, and reports what is typed', async () => {
    editor()

    await chooseOption('Custom path')
    expect(pathField().exists()).toBe(true)

    await pathField().setValue('/snapshots/dev')
    expect(wrapper.emitted('update:path').at(-1)).toEqual(['/snapshots/dev'])
  })

  it('keeps the custom path field open while it is typed, even once the text names a repository', async () => {
    editor()
    await chooseOption('Custom path')

    await wrapper.setProps({ path: '/snapshots' })

    expect(pathField().exists()).toBe(true)
    expect(selectedLabel()).toBe('Custom path')
  })

  it('warns when a custom path starts with a name that is not a repository', async () => {
    editor({ path: '/relaeses/com' })

    expect(warning().text()).toContain('There is no repository "relaeses"')
  })

  it('does not warn about a custom path whose first segment is a saved repository', () => {
    editor({ path: '/snapshots/dev' })

    expect(warning().exists()).toBe(false)
  })

  it('warns about a route whose repository was deleted, since it matches nothing', () => {
    editor({ path: '/retired/artifacts' })

    expect(warning().text()).toContain('There is no repository "retired"')
  })

  it('does not warn and keeps a plain path field while the repositories are not loaded', () => {
    editor({ path: '/relaeses', repositories: null })

    expect(picker().exists()).toBe(false)
    expect(pathField().exists()).toBe(true)
    expect(warning().exists()).toBe(false)
  })
})
