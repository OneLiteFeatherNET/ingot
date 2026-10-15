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

import { createRouter } from 'vue-router'
import { ROUTES } from './routes'
import { createManagerGuard, createPasswordChangeGuard } from './guard'
import { pageTitle } from './title'

/*
 * Builds the router from its parts. Nothing here reads the browser, the session or the
 * settings on its own: they are passed in, so the router can be built on memory history with
 * fakes in a test, and src/router/index.js is the only place that wires in the real ones.
 *
 * - legacy(hash) turns an old `#/...` link into a location, or returns null. It is asked
 *   once, for the first navigation, which is the one the app starts with.
 * - The manager options describe the session. The defaults are an anonymous visitor.
 */
const createAppRouter = ({
  history,
  component,
  legacy = () => null,
  siteTitle = '',
  whenSessionKnown = () => Promise.resolve(),
  isManager = () => false,
  isLogged = () => false,
  mustChangePassword = () => false,
  notify = () => {}
}) => {
  const router = createRouter({
    history,
    routes: ROUTES.map(route => ({ ...route, component }))
  })

  let started = false
  router.beforeEach((to) => {
    if (started) return true
    started = true
    return legacy(to.hash) ?? true
  })

  router.beforeEach(createPasswordChangeGuard({ mustChangePassword }))
  router.beforeEach(createManagerGuard({ whenSessionKnown, isManager, isLogged, notify }))

  router.afterEach((to) => {
    document.title = pageTitle(to, siteTitle)
  })

  return router
}

export { createAppRouter }
