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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'

// The store reads the session for its client and token at import time, and it talks to the
// stream through EventSource. Both are replaced so the test runs without a server, a token
// or a browser stream.
const session = vi.hoisted(() => ({ client: null, token: null }))
vi.mock('../session.js', () => ({ useSession: () => session }))
vi.mock('../client', () => ({ createURL: (path) => `http://localhost${path}` }))
vi.mock('../../helpers/toast', () => ({
  createErrorToast: vi.fn(),
  errorMessage: (error) => error.message
}))

describe('useConsole', () => {
  beforeEach(() => {
    // Every test imports the store afresh, so the command history and the other module-scope
    // state of one test cannot leak into the next one, whatever order they run in.
    vi.resetModules()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('sending a command', () => {
    it('restores the typed command when sending it fails', async () => {
      session.client = ref({
        console: { execute: vi.fn(() => Promise.reject(new Error('connection refused'))) }
      })
      const { default: useConsole } = await import('./connection')
      const { command, execute } = useConsole()
      command.value = 'say hello'

      await execute()

      expect(command.value, 'a command that was not sent must stay in the input so it can be sent again').toBe('say hello')
    })
  })

  describe('command history', () => {
    it('keeps the commands sent before the console was opened again', async () => {
      session.client = ref({ console: { execute: vi.fn(() => Promise.resolve()) } })
      const { default: useConsole } = await import('./connection')
      const beforeRemount = useConsole()
      beforeRemount.command.value = 'list'
      await beforeRemount.execute()

      // Opening the view again creates a second call to the composable, as a remount does.
      const afterRemount = useConsole()
      afterRemount.previousCommand()

      expect(afterRemount.command.value, 'the arrow key should still recall the command sent before the remount').toBe('list')
    })
  })
})
