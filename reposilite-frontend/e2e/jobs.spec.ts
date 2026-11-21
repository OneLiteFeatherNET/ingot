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

import { test, expect } from './support/fixtures'
import { readJob, siteUrl, startIndexRebuild } from './support/api'

/*
 * The Jobs page lists the background runs of every source. The entry, the kind filter and the maintenance links run
 * against the server of this run. Paging and the failure list need more than 50 runs and a run that failed, which
 * the server of this run cannot be made to produce on demand, so those two answer GET /api/jobs from the test.
 */

const JOBS_PAGE_SIZE = 50

const runList = (page) => page.getByRole('list', { name: 'Background runs, newest first', exact: true })
const jobsHeading = (page) => page.getByRole('heading', { name: 'Jobs', level: 2, exact: true })

const isJobsList = (url) => new URL(url).pathname === '/api/jobs'

const stubRun = (localId, overrides = {}) => ({
  id: `index:${localId}`,
  source: 'index',
  kind: 'index.rebuild',
  label: `Stubbed rebuild ${localId}`,
  trigger: 'MANUAL',
  triggerDetail: null,
  state: 'SUCCEEDED',
  startedAt: new Date(Date.UTC(2026, 0, 1) - localId * 60000).toISOString(),
  finishedAt: new Date(Date.UTC(2026, 0, 1) - localId * 60000 + 1000).toISOString(),
  durationMs: 1000,
  counts: [],
  failures: [],
  failuresTruncated: false,
  ...overrides,
})

const stubPage = (items, nextCursor = null) => ({
  items,
  nextCursor,
  sourceErrors: [],
  kinds: ['index.rebuild', 'index.verify'],
})

test.describe('the Jobs page', () => {
  test('opens from the Jobs entry of the Support group', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/settings'))
    const tree = page.getByRole('navigation', { name: 'Administration', exact: true })

    await tree.getByRole('link', { name: 'Jobs', exact: true }).click()

    await expect(page, 'the entry opens the Jobs address').toHaveURL(/\/settings\/jobs$/)
    await expect(jobsHeading(page), 'the Jobs page is drawn').toBeVisible()
  })

  test('lists index runs only when the address filters on the index kind', async ({ adminPage: page, seed }) => {
    const repository = await seed.repository('jobs')
    const runId = await startIndexRebuild([repository])
    // The rebuild of the test's own repository ends before the repository is removed again at the end of the test.
    await expect.poll(async () => (await readJob(`index:${runId}`)).state, 'the state of the seeded rebuild')
      .not.toBe('RUNNING')

    const listed = page.waitForRequest(request => isJobsList(request.url()))
    await page.goto(siteUrl('/settings/jobs?kind=index'))

    expect(new URL((await listed).url()).searchParams.get('kind'), 'the kind filter sent to the server').toBe('index')
    await expect(runList(page).locator(`[data-job-id="index:${runId}"]`), 'the seeded rebuild is listed').toBeVisible()
    const kinds = await runList(page).getByRole('listitem').getByRole('paragraph').allInnerTexts()
    expect(kinds.filter(text => !/^index\.(rebuild|verify),/.test(text)), 'rows of a kind other than index').toEqual([])
  })

  test('adds the next page of runs with Load more', async ({ adminPage: page }) => {
    const firstPages = []
    await page.route(isJobsList, async (route) => {
      const cursor = new URL(route.request().url()).searchParams.get('cursor')
      if (cursor === null) {
        firstPages.push(route.request().url())
        const items = Array.from({ length: JOBS_PAGE_SIZE }, (_, index) => stubRun(index + 1))
        await route.fulfill({ json: stubPage(items, 'next-page') })
        return
      }
      const items = Array.from({ length: 3 }, (_, index) => stubRun(JOBS_PAGE_SIZE + index + 1))
      await route.fulfill({ json: stubPage(items) })
    })

    await page.goto(siteUrl('/settings/jobs'))
    await expect(runList(page).getByRole('listitem'), 'the first page').toHaveCount(JOBS_PAGE_SIZE)

    await page.getByRole('button', { name: 'Load more', exact: true }).click()

    await expect(runList(page).getByRole('listitem'), 'the first page and the next one').toHaveCount(JOBS_PAGE_SIZE + 3)
    await expect(page.getByRole('link', { name: `Stubbed rebuild ${JOBS_PAGE_SIZE + 3}`, exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Load more', exact: true }), 'no further page is offered').toHaveCount(0)
    expect(firstPages, 'requests of the first page').toHaveLength(1)
  })

  test('opens the detail of a failed run and lists its failures', async ({ adminPage: page }) => {
    const failed = stubRun(7, {
      label: 'Failed rebuild',
      state: 'FAILED',
      failures: [{ message: 'releases: storage could not be read' }, { message: 'snapshots: storage could not be read' }],
      failuresTruncated: true,
    })
    await page.route(isJobsList, route => route.fulfill({ json: stubPage([failed]) }))
    await page.route(url => new URL(url).pathname === `/api/jobs/${encodeURIComponent(failed.id)}`,
      route => route.fulfill({ json: failed }))

    await page.goto(siteUrl('/settings/jobs'))
    await page.getByRole('link', { name: 'Failed rebuild', exact: true }).click()

    await expect(page, 'the detail has its own address').toHaveURL(/\/settings\/jobs\/index(:|%3A)7$/)
    await expect(page.getByRole('heading', { name: 'Failed rebuild', level: 2, exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Failures', level: 3, exact: true })).toBeVisible()
    await expect(page.getByText('releases: storage could not be read', { exact: true })).toBeVisible()
    await expect(page.getByText('snapshots: storage could not be read', { exact: true })).toBeVisible()
    await expect(page.getByText('Only the first 50 failures are listed.', { exact: true }), 'the truncation note').toBeVisible()
  })
})

test.describe('the maintenance tabs', () => {
  for (const { tab, kind } of [{ tab: 'index', kind: 'index' }, { tab: 'cleanup', kind: 'cleanup' }]) {
    test(`link All runs on the ${tab} tab to the Jobs page filtered on ${kind}`, async ({ adminPage: page }) => {
      await page.goto(siteUrl(`/maintenance?tab=${tab}`))

      await page.getByRole('link', { name: 'All runs', exact: true }).click()

      await expect(page, 'the link opens the filtered Jobs address').toHaveURL(new RegExp(`/settings/jobs\\?kind=${kind}$`))
      await expect(jobsHeading(page), 'the Jobs page is drawn').toBeVisible()
    })
  }
})
