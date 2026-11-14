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
 * The seams of the migration components: the API, the wizard store and the timers and tab visibility of the
 * poller. A component reads each one with `inject`, and the default is the application's. A test provides its
 * own, so no test waits for a real timer or touches the network.
 */

const MIGRATION_API = Symbol('migration-api')
const MIGRATION_WIZARD = Symbol('migration-wizard')
const MIGRATION_RUNTIME = Symbol('migration-runtime')

// The browser's timers and the browser's tab visibility, as the poller takes them.
const browserRuntime = () => ({
  now: () => Date.now(),
  timers: {
    setTimeout: (callback, ms) => window.setTimeout(callback, ms),
    clearTimeout: (handle) => window.clearTimeout(handle),
  },
  visibility: {
    isHidden: () => document.visibilityState === 'hidden',
    subscribe: (listener) => {
      document.addEventListener('visibilitychange', listener)
      return () => document.removeEventListener('visibilitychange', listener)
    },
  },
})

export { MIGRATION_API, MIGRATION_WIZARD, MIGRATION_RUNTIME, browserRuntime }
