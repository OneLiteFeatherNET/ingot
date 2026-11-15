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
 * Reading a settings file into the draft. The file is the export of the settings: an object that maps
 * each domain to its values. Every domain it names is checked against that domain's schema with the
 * same validator the store uses. A file with any error is not loaded at all, so the draft never holds
 * values the server would refuse, and the errors say where each one is.
 */

import { humanize } from './settingsDiff'
import type { SettingsSchema } from './collections'

/* A settings object: one value per domain, keyed by the domain's name. */
export type SettingsFile = Record<string, unknown>

/* One error the validator reports. Only the keywords and parameters the messages read are named. */
export interface ValidationError {
  keyword: string
  instancePath?: string
  message?: string
  params: { allowedValues?: unknown[]; missingProperty?: string }
}

/* A compiled schema check: true when the data passes, and the errors of the last failed check otherwise. */
export interface CompiledCheck {
  (data: unknown): boolean
  errors?: ValidationError[] | null
}

/* The validator the store uses to compile the schema of a domain. */
export interface SchemaValidator {
  compile(schema: unknown): CompiledCheck
}

/* One problem with the file: the domain, the place in it (empty for the domain as a whole) and the message. */
export interface ImportError {
  domain: string
  label: string
  message?: string
}

const isPlainObject = (value: unknown): value is SettingsFile => value !== null && typeof value === 'object' && !Array.isArray(value)

/* The text of a file as settings: { ok, value } or { ok: false, error }. Nothing is checked against a schema here. */
export const parseSettingsFile = (text: string): { ok: boolean; value?: SettingsFile; error?: string } => {
  let value: unknown
  try {
    value = JSON.parse(text)
  } catch {
    return { ok: false, error: 'The file is not valid JSON.' }
  }
  if (!isPlainObject(value)) {
    return { ok: false, error: 'The file must hold a settings object, with one entry per domain.' }
  }
  return { ok: true, value }
}

/*
 * The file laid over the draft. A domain the file names keeps the draft's fields that the file leaves
 * out, so a file with one field changed does not drop the others. A field the file does name, a list
 * for instance, is taken as the file gives it. The draft is not changed.
 */
export const overlayImport = (file: SettingsFile, draft: SettingsFile | null | undefined): SettingsFile => Object.fromEntries(
  Object.entries(file).map(([domain, values]) => [
    domain,
    isPlainObject(values) && isPlainObject(draft?.[domain]) ? { ...JSON.parse(JSON.stringify(draft?.[domain])) as SettingsFile, ...values } : values
  ])
)

const titleOf = (schema: SettingsSchema | undefined, key: string): string => schema?.title ?? humanize(key)

/*
 * The schema titles of the place an error is in, such as "Repositories > releases > Visibility". An
 * entry of a list is named by its id, and a missing required field by its name.
 */
const labelOf = (schema: SettingsSchema | undefined, data: unknown, instancePath: string, missingProperty?: string): string => {
  const segments = instancePath.split('/').slice(1)
  const labels: unknown[] = []
  let node: SettingsSchema | undefined = schema
  let value: unknown = data
  for (const segment of segments) {
    if (node?.items !== undefined && Array.isArray(value)) {
      const entry: unknown = value[Number(segment)]
      labels.push((entry as { id?: unknown } | undefined)?.id ?? `#${Number(segment) + 1}`)
      node = node.items
      value = entry
    } else {
      const child: SettingsSchema | undefined = node?.properties?.[segment]
      labels.push(titleOf(child, segment))
      node = child
      value = (value as Record<string, unknown> | undefined)?.[segment]
    }
  }
  if (missingProperty !== undefined) labels.push(titleOf(node?.properties?.[missingProperty], missingProperty))
  return labels.join(' > ')
}

const messageOf = (error: ValidationError): string | undefined => {
  if (error.keyword === 'enum') {
    return `must be one of ${(error.params.allowedValues as unknown[]).map(value => JSON.stringify(value)).join(', ')}`
  }
  return error.message
}

/*
 * Checks the file against the schemas of the domains it names. Returns { ok, domains, errors }: `domains`
 * holds the checked values, with the schema defaults filled in, only when there are no errors. An error
 * is { domain, label, message }; `label` is empty when the error is the domain as a whole.
 */
export const validateImport = (file: SettingsFile, { domains, schemas, validator }: {
  domains: string[]
  schemas: Record<string, SettingsSchema | undefined>
  validator: SchemaValidator
}): { ok: boolean; domains: SettingsFile; errors: ImportError[] } => {
  const errors: ImportError[] = []
  const values: SettingsFile = {}

  for (const domain of Object.keys(file)) {
    if (!domains.includes(domain)) {
      errors.push({ domain, label: '', message: 'Unknown settings domain' })
      continue
    }
    const data: unknown = JSON.parse(JSON.stringify(file[domain]))
    let validate: CompiledCheck
    try {
      validate = validator.compile(schemas[domain])
    } catch {
      errors.push({ domain, label: '', message: 'The schema of this domain cannot check the file.' })
      continue
    }
    if (!validate(data)) {
      for (const error of validate.errors ?? []) {
        errors.push({
          domain,
          label: labelOf(schemas[domain], data, error.instancePath ?? '', error.keyword === 'required' ? error.params.missingProperty : undefined),
          message: messageOf(error)
        })
      }
      continue
    }
    values[domain] = data
  }

  const ok = errors.length === 0
  return { ok, domains: ok ? values : {}, errors }
}
