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

test.describe('dashboard status', () => {
  test('shows read-only as a status without a switch control', async ({ adminPage: page }) => {
    // Known regression on main: the read-only state is a switch, although it is only a status. The fixing change
    // is fix-dashboard-status, which removes this marker.
    test.fail(true, 'fix-dashboard-status')
    await page.goto(siteUrl('/dashboard'))
    const status = page.getByRole('region', { name: 'Instance status' })
    await expect(status).toBeVisible()
    await expect(status.getByText('Read-only mode', { exact: true })).toBeVisible()
    await expect(page.getByRole('switch', { name: /read-only/i }), 'a switch for the read-only state').toHaveCount(0)
  })

  test('shows no used value above its limit', async ({ adminPage: page }) => {
    // Known regression on main: the threads card shows 32 used of a pool of 25. The fixing change is
    // fix-dashboard-status, which removes this marker.
    test.fail(true, 'fix-dashboard-status')
    await page.goto(siteUrl('/dashboard'))
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
