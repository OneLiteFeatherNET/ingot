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

import { test as base, expect } from '@playwright/test'
import { createRepository, deleteRepository, uploadFile } from './api.js'
import { scopedName } from './names.js'
import { signInAsAdmin } from './session.js'

/*
 * The test object of this suite. Every test gets:
 *
 * - `seed`: creates repositories and uploads files through the public REST API. Each repository the
 *   test creates is removed when the test ends, whether it passed or not. The ids come from the
 *   test title and the worker index (see names.js), so no two tests share a repository.
 * - `adminPage`: a page signed in as the instance admin of this run, with the password account of
 *   the setup (see session.js).
 */
const test = base.extend({
  seed: async ({}, use, testInfo) => {
    const created = new Set()
    const create = async (id, options) => {
      created.add(id)
      await createRepository(id, options)
      return id
    }
    await use({
      name: (label) => scopedName(testInfo, label),
      create,
      repository: (label, options) => create(scopedName(testInfo, label), options),
      track: (id) => created.add(id),
      upload: (id, path, body) => uploadFile(id, path, body),
    })
    for (const id of created) {
      await deleteRepository(id)
    }
  },
  adminPage: async ({ page }, use) => {
    await signInAsAdmin(page)
    await use(page)
  },
})

export { test, expect }
