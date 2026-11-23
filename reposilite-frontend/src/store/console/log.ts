/*
 * Copyright (c) 2023 dzikoysk
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

import { computed, ref, reactive, type ComputedRef } from 'vue'
import { parseAnsi, type AnsiSegment } from './ansi'

const levelNames = ['Other', 'Trace', 'Debug', 'Info', 'Warn', 'Error']

export interface LogLevel {
  name: string
  enabled: boolean
  count: ComputedRef<number>
}

export interface LineEntry {
  id: number
  kind: 'line'
  segments: AnsiSegment[]
  text: string
  level: string
}

export interface SeparatorEntry {
  id: number
  kind: 'separator'
  message: string
  text: string
  level: null
}

// The structured data of a result, drawn as a table. Every cell is text.
export interface TableEntry {
  id: number
  kind: 'table'
  columns: string[]
  rows: string[][]
  text: string
  level: string
}

export type LogEntry = LineEntry | SeparatorEntry | TableEntry

const cell = (value: unknown): string => {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : ''
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

/**
 * The table a result's `data` stands for, or null when it has none. A list of records takes their keys as columns, a
 * list of plain values is one column, and a single record is a key and value table.
 */
export const tableOf = (data: unknown): { columns: string[]; rows: string[][] } | null => {
  if (Array.isArray(data)) {
    if (data.length === 0) return null
    if (data.every(isRecord)) {
      const columns = [...new Set(data.flatMap(record => Object.keys(record)))]
      return { columns, rows: data.map(record => columns.map(column => cell(record[column]))) }
    }
    return { columns: ['value'], rows: data.map(value => [cell(value)]) }
  }
  if (isRecord(data)) {
    const entries = Object.entries(data)
    return entries.length === 0 ? null : { columns: ['key', 'value'], rows: entries.map(([key, value]) => [key, cell(value)]) }
  }
  return null
}

const levels = reactive({}) as Record<string, LogLevel>
const filter = ref('')
const id = ref(0)
const rawLog = reactive([]) as LogEntry[]

const getLevel = (message: string): string =>
  levelNames.find(level => message.includes(`${level.toUpperCase()} | `)) ?? 'Other'

export default function useLog() {
  levelNames.forEach(level => {
    levels[level] = {
      name: level,
      enabled: true,
      count: computed(() => rawLog.reduce((accumulator, entry) => accumulator + (entry.level === level ? 1 : 0), 0))
    }
  })

  // A separator is a marker in the output, not a line from the server, so the filters and the
  // level switches never hide it.
  const isSeparator = (entry: LogEntry): entry is SeparatorEntry => entry.kind === 'separator'

  const log = computed(() => {
    return rawLog
      .filter(entry => isSeparator(entry) || entry.text.toLowerCase().includes(filter.value.toLowerCase()))
      .filter(entry => isSeparator(entry) || levels[entry.level].enabled)
  })

  const hasLines = computed(() => rawLog.some(entry => !isSeparator(entry)))

  // A level given by name, as a socket frame names it, wins over the one read from the text.
  const logMessage = (value: string, level?: string) => {
    const segments = parseAnsi(value)
    const text = segments.map(segment => segment.text).join('')
    const named = level === undefined ? undefined : levelNames.find(name => name.toUpperCase() === level.toUpperCase()) ?? 'Other'
    rawLog.push({ id: id.value++, kind: 'line', segments, text, level: named ?? getLevel(value) })
  }

  // The data of a result. It counts as an info entry, and the filter matches the text of its cells.
  const addTable = (table: { columns: string[]; rows: string[][] }) => {
    rawLog.push({ id: id.value++, kind: 'table', ...table, text: table.rows.map(row => row.join(' ')).join('\n'), level: 'Info' })
  }

  // The label is ours and constant, and like every other line it is rendered as text.
  const addSeparator = (label: string) => {
    rawLog.push({ id: id.value++, kind: 'separator', message: label, text: label, level: null })
  }

  const clearLog = () => {
    rawLog.length = 0
  }

  return {
    levels,
    log,
    hasLines,
    filter,
    logMessage,
    addSeparator,
    addTable,
    clearLog
  }
}

export { levelNames }
