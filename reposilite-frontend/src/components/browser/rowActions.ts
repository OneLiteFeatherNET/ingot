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

// The actions a row of the listing can offer, shared by the row and by the list, which sizes the
// action slot of every row by the widest row it shows.

const HUMAN_READABLE = ['application/xml', 'text/plain', 'text/xml', 'text/markdown', 'application/json']

// Index is the number of actions: three 40px buttons and two 4px gaps is the widest slot. From the medium window
// (600 px) up every row reserves that width, so the sizes line up. Below it a row is only as wide as its own
// buttons, so a phone keeps the room for the names that the row had before the slot existed.
const ACTION_SLOT_CLASSES = ['w-0', 'w-auto medium:w-10', 'w-auto medium:w-21', 'w-auto medium:w-32']

export const actionSlotClass = (count) =>
  ACTION_SLOT_CLASSES[Math.min(Math.max(count, 0), ACTION_SLOT_CLASSES.length - 1)]

// Deleting needs a path below the repository and write access to it.
export const canDeleteAt = (path, hasPermissionTo) =>
  path.length > 1 && hasPermissionTo(`/${path}`, 'route:write')

const hasJavadocPath = (path) => {
  const elements = path.split('/')
  return elements.length >= 2 && elements[1] !== ''
}

export const rowActionsOf = ({ file, qualifier, canDelete, javadocSuffixes }) => ({
  view: Object.hasOwn(file, 'contentLength') && HUMAN_READABLE.includes(file.contentType),
  javadoc: file.type !== 'DIRECTORY'
    && javadocSuffixes.some(suffix => file.name.endsWith(suffix))
    && hasJavadocPath(qualifier.path),
  delete: canDelete
})

export const actionCountOf = (options) =>
  Object.values(rowActionsOf(options)).filter(Boolean).length

// The most actions any row of the listing offers: the slot every row reserves, so the sizes line up.
export const widestActionCount = (files, { qualifier, canDelete, javadocSuffixes }) =>
  files.reduce(
    (widest, file) => Math.max(widest, actionCountOf({ file, qualifier, canDelete, javadocSuffixes })),
    0
  )
