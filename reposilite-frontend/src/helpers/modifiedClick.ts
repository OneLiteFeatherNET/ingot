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
 * Whether a click is a modified click: one with Ctrl, Shift, Meta or Alt, or with a button other than the
 * primary one. The browser opens a modified click in a new tab or window, or shows a menu for it, so a link
 * leaves it alone: it neither prevents the default action nor reports a selection of the page.
 */
export type ClickModifiers = Pick<MouseEvent, 'button' | 'ctrlKey' | 'shiftKey' | 'metaKey' | 'altKey'>

export const isModifiedClick = (event: ClickModifiers): boolean =>
  event.button !== 0 || event.ctrlKey || event.shiftKey || event.metaKey || event.altKey
