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
 * Shapes of the settings API that the store and the settings components share. The generated API types
 * do not name them: the schema of a domain is plain JSON, and the bodies below are read by the helpers.
 */

/*
 * A JSON schema of a settings domain as the backend writes it. The keywords the helpers and the forms read are named.
 * There is no open index signature: a value of type {} (for instance a narrowed unknown) must still be a schema.
 */
export interface SettingsSchema {
  $schema?: string
  type?: string | string[]
  title?: string
  description?: string
  format?: string
  additionalProperties?: boolean
  const?: unknown
  default?: unknown
  required?: string[]
  properties?: Record<string, SettingsSchema>
  items?: SettingsSchema
  enum?: unknown[]
  oneOf?: SettingsSchema[]
  anyOf?: SettingsSchema[]
  allOf?: SettingsSchema[]
}

/* An entry of a collection. Its `id` addresses it, and the other fields belong to the entry. */
export interface CollectionEntry {
  id?: unknown
  [field: string]: unknown
}

/* The repository entry the settings store receives. Optional fields are added only when the schema defines them. */
export interface RepositoryEntry {
  id: string
  visibility: string
  type: string
  [field: string]: unknown
}

/* The members of a team as the server lists them. Only the principal and its source are read here. */
export interface TeamBody {
  members?: { principal: string; source: string }[]
}

/* The nodes of a principal's node list as the server lists them. */
export interface NodeListBody {
  nodes?: { node: string; allowed?: boolean }[]
}
