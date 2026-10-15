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

import { createWebHistory } from 'vue-router'
import IndexPage from '../pages/IndexPage.vue'
import usePlaceholders from '../store/placeholders'
import { useSession, whenSessionKnown } from '../store/session'
import { createInfoToast } from '../helpers/toast'
import { createAppRouter } from './create'
import { legacyLocation } from './legacy'

/*
 * The application's router. It is created when the app starts, not when this module loads:
 * the base path is only known once the settings have arrived, and a router built too early
 * would mount the dashboard at the wrong place.
 *
 * The base path is where the dashboard is mounted, so routes are resolved below it and the
 * links the router produces already carry it.
 */
export function createIngotRouter() {
  const { basePath, title } = usePlaceholders()
  const { isManager, isLogged } = useSession()

  return createAppRouter({
    history: createWebHistory(basePath),
    component: IndexPage,
    legacy: legacyLocation,
    siteTitle: title,
    whenSessionKnown,
    isManager: () => isManager.value,
    isLogged: () => isLogged.value,
    notify: createInfoToast
  })
}
