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

import { test, expect } from './support/fixtures.js'
import { siteUrl } from './support/api.js'

const ARTIFACT_DIRECTORY = 'com/example/demo/1.0'
const ARTIFACT_FILE = 'demo-1.0.txt'
const ARTIFACT_BYTES = 'uploaded through the repository protocol\n'

test.describe('browse a repository', () => {
  test('downloads a file that was uploaded through the repository protocol', async ({ page, seed }) => {
    const repository = await seed.repository('browse')
    await seed.upload(repository, `${ARTIFACT_DIRECTORY}/${ARTIFACT_FILE}`, ARTIFACT_BYTES)

    await page.goto(siteUrl(`/browse/${repository}/${ARTIFACT_DIRECTORY}`))
    // The listing names a file with its size and date as well, so the name is matched by its start.
    const file = page.getByRole('link', { name: new RegExp(`^${ARTIFACT_FILE}\\b`) })
    await expect(file).toBeVisible()

    // The link points at the file itself: following it is the download, and the bytes are the uploaded ones.
    const href = await file.getAttribute('href')
    const response = await page.request.get(href)
    expect(response.status(), 'the status of the download').toBe(200)
    expect(await response.text(), 'the downloaded bytes').toBe(ARTIFACT_BYTES)
  })

  test('lists a file uploaded through the repository protocol when the browser opens the repository', async ({ page, seed }) => {
    // Upload first, then browse: the page is opened after the file exists, so no reload is involved.
    const repository = await seed.repository('upload-then-browse')
    await seed.upload(repository, 'uploaded-first.txt', 'listed on the first visit\n')

    await page.goto(siteUrl(`/browse/${repository}`))
    await expect(page.getByRole('link', { name: /^uploaded-first\.txt\b/ }), 'the uploaded file in the listing').toBeVisible()
  })
})
