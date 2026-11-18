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
 * The helpers of the plugin blocks. The server checks each path when the plugin registers it
 * (PluginUiValidator); the dashboard checks it again before it calls anything, so a manifest changed
 * in transit cannot widen a call. The rule is the same in both places.
 */

import type { Method } from 'axios'
import type { JsonSchema, UISchemaElement } from '@jsonforms/core'
import type { PluginPage } from '../../navigation/registry'

export const BLOCK_TYPES = ['text', 'keyValue', 'table', 'form', 'action', 'status', 'code', 'link', 'list']

// One block of a page as the server declares it. Each kind reads only the fields it names.
export interface PluginBlockDefinition {
  type?: string
  text?: string
  label?: string
  source?: string
  target?: string
  path?: string
  method?: Method
  confirm?: string
  danger?: boolean
  refreshSeconds?: number
  language?: string
  href?: string
  pageSize?: number
  columns?: { key: string; label: string }[]
  schema?: JsonSchema
  uiSchema?: UISchemaElement
}

// A contributed page with its blocks typed. The registry keeps the blocks as unknown until a page draws them.
export type DrawnPage = Omit<PluginPage, 'blocks'> & { blocks?: PluginBlockDefinition[] }

// The line a block shows when a request fails.
export const failureText = (failure: unknown): string => (failure as Error | undefined)?.message ?? 'unknown error'

export const MAX_TABLE_ROWS = 500

const PATH_CHARACTERS = /^[A-Za-z0-9._~/-]+$/

// A path is allowed when it is below the prefix of the plugin and climbs out of nothing.
export const pathAllowed = (plugin: string, path: unknown): boolean => {
  const prefix = `/api/plugins/${plugin}/`
  if (typeof path !== 'string' || !path.startsWith(prefix) || !PATH_CHARACTERS.test(path)) return false
  const rest = path.slice(prefix.length)
  return rest.length > 0 && rest.split('/').every(segment => segment !== '' && segment !== '.' && segment !== '..')
}

const LINK_CHARACTERS = /^[A-Za-z0-9._~/?=&-]+$/
const LINK_LIMIT = 500

// A link is a same-origin path, checked with the same rules as the server (PluginUiValidator.linkProblem).
export const linkAllowed = (href: unknown): boolean => {
  if (typeof href !== 'string' || href.length > LINK_LIMIT || !LINK_CHARACTERS.test(href)) return false
  if (!href.startsWith('/') || href[1] === '/' || href[1] === '\\') return false
  if (href.startsWith('/api/') || href.startsWith('/assets/')) return false
  return href.split('?')[0].slice(1).split('/').every(segment => segment !== '' && segment !== '.' && segment !== '..')
}

// The text a value shows as: strings as they are, everything else as compact JSON.
export const displayValue = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

// The rows a table shows and how many it cut. The count of the rows is kept, so the cut is said out loud.
export const tableRows = <T>(rows: T[]) => ({
  shown: rows.slice(0, MAX_TABLE_ROWS),
  total: rows.length,
  cut: rows.length > MAX_TABLE_ROWS,
})

// The rows a list shows: at most 100, and an item without a usable title is skipped and counted.
export const MAX_LIST_ROWS = 100
const TITLE_LIMIT = 200
const SUBTITLE_LIMIT = 400
const CHIP_LIMIT = 40
const MAX_CHIPS = 8

export interface ListRow {
  title: string
  subtitle?: string
  chips: string[]
  link: string | null
  linkRefused: boolean
}

// One item of a list response as a row, or null when the item has no usable title.
const listRow = (item: unknown): ListRow | null => {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return null
  const { title, subtitle, chips, href } = item as Record<string, unknown>
  if (typeof title !== 'string' || title.length === 0 || title.length > TITLE_LIMIT) return null
  const chipTexts = Array.isArray(chips)
    ? chips.filter((chip): chip is string => typeof chip === 'string' && chip.length > 0 && chip.length <= CHIP_LIMIT).slice(0, MAX_CHIPS)
    : []
  const linked = href !== undefined && linkAllowed(href)
  return {
    title,
    subtitle: typeof subtitle === 'string' && subtitle.length <= SUBTITLE_LIMIT ? subtitle : undefined,
    chips: chipTexts,
    link: linked ? (href as string) : null,
    linkRefused: href !== undefined && !linked,
  }
}

export const listRows = (items: unknown[]) => {
  const rows: ListRow[] = []
  let skipped = 0
  for (const item of items) {
    const row = listRow(item)
    if (row) {
      rows.push(row)
    } else {
      skipped += 1
    }
  }
  return {
    shown: rows.slice(0, MAX_LIST_ROWS),
    total: rows.length,
    skipped,
    cut: rows.length > MAX_LIST_ROWS,
  }
}
