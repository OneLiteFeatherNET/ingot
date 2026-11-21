import { test, expect } from './support/fixtures'
import { siteUrl } from './support/api'
import { signInAsAdmin } from './support/session'

// The first step of the nexus import wizard, Connect, walked without a Nexus behind it. The suite makes no network
// calls, so the connection test is never sent: the empty submit, the password modes and the width of the form are
// what the browser can check here.
const CONNECT_ADDRESS = '/settings/migration/connect'
// The form is capped at the reading width of the page, max-w-xl, which is 36 rem at 16 pixels.
const READING_WIDTH = 576

test.describe('nexus import wizard, the connect step', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page)
  })

  test('opens on the connect step, which the step list marks as the current step', async ({ page }) => {
    await page.goto(siteUrl(CONNECT_ADDRESS))

    await expect(page.getByRole('heading', { name: 'Connect', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '1. Connect', exact: true })).toHaveAttribute('aria-current', 'step')
  })

  test('names each empty field in the error summary when the connection is tested without them', async ({ page }) => {
    await page.goto(siteUrl(CONNECT_ADDRESS))
    await page.getByRole('button', { name: 'Test connection', exact: true }).click()

    const summary = page.locator('#migration-connect-errors')
    await expect(summary).toContainText('Fix the fields below before the connection can be tested.')
    await expect(summary.getByRole('link', { name: 'Enter the Nexus URL.', exact: true })).toBeVisible()
    await expect(summary.getByRole('link', { name: 'Enter the user name.', exact: true })).toBeVisible()
    await expect(summary.getByRole('link', { name: 'Enter the password, or use a secret reference.', exact: true })).toBeVisible()
  })

  test('moves the focus to the field that an error summary link names', async ({ page }) => {
    await page.goto(siteUrl(CONNECT_ADDRESS))
    await page.getByRole('button', { name: 'Test connection', exact: true }).click()

    await page.locator('#migration-connect-errors').getByRole('link', { name: 'Enter the Nexus URL.', exact: true }).click()

    await expect(page.getByLabel('Nexus URL', { exact: true })).toBeFocused()
  })

  test('asks for a secret reference instead of a password when that mode is chosen', async ({ page }) => {
    await page.goto(siteUrl(CONNECT_ADDRESS))
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible()

    await page.getByLabel('Use a secret reference', { exact: true }).check()

    await expect(page.getByLabel('Secret reference', { exact: true })).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toHaveCount(0)

    await page.getByRole('button', { name: 'Test connection', exact: true }).click()
    await expect(page.locator('#migration-connect-errors').getByRole('link', { name: 'Enter the secret reference.', exact: true })).toBeVisible()
  })

  test('keeps the connect form within the reading width', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl(CONNECT_ADDRESS))

    const form = page.locator('#migration-connect-form')
    await expect(form).toBeVisible()
    const box = await form.boundingBox()

    expect(box!.width, 'the form is no wider than the reading width at 1440 pixels').toBeLessThanOrEqual(READING_WIDTH)
  })
})
