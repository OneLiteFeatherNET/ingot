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

import { describe, it, expect } from 'vitest'
import {
  textParam,
  textFromParam,
  optionalParam,
  levelParam,
  enabledFromLevelParam,
  tokenFromEditor,
  editorFromToken
} from './url-state'

const LEVELS = ['Other', 'Trace', 'Debug', 'Info', 'Warn', 'Error']

describe('textParam and textFromParam', () => {
  it('leaves the parameter out when the text is empty', () => {
    expect(textParam('')).toBeUndefined()
  })

  it('keeps the text as it was typed', () => {
    expect(textParam('ci build')).toBe('ci build')
  })

  it('reads a missing parameter as empty text', () => {
    expect(textFromParam(undefined)).toBe('')
  })

  it('reads a repeated parameter as empty text rather than guessing', () => {
    expect(textFromParam(['a', 'b'])).toBe('')
  })
})

describe('optionalParam', () => {
  it('reads a missing parameter as missing, not as empty', () => {
    expect(optionalParam(undefined)).toBeUndefined()
  })

  it('keeps an empty value apart from a missing one', () => {
    expect(optionalParam('')).toBe('')
  })
})

describe('levelParam', () => {
  it('leaves the parameter out while every level is shown', () => {
    expect(levelParam(LEVELS, LEVELS)).toBeUndefined()
  })

  it('lists the shown levels in lower case when some are hidden', () => {
    expect(levelParam(['Info', 'Warn', 'Error'], LEVELS)).toBe('info,warn,error')
  })

  it('writes an empty value when every level is hidden', () => {
    expect(levelParam([], LEVELS)).toBe('')
  })
})

describe('enabledFromLevelParam', () => {
  it('shows every level when the parameter is missing', () => {
    expect(enabledFromLevelParam(undefined, LEVELS)).toEqual(LEVELS)
  })

  it('shows exactly the levels the parameter lists', () => {
    expect(enabledFromLevelParam('info,error', LEVELS)).toEqual(['Info', 'Error'])
  })

  it('ignores names that are not levels and keeps the level order', () => {
    expect(enabledFromLevelParam('error,nonsense,TRACE', LEVELS)).toEqual(['Trace', 'Error'])
  })

  it('shows no level when the parameter is empty', () => {
    expect(enabledFromLevelParam('', LEVELS)).toEqual([])
  })

  it('reads a repeated parameter as if it were missing', () => {
    expect(enabledFromLevelParam(['info', 'warn'], LEVELS)).toEqual(LEVELS)
  })

  it('round trips through levelParam', () => {
    const shown = ['Debug', 'Warn']
    expect(enabledFromLevelParam(levelParam(shown, LEVELS), LEVELS)).toEqual(shown)
  })
})

describe('tokenFromEditor', () => {
  it('names no token while no editor is open', () => {
    expect(tokenFromEditor(null)).toBeNull()
  })

  it('names the token whose details are open', () => {
    expect(tokenFromEditor('token:ci-bot')).toBe('ci-bot')
  })

  it('names the new token form as new', () => {
    expect(tokenFromEditor('newtoken')).toBe('new')
  })

  it('keeps the token in the address while one of its routes is being edited', () => {
    expect(tokenFromEditor('route:ci-bot:/releases/**')).toBe('ci-bot')
    expect(tokenFromEditor('newroute:ci-bot')).toBe('ci-bot')
  })
})

describe('editorFromToken', () => {
  it('opens no editor without a token in the address', () => {
    expect(editorFromToken(null)).toBeNull()
    expect(editorFromToken(undefined)).toBeNull()
  })

  it('opens the create form for the reserved name new', () => {
    expect(editorFromToken('new')).toBe('newtoken')
  })

  it('opens the editor of the named token', () => {
    expect(editorFromToken('ci-bot')).toBe('token:ci-bot')
  })

  it('reads back the token that tokenFromEditor named', () => {
    expect(tokenFromEditor(editorFromToken('ci-bot'))).toBe('ci-bot')
    expect(tokenFromEditor(editorFromToken('new'))).toBe('new')
  })
})
