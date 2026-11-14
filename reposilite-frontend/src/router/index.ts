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

import type { Component } from 'vue'
import { createWebHistory, type Router } from 'vue-router'
import IndexPage from '../pages/IndexPage.vue'
import usePlaceholders from '../store/placeholders'
import { useSession, whenSessionKnown } from '../store/session'
import { createAppRouter } from './create'
import { legacyLocation } from './legacy'
import type { Can } from '../navigation/registry'

/*
 * The application's router. It is created when the app starts, not when this module loads:
 * the base path is only known once the settings have arrived, and a router built too early
 * would mount the dashboard at the wrong place.
 *
 * The base path is where the dashboard is mounted, so routes are resolved below it and the
 * links the router produces already carry it.
 */
export function createIngotRouter(): Router {
  // The store is still JavaScript, so the two values it gives are typed here, at the boundary.
  const { basePath, title } = usePlaceholders() as { basePath: string; title: string }
  // The store is still JavaScript, so the session values are typed here, at the boundary.
  const { isManager, can, mustChangePassword } = useSession() as {
    isManager: { value: boolean }
    can: Can
    mustChangePassword: { value: boolean }
  }

  return createAppRouter({
    history: createWebHistory(basePath),
    component: IndexPage as Component,
    legacy: legacyLocation,
    siteTitle: title,
    whenSessionKnown,
    isManager: () => isManager.value,
    can,
    mustChangePassword: () => mustChangePassword.value,
    // The same breakpoint as the side navigation: below 1240px it is an index.
    narrow: () => window.matchMedia('(max-width: 1239px)').matches
  })
}
