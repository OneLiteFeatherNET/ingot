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
 * The fit decision of the Administration tree. From 1240 pixels the tree is sticky beside the page only while its full
 * height fits between the top bar and the save bar; otherwise it scrolls with the page and has no scroll box of its own.
 * The decision is a pure function, so it is tested with fixed heights (see useTreeFits for the measuring).
 *
 * `margin` is the gap under the top bar and above the save bar: the tree is sticky 1rem below the top bar, and it keeps
 * the same gap at the bottom. The room for the tree is the window less the top bar, the save bar and two margins.
 */
export const TOP_BAR_HEIGHT = 64
export const TREE_MARGIN = 16

export const treeFits = ({ navHeight, viewportHeight, topBarHeight, saveBarHeight = 0, margin = TREE_MARGIN }) =>
  navHeight <= viewportHeight - topBarHeight - saveBarHeight - 2 * margin
