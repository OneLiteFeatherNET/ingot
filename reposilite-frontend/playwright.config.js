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

import { defineConfig, devices } from '@playwright/test'

// The browser suite of the dashboard. It starts the shaded server jar itself (e2e/support/global-setup.js),
// so it needs the jar to be built first: ./gradlew :reposilite-backend:shadowJar
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.js',
  globalSetup: './e2e/support/global-setup.js',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  // A test that needs a retry to pass is a failing test, locally and in CI.
  retries: 0,
  workers: 2,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    locale: 'en-US',
    timezoneId: 'UTC',
  },
  // Chromium only. The settings writes run as a second project after the first one: a save on the Repositories
  // page conflicts with the repositories that other specs seed in parallel (see repositories-crud.spec.js).
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: '**/repositories-crud.spec.js',
    },
    {
      name: 'chromium-settings-writes',
      testMatch: '**/repositories-crud.spec.js',
      dependencies: ['chromium'],
      fullyParallel: false,
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
