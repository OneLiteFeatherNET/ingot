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
import type { VueWrapper } from '@vue/test-utils'
import prettyBytes from 'pretty-bytes'
import DryRunResult from './DryRunResult.vue'
import { normalizeDryRun } from './cleanup'
import type { CleanupPreview } from './cleanup'

let wrapper: VueWrapper

const preview = (overrides = {}) => normalizeDryRun({
  policyId: 'snapshots-weekly',
  evaluatedAt: '2026-10-15T10:00:00Z',
  count: 2,
  totalBytes: 2048,
  deletions: [
    { repository: 'snapshots', path: 'net/x/app/1.0-SNAPSHOT', version: '1.0-SNAPSHOT', sizeBytes: 1024, reason: 'beyond the newest 3 versions; older than 30 days' },
    { repository: 'snapshots', path: 'net/x/app/1.1-SNAPSHOT', version: '1.1-SNAPSHOT', sizeBytes: 1024, reason: 'older than 30 days' }
  ],
  warnings: ["repository 'x' has no mirror that stores artifacts"],
  ...overrides
})

const mountResult = (props: { preview: CleanupPreview }) => {
  wrapper = mount(DryRunResult, { props }) as VueWrapper
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
})

describe('DryRunResult', () => {
  it('shows the count and the total size of what would be deleted', () => {
    mountResult({ preview: preview() })

    expect(wrapper.text()).toContain('2 artifacts')
    expect(wrapper.text()).toContain(prettyBytes(2048))
  })

  it('shows one table row per deletion with path, version, size and reason', () => {
    mountResult({ preview: preview() })

    const rows = wrapper.findAll('tbody tr')

    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('net/x/app/1.0-SNAPSHOT')
    expect(rows[0].text()).toContain('1.0-SNAPSHOT')
    expect(rows[0].text()).toContain(prettyBytes(1024))
    expect(rows[0].text()).toContain('beyond the newest 3 versions; older than 30 days')
  })

  it('keeps the table caption in the DOM but visually hidden', () => {
    mountResult({ preview: preview() })

    const caption = wrapper.find('caption')

    expect(caption.text()).toBe('Artifacts a run would delete')
    expect(caption.classes(), 'the caption is for screen readers only').toContain('sr-only')
  })

  it('keeps the table header pinned to the top of the scrolling table', () => {
    mountResult({ preview: preview() })

    const headers = wrapper.findAll('thead th')

    expect(headers).toHaveLength(5)
    headers.forEach((header) => {
      expect(header.classes(), `the ${header.text()} header is sticky`).toContain('sticky')
      expect(header.classes(), `the ${header.text()} header sticks to the top`).toContain('top-0')
    })
  })

  it('aligns the size column to the end, in its header and its cells', () => {
    mountResult({ preview: preview() })

    const sizeHeader = wrapper.findAll('thead th').find((header) => header.text() === 'Size')
    const sizeCell = wrapper.find('tbody tr').findAll('td').find((cell) => cell.text() === prettyBytes(1024))

    expect(sizeHeader?.classes(), 'the Size header is end-aligned').toContain('text-end')
    expect(sizeCell?.classes(), 'the size cell is end-aligned').toContain('text-end')
  })

  it('lists the warnings the server returned', () => {
    mountResult({ preview: preview() })

    expect(wrapper.text()).toContain("repository 'x' has no mirror that stores artifacts")
  })

  it('says that nothing would be deleted when the preview is empty', () => {
    mountResult({ preview: preview({ count: 0, totalBytes: 0, deletions: [] }) })

    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.text()).toContain('Nothing would be deleted.')
  })

  it('shows the warnings even when nothing would be deleted', () => {
    mountResult({ preview: preview({ count: 0, totalBytes: 0, deletions: [] }) })

    expect(wrapper.text()).toContain("repository 'x' has no mirror that stores artifacts")
  })
})
