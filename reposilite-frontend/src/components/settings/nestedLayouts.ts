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
 * The order of the fields of the generated forms that are nested inside a settings entry. JSON Forms draws the
 * properties of an object in the order the schema lists them, which is the order of the Kotlin fields, and the
 * order that fits the code is not always the order that fits a task: the credentials of a mirror sit after its
 * timeouts, and the region of an S3 bucket sits after its keys. The saved configuration and the schemas keep the
 * Kotlin order. Only the display changes, through a uischema per nested schema that JSON Forms looks up by the title
 * of the schema it draws.
 */

import { NOT_APPLICABLE, type JsonFormsUISchemaRegistryEntry, type JsonSchema, type UISchemaElement, type VerticalLayout } from '@jsonforms/core'

interface NestedForm {
  /* The title of the schema the form draws, as the backend gives it. */
  title: string
  /* Properties the schema must also have, for a title that several schemas share. */
  requires?: readonly string[]
  /* The fields in the order they are drawn. A field that is not named follows them, in schema order. */
  fields: readonly string[]
}

export const NESTED_FORMS: readonly NestedForm[] = [
  {
    title: 'Mirrored Maven Repository',
    fields: ['reference', 'authorization', 'store', 'checksumPolicy', 'authenticatedFetchingOnly', 'allowedGroups', 'allowedExtensions', 'httpProxy', 'connectTimeout', 'readTimeout']
  },
  {
    title: 'S3 Storage Provider',
    fields: ['type', 'bucketName', 'region', 'endpoint', 'prefix', 'sharedBucket', 'accessKey', 'secretKey', 'metadataCacheSettings']
  },
  {
    title: 'File system Storage Provider',
    fields: ['type', 'mount', 'quota']
  },
  {
    // The browser login has an LDAP settings object of its own under the same title, which the two properties tell apart.
    title: 'LDAP',
    requires: ['userFilter', 'typeAttribute'],
    fields: ['enabled', 'hostname', 'port', 'ssl', 'baseDn', 'searchUserDn', 'searchUserPassword', 'userFilter', 'typeAttribute', 'userAttribute', 'userType']
  },
  {
    title: 'Breached Password Check',
    fields: ['mode', 'rangeUrl', 'file', 'failClosed']
  }
]

const MATCH = 10

const matches = (form: NestedForm, schema: JsonSchema): boolean => schema.title === form.title
  && (form.requires ?? []).every(name => name in (schema.properties ?? {}))

/*
 * The layout of a schema with the named fields first. Every property of the schema is in it: a field the list does not
 * name is drawn after the named ones, so a new setting is never hidden by a missing entry here.
 */
export const orderedLayout = (schema: JsonSchema, fields: readonly string[]): VerticalLayout => {
  const keys = Object.keys(schema.properties ?? {})
  const named = fields.filter(name => keys.includes(name))
  const rest = keys.filter(name => !named.includes(name))
  return {
    type: 'VerticalLayout',
    elements: [...named, ...rest].map(name => ({ type: 'Control', scope: `#/properties/${name}` }))
  }
}

const registryEntryOf = (form: NestedForm): JsonFormsUISchemaRegistryEntry => {
  /*
   * JSON Forms asks the tester about the schema, and then reads the layout of the entry that matched, for that same
   * schema. The schema of the last match is kept for that read. The layout is built on every read, because the object
   * renderer writes its label and options into the layout it is given.
   */
  let drawn: JsonSchema | undefined
  return {
    tester: (schema: JsonSchema) => {
      if (!matches(form, schema)) return NOT_APPLICABLE
      drawn = schema
      return MATCH
    },
    get uischema(): UISchemaElement {
      return orderedLayout(drawn ?? {}, form.fields)
    }
  }
}

/* The registry handed to every JsonForms that draws settings, so one nested schema is ordered the same everywhere. */
export const nestedUischemas: JsonFormsUISchemaRegistryEntry[] = NESTED_FORMS.map(registryEntryOf)
