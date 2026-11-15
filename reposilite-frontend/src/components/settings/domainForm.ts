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

import { entryLayout } from './collections'

/*
 * The fields of a domain that are not collections, as one generated form. Most domains have none of these, and then
 * null is returned and no form is drawn. The settings page and the Maven options section of the Repositories page
 * share it, so a field is shown the same way on both.
 */
export const domainFormOf = (schema, collectionKeys = []) => {
  if (!schema?.properties) return null
  const fields = Object.fromEntries(
    Object.entries(schema.properties).filter(([key]) => !collectionKeys.includes(key))
  )
  if (Object.keys(fields).length === 0) return null
  const fieldSchema = { ...schema, properties: fields }
  return { schema: fieldSchema, layout: entryLayout(fieldSchema, { exclude: [] }) }
}
