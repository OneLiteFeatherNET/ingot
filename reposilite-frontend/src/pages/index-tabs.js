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
 * The main sections of the index page. The selected section lives in the `tab` query
 * parameter, so the back button and deep links work and nothing has to be remembered in
 * the browser. Overview is the default and carries no parameter at all.
 */

const OVERVIEW = 'overview'

const TABS = [
  { key: 'overview', label: 'Overview', manager: false },
  { key: 'dashboard', label: 'Dashboard', manager: true },
  { key: 'tokens', label: 'Tokens', manager: true },
  { key: 'console', label: 'Console', manager: true },
  { key: 'settings', label: 'Settings', manager: true },
]

const visibleTabs = (isManager) =>
  TABS.filter(tab => !tab.manager || isManager)

/*
 * The section to show for a query value. A tab the user may not see, an unknown name and a
 * repeated parameter all fall back to Overview, so the page never shows an empty section.
 */
const resolveTab = (queryValue, isManager) => {
  if (typeof queryValue !== 'string') {
    return OVERVIEW
  }
  return visibleTabs(isManager).some(tab => tab.key === queryValue) ? queryValue : OVERVIEW
}

/* A copy of the route query with the tab set, or with the tab removed for Overview. */
const withTab = (query, key) => {
  const next = { ...query }
  if (key === OVERVIEW) {
    delete next.tab
  } else {
    next.tab = key
  }
  return next
}

const tabLabel = (key) =>
  TABS.find(tab => tab.key === key)?.label ?? key

export { TABS, OVERVIEW, visibleTabs, resolveTab, withTab, tabLabel }
