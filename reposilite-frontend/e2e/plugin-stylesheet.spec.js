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

import { createHash, randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { siteUrl } from './support/api.js'
import { buildStylePlugin, PLUGIN_NAME } from './support/plugin-fixture.js'
import { resolveJar, startServer } from './support/server.js'
import { signInAsAdmin } from './support/session.js'

/*
 * A plugin that ships a stylesheet, end to end: the shaded server loads the fixture plugin from its plugins directory,
 * the dashboard reads the manifest, injects the link and applies the rules. The shared server of the run carries the
 * fixture; a second server with INGOT_LOCAL_PLUGINSTYLES=false checks that the switch removes everything.
 */

const SHEET = readFileSync(new URL('./support/fixtures/e2e-style/dashboard.css', import.meta.url), 'utf8')
const WRAPPED = `@layer ingot-plugins {\n${SHEET}\n}\n`

// The path the server derives from the served bytes: the first 16 hex characters of their SHA-256.
const expectedPath = () => {
  const digest = createHash('sha256').update(WRAPPED).digest('hex')
  return `/api/frontend/plugin-styles/${PLUGIN_NAME}/${digest.slice(0, 16)}.css`
}

const secret = () => randomBytes(24).toString('base64url')

// The brand of the navigation rail, which the fixture restyles. It is found by its accessible name.
const railBrand = (page) => page.getByRole('link', { name: /, start page$/ })

const manifestOf = (page) => page.evaluate(async () => (await fetch('/api/frontend/plugin-ui', { credentials: 'include' })).json())

const pluginLinksOf = (page) => page.evaluate(() => [...document.head.children]
  .filter(element => element.hasAttribute('data-ingot-plugin-style'))
  .map(element => element.getAttribute('href')))

test.describe('plugin stylesheet on the shared server', () => {
  test('lists the stylesheet of the plugin in the manifest for a signed in administrator', async ({ page }) => {
    // when: the administrator signs in and the manifest is read
    await signInAsAdmin(page)
    const manifest = await manifestOf(page)

    // then: the plugin is loaded, and its one stylesheet is listed with the path of its bytes and a SHA-256 digest
    expect(manifest.enabledPlugins, 'enabled plugins').toContain(PLUGIN_NAME)
    expect(manifest.stylesheets, 'stylesheets').toHaveLength(1)
    expect(manifest.stylesheets[0].plugin, 'plugin').toBe(PLUGIN_NAME)
    expect(manifest.stylesheets[0].path, 'path').toBe(expectedPath())
    expect(manifest.stylesheets[0].sha256, 'sha256').toMatch(/^[0-9a-f]{64}$/)
  })

  test('serves the wrapped sheet with the immutable cache policy and an ETag, without sign in', async ({ request }) => {
    // when: the path is requested without any credentials
    const response = await request.get(siteUrl(expectedPath()))

    // then: the body is the wrapped sheet, and the headers carry the content type, the policy and the hash
    expect(response.status(), 'status').toBe(200)
    expect(response.headers()['content-type'], 'content type').toBe('text/css; charset=utf-8')
    expect(response.headers()['cache-control'], 'cache policy').toBe('public, max-age=31536000, immutable')
    expect(response.headers().etag, 'ETag').toBe(`"${expectedPath().split('/').pop().slice(0, 16)}"`)
    expect(await response.text(), 'body').toBe(WRAPPED)
  })

  test('injects the link as the last element of the head, after the dashboard styles', async ({ page }) => {
    // when: the administrator signs in and the dashboard has applied the manifest
    await signInAsAdmin(page)
    await expect.poll(() => pluginLinksOf(page), { message: 'plugin link' }).toEqual([expectedPath()])

    // then: the plugin link is the last child of the head, and the dashboard's own stylesheets come before it
    const head = await page.evaluate(() => [...document.head.children].map(element => ({
      tag: element.tagName,
      rel: element.getAttribute('rel'),
      plugin: element.getAttribute('data-ingot-plugin-style'),
      href: element.getAttribute('href'),
    })))
    const last = head.at(-1)
    expect(last.tag, 'last element').toBe('LINK')
    expect(last.rel, 'rel').toBe('stylesheet')
    expect(last.plugin, 'plugin attribute').toBe(PLUGIN_NAME)
    const before = head.slice(0, -1)
    const appStyles = before.filter(element => element.tag === 'STYLE' || (element.tag === 'LINK' && element.rel === 'stylesheet'))
    expect(appStyles.length, 'dashboard stylesheets before the plugin link').toBeGreaterThan(0)
  })

  test('applies the plugin rule to the rail brand, over the utility class that sets its radius', async ({ page }) => {
    // when: the administrator signs in
    await signInAsAdmin(page)
    const brand = railBrand(page)
    await expect(brand, 'rail brand').toBeVisible()

    // then: the outline of the rule is drawn, and the radius of the utility class is overridden
    await expect.poll(() => brand.evaluate(element => getComputedStyle(element).outlineStyle), { message: 'outline style' })
      .toBe('dashed')
    expect(await brand.evaluate(element => getComputedStyle(element).borderRadius), 'border radius').toBe('0px')
  })

  test('keeps the colour token of the dashboard, which the dashboard declares unlayered', async ({ page }) => {
    // given: the plugin rule is applied, so the test does not pass while the sheet is missing
    await signInAsAdmin(page)
    await expect.poll(() => railBrand(page).evaluate(element => getComputedStyle(element).outlineStyle)).toBe('dashed')

    // when: the primary colour token is read from the root element
    const token = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--md-sys-color-primary').trim())

    // then: the value is the dashboard's own, not the value the plugin rule names
    expect(token, 'primary token').not.toBe('')
    expect(token, 'primary token').not.toBe('rgb(255 0 255)')
    expect(token, 'primary token').not.toBe('#ff00ff')
  })
})

test.describe('plugin stylesheet with INGOT_LOCAL_PLUGINSTYLES=false', () => {
  const admin = { username: 'e2e-off-operator', password: secret() }
  let server
  let stylePlugin

  test.beforeAll(async () => {
    stylePlugin = buildStylePlugin(resolveJar())
    server = await startServer({
      tokenName: 'e2e-off',
      tokenSecret: secret(),
      plugins: [stylePlugin.jar],
      env: { INGOT_LOCAL_PLUGINSTYLES: 'false' },
    })
    // The first administrator is created the way an operator creates it, as in global-setup.js.
    const setupCode = await server.setupCodeFrom()
    const response = await fetch(`${server.baseUrl}/api/auth/setup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ setupCode, username: admin.username, password: admin.password, displayName: 'Switch off' }),
    })
    if (!response.ok) {
      throw new Error(`completing the setup answered ${response.status}: ${await response.text()}`)
    }
  })

  test.afterAll(async () => {
    await server?.stop()
    stylePlugin?.dispose()
  })

  test('lists no stylesheet and injects no link, while the plugin itself is loaded', async ({ page }) => {
    // given: the manifest response is awaited before the assertions, so the absence is not checked too early
    const manifestLoaded = page.waitForResponse(response => response.url().endsWith('/api/frontend/plugin-ui') && response.ok())

    // when: the administrator signs in on this server
    await page.goto(`${server.baseUrl}/`)
    await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Sign in' })
    await dialog.getByLabel('Username', { exact: true }).fill(admin.username)
    await dialog.getByLabel('Password', { exact: true }).fill(admin.password)
    await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
    await manifestLoaded
    const manifest = await manifestOf(page)

    // then: the plugin is still loaded, its stylesheet is not listed, and no plugin link is in the head
    expect(manifest.enabledPlugins, 'enabled plugins').toContain(PLUGIN_NAME)
    expect(manifest.stylesheets, 'stylesheets').toEqual([])
    expect(await pluginLinksOf(page), 'plugin links').toEqual([])
  })

  test('answers 404 on the stylesheet address', async ({ request }) => {
    // when: the address that the sheet would have is requested
    const response = await request.get(new URL(expectedPath(), server.baseUrl).toString())

    // then: the address is not found
    expect(response.status(), 'status').toBe(404)
  })
})
