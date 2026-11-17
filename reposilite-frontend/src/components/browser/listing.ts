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

import type { components } from '../../types/api'

/*
 * The shapes the file browser passes between its listing, its rows and its dialogs. The API spec
 * describes a listed file by its name and type; the listing also carries a file's content type,
 * length and modification instant, which the browser reads too.
 */

export type BrowserFile = components['schemas']['FileDetails'] & {
  contentType?: string
  contentLength?: number
  lastModifiedTime?: number
  // Set on the directories the browser makes up for a forbidden listing; nothing reads it.
  list?: BrowserFile[]
}

// The repository or directory being browsed, as the route names it.
export interface BrowserPath {
  path: string
}

// The browsed path, with the counter that asks the listing again when it changes.
export interface BrowserQualifier extends BrowserPath {
  watchable: number
}

// The ways a listing can fail, from the classification of the failed request.
export type ListingFailure = 'not-found' | 'forbidden' | 'network' | 'unknown'

// The listing as the browser holds it: the files, or the failure in place of them.
export interface Listing {
  list: BrowserFile[]
  error: false | ListingFailure
  message?: string
}

// The entry the delete dialog is about to remove.
export interface DeleteTarget {
  path: string
  file: string
  directory: boolean
}
