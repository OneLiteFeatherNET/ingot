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

// Types shared by the md primitives. They live in a .ts module because the lint rules for .vue files
// flag the parameter names of function types written inside a component.

// One destination of a navigation bar or rail. `shortLabel` is the visible text when set, and `group`
// names the block the entry belongs to in a rail.
export interface NavItem {
  key: string
  label: string
  href?: string
  shortLabel?: string
  group?: string
}

// The scoped slot of a navigation item: the item itself and whether it is the current destination.
export type NavIconSlot = (slot: { item: NavItem; active: boolean }) => unknown

// Checks one value of an input chip field against the values already there. Returns the problem to show,
// or an empty string when the value is accepted.
export type TextValidator = (text: string, existing: string[]) => string

// A divider between groups of items in a menu. It has no value and takes no focus.
export interface MenuSeparator {
  type: 'separator'
}

// A chooseable entry of a menu. `type: 'radio'` draws it as the choice in use.
export interface MenuEntry {
  type?: 'radio'
  value: string
  label?: string
  ariaLabel?: string
  error?: boolean
  href?: string
  checked?: boolean
}

export type MenuItem = MenuSeparator | MenuEntry

// The navigate function that a router link passes to its slot. It takes the click event and resolves once the route has moved.
export type RouterNavigate = (event?: MouseEvent) => Promise<unknown>
