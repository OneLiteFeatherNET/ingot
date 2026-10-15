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
 * The width of the navigation rail, which sits at the left edge from 600px up. It is a separate
 * value from the bottom bar height, although both are 80px today, so that a change to one does
 * not silently move the other. The rail publishes it as --app-rail-width on the document root
 * while it is mounted, and fixed overlays such as the snackbar read it there.
 */
export const RAIL_WIDTH = 80

export const RAIL_WIDTH_VARIABLE = '--app-rail-width'

export const railWidth = () => `${RAIL_WIDTH}px`

export const publishRailWidth = (root = document.documentElement) => {
  root.style.setProperty(RAIL_WIDTH_VARIABLE, railWidth())
}

export const clearRailWidth = (root = document.documentElement) => {
  root.style.removeProperty(RAIL_WIDTH_VARIABLE)
}
