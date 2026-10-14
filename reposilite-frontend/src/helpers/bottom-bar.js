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
 * The height of the bottom navigation bar, without the device's safe area. The page publishes
 * it as the CSS variable --app-bottom-bar-height on the document root, so that fixed overlays
 * such as the snackbar can sit above the bar. Whether the bar is on screen at the current
 * width is left to CSS: the overlay only reads the variable below 600px.
 */
export const BOTTOM_BAR_HEIGHT = 80

export const BOTTOM_BAR_VARIABLE = '--app-bottom-bar-height'

export const bottomBarHeight = (hasBar) => (hasBar ? `${BOTTOM_BAR_HEIGHT}px` : '0px')
