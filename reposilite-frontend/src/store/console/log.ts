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

export type LogEntry = LineEntry | SeparatorEntry

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

  const logMessage = (value: string) => {
    const segments = parseAnsi(value)
    const text = segments.map(segment => segment.text).join('')
    rawLog.push({ id: id.value++, kind: 'line', segments, text, level: getLevel(value) })
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
    clearLog
  }
}

export { levelNames }
