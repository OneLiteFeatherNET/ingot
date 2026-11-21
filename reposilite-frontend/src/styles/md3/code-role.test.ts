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

/// <reference types="node" />
import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, type Dirent } from 'node:fs'
import { join, relative } from 'node:path'

// Monospace text takes a code role (md-code-small, md-code-medium) or a code primitive (MdCode, MdCodeBlock), not
// the font-mono class beside a body role. The files below still write font-mono and are migrated one by one; the
// list only shrinks. Reads the sources from disk and mounts nothing.
const PENDING: readonly string[] = Object.freeze([
  'src/components/access/check/CheckPermissionView.vue',
  'src/components/account/SecondFactorsCard.vue',
  'src/components/audit/AuditEventRow.vue',
  'src/components/browser/AdjustmentsModal.vue',
  'src/components/browser/DeleteEntryModal.vue',
  'src/components/browser/FileUpload.vue',
  'src/components/dashboard/FailuresSheet.vue',
  'src/components/identity/SecretOnceDialog.vue',
  'src/components/identity/TokenList.vue',
  'src/components/maintenance/CleanupPoliciesPanel.vue',
  'src/components/maintenance/IndexPanel.vue',
  'src/components/maintenance/RunHistory.vue',
  'src/components/maintenance/StorageUsagePanel.vue',
  'src/components/access/nodes/NodeTreeView.vue',
  'src/components/md/MdListbox.vue',
  'src/components/settings/RepositoryWizard.vue',
  'src/components/support/SystemInfoView.vue',
])

const SOURCE_ROOT = join(process.cwd(), 'src')

const vueFiles = (directory: string): string[] => readdirSync(directory, { withFileTypes: true }).flatMap((entry: Dirent) => {
  const path = join(directory, entry.name)
  if (entry.isDirectory()) return vueFiles(path)
  return entry.name.endsWith('.vue') ? [path] : []
})

const writersOfFontMono = () => vueFiles(SOURCE_ROOT)
  .filter((file) => /\bfont-mono\b/.test(readFileSync(file, 'utf8')))
  .map((file) => relative(process.cwd(), file))

describe('monospace text in the components', () => {
  it('takes a code role or a code primitive, apart from the files still pending', () => {
    const offenders = writersOfFontMono().filter((file) => !PENDING.includes(file))

    expect(offenders, 'use md-code-small, md-code-medium, MdCode or MdCodeBlock instead of font-mono').toEqual([])
  })

  it('lists only pending files that still write font-mono, so a migrated file leaves the list', () => {
    const writers = writersOfFontMono()

    expect(PENDING.filter((file) => !writers.includes(file)), 'remove the migrated file from PENDING').toEqual([])
  })
})
