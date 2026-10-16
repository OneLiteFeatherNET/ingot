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
 * How the state of a view is written into its URL and read back from it. The views own the
 * state; the router owns the address. These functions are the only place where the two meet,
 * so the spelling of a parameter is decided once.
 *
 * A parameter that holds the default is left out of the address, so a plain link stays plain.
 */

// Empty text is the default, so it has no parameter at all.
const textParam = (text) => (text ? text : undefined)

// A repeated parameter is ambiguous, so it counts as no text rather than a guess.
const textFromParam = (param) => (typeof param === 'string' ? param : '')

// For a parameter whose absence means something other than its empty value.
const optionalParam = (param) => (typeof param === 'string' ? param : undefined)

/*
 * The shown levels, as a comma list in lower case. Every level shown is the default, so the
 * parameter is left out then. Nothing shown is written as an empty value, which is the only
 * way to say so that is different from the default.
 */
const levelParam = (shown, allLevels) =>
  shown.length === allLevels.length
    ? undefined
    : shown.map(level => level.toLowerCase()).join(',')

const enabledFromLevelParam = (param, allLevels) => {
  if (typeof param !== 'string') {
    return [...allLevels]
  }
  const listed = new Set(param.split(',').map(name => name.trim().toLowerCase()))
  return allLevels.filter(level => listed.has(level.toLowerCase()))
}

// The token whose editor is open, or 'new' for the create form. An open route editor keeps
// its token in the address too, so the address does not jump away while a route is edited.
const tokenFromEditor = (editing) => {
  if (!editing) return null
  if (editing === 'newtoken') return 'new'
  const separator = editing.indexOf(':')
  const rest = editing.slice(separator + 1)
  return editing.slice(0, separator) === 'token' ? rest : rest.split(':')[0]
}

const NEW_TOKEN = 'new'

const editorFromToken = (token) => {
  if (!token) return null
  return token === NEW_TOKEN ? 'newtoken' : `token:${token}`
}

/*
 * The tags of the overview: a repeated `tag` parameter, read as lower-case names. A single value
 * arrives as text and several as a list; a value without text is dropped, and each tag counts once.
 */
const tagsFromParam = (param) => {
  const values = [param ?? []].flat()
  const tags = values
    .filter(value => typeof value === 'string' && value.trim() !== '')
    .map(value => value.trim().toLowerCase())
  return [...new Set(tags)]
}

// The empty list is the default, so it leaves the parameter out. Otherwise each tag is repeated.
const tagsParam = (tags) => (tags.length > 0 ? [...tags] : undefined)

export {
  textParam,
  textFromParam,
  optionalParam,
  levelParam,
  enabledFromLevelParam,
  tokenFromEditor,
  editorFromToken,
  tagsFromParam,
  tagsParam
}
