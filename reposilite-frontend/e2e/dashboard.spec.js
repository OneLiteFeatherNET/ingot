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
import { siteUrl } from './support/api'

test.describe('instance status on Overview', () => {
  test('shows read-only as a status without a switch control', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/settings'))
    const status = page.getByRole('region', { name: 'Instance status' })
    await expect(status).toBeVisible()
    await expect(status.getByText('Read-only mode', { exact: true })).toBeVisible()
    await expect(page.getByRole('switch', { name: /read-only/i }), 'a switch for the read-only state').toHaveCount(0)
  })

  test('draws the status above At a glance at 1440 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl('/settings'))
    const status = page.getByRole('region', { name: 'Instance status' })
    const glance = page.getByRole('heading', { name: 'At a glance', exact: true })
    await expect(status).toBeVisible()

    const statusTop = await status.evaluate(element => element.getBoundingClientRect().top)
    const glanceTop = await glance.evaluate(element => element.getBoundingClientRect().top)
    expect(statusTop, 'the status comes above At a glance').toBeLessThan(glanceTop)
  })

  test('shows no used value above its limit', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/settings'))
    await expect(page.getByRole('region', { name: 'Instance status' })).toBeVisible()

    // The meters come with the figures of the status, which load after the region is shown: wait for the first one.
    await expect(page.locator('meter, [role="meter"]').first()).toBeVisible()

    // A meter is a <meter> or an element with role meter. Both carry their value and limit, the ARIA way for the role.
    const meters = await page.locator('meter, [role="meter"]').evaluateAll(elements => elements.map(element => ({
      label: element.getAttribute('aria-label'),
      value: Number(element.getAttribute('aria-valuenow') ?? element.getAttribute('value')),
      max: Number(element.getAttribute('aria-valuemax') ?? element.getAttribute('max')),
    })))
    expect(meters.length, 'the meters of the dashboard').toBeGreaterThan(0)
    const above = meters.filter(meter => meter.value > meter.max)
    expect(above, 'the cards whose used value is above the limit').toEqual([])
  })
})
