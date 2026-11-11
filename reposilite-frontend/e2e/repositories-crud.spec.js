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

/*
 * Runs in its own project, after every other spec (see playwright.config.js). Saving on the Repositories page
 * writes the whole repository settings document, and the other specs seed repositories by writing that same
 * document. Run side by side, a save loses the race and the server answers with a conflict, so this spec runs
 * alone, once the seeding specs have finished.
 */

import { test, expect } from './support/fixtures.js'
import { listRepositoryIds, readRepository, siteUrl } from './support/api.js'

const EDITED_DESCRIPTION = 'Edited by the browser suite'

test.describe('repositories page writes', () => {
  test.describe.configure({ mode: 'serial' })

  test('creates, edits and deletes a repository', async ({ adminPage: page, seed }) => {
    const repository = seed.name('crud')
    seed.track(repository)

    await page.goto(siteUrl('/settings/repositories'))
    await page.getByRole('button', { name: 'New repository', exact: true }).click()
    const wizard = page.getByRole('dialog', { name: 'New repository' })
    await wizard.getByRole('button', { name: 'Next', exact: true }).click()
    await wizard.getByRole('textbox', { name: 'Id', exact: true }).fill(repository)
    await wizard.getByRole('button', { name: 'Next', exact: true }).click()
    await wizard.getByRole('button', { name: 'Next', exact: true }).click()
    await wizard.getByRole('radio', { name: /Skip for now/ }).check()
    await wizard.getByRole('button', { name: 'Next', exact: true }).click()
    await wizard.getByRole('button', { name: 'Create', exact: true }).click()
    await expect(page.getByText(`Repository ${repository} is ready.`), 'the wizard confirms the new repository').toBeVisible()
    await wizard.getByRole('button', { name: 'Done', exact: true }).click()
    await page.goto(siteUrl('/settings/repositories'))
    await expect(page.getByRole('row', { name: new RegExp(repository) }), 'the new repository in the list').toBeVisible()

    // Edits are drafts until Save: the save bar appears with the first change and writes it.
    await page.getByRole('button', { name: `Actions for ${repository}`, exact: true }).click()
    await page.getByRole('menuitem', { name: `Edit ${repository}`, exact: true }).click()
    await page.getByRole('textbox', { name: 'Description', exact: true }).fill(EDITED_DESCRIPTION)
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Save', exact: true }), 'the save bar after saving').toBeHidden()
    await expect.poll(async () => (await readRepository(repository)).description, 'the stored description').toBe(EDITED_DESCRIPTION)

    // Deleting asks for the id, and the removal is a draft until Save as well.
    await page.goto(siteUrl('/settings/repositories'))
    await page.getByRole('button', { name: `Actions for ${repository}`, exact: true }).click()
    await page.getByRole('menuitem', { name: `Delete ${repository}`, exact: true }).click()
    const confirm = page.getByRole('dialog').filter({ hasText: `Delete ${repository}?` })
    await confirm.getByRole('textbox', { name: 'Id', exact: true }).fill(repository)
    await confirm.getByRole('button', { name: 'Delete', exact: true }).click()
    await expect(page.getByText(`${repository} deleted, not saved yet`)).toBeVisible()
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByRole('row', { name: new RegExp(repository) }), 'the row after saving the delete').toHaveCount(0)
    expect(await listRepositoryIds(), 'the repositories of the server').not.toContain(repository)
  })
})
