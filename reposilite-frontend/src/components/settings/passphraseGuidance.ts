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

import { MIN_PASSWORD_LENGTH } from './settingsEncryption'

/*
 * The strength line under the passphrase field. It is computed from the text alone: no dictionary and no
 * third-party library. Length counts code points, so an emoji is one character, the way a person counts it.
 */

/* Below this many characters a passphrase is not accepted; from it on the line says "long". */
export const PASSPHRASE_LONG_LENGTH = 16

export type PassphraseLevel = 'empty' | 'too-short' | 'acceptable' | 'long'

export interface PassphraseGuidance {
  level: PassphraseLevel
  label: string
  confirmationMatches: boolean
  submittable: boolean
}

const LABELS: Record<PassphraseLevel, string> = {
  empty: '',
  'too-short': 'too short',
  acceptable: 'acceptable',
  long: 'long'
}

const levelOf = (length: number): PassphraseLevel => {
  if (length === 0) return 'empty'
  if (length < MIN_PASSWORD_LENGTH) return 'too-short'
  if (length < PASSPHRASE_LONG_LENGTH) return 'acceptable'
  return 'long'
}

/* The confirmation is null where none is asked, as on import. */
export const passphraseGuidance = ({ password, confirmation = null }: { password: string; confirmation?: string | null }): PassphraseGuidance => {
  const level = levelOf([...password].length)
  const confirmationMatches = confirmation === null || confirmation === password
  return {
    level,
    label: LABELS[level],
    confirmationMatches,
    submittable: (level === 'acceptable' || level === 'long') && confirmationMatches
  }
}
