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

import { computed, ref, reactive } from 'vue'
import { parseAnsi } from './ansi'

const levelNames = ['Other', 'Trace', 'Debug', 'Info', 'Warn', 'Error']
const levels  = reactive({})
const filter = ref('')
const id = ref(0)
const rawLog = reactive([])

const getLevel = message =>
  levelNames.find(level => message.includes(`${level.toUpperCase()} | `)) ?? 'Other'

export default function useLog() {
  levelNames.forEach(level => {
    levels[level] = {
      name: level,
      enabled: true,
      count: computed(() => rawLog.reduce((accumulator, entry) => accumulator + (entry.level === level), 0))
    }
  })

  const log = computed(() => {
    return rawLog
      .filter(entry => entry.text.toLowerCase().includes(filter.value.toLowerCase()))
      .filter(entry => levels[entry.level].enabled)
  })

  const logMessage = value => {
    const segments = parseAnsi(value)
    const text = segments.map(segment => segment.text).join('')
    rawLog.push({ id: id.value++, segments, text, level: getLevel(value) })
  }

  const clearLog = () => {
    rawLog.length = 0
  }

  return {
    levels,
    log,
    filter,
    logMessage,
    clearLog
  }
}
