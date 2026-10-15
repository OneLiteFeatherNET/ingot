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

const isPlainObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value)

/* The text of a file as settings: { ok, value } or { ok: false, error }. Nothing is checked against a schema here. */
export const parseSettingsFile = (text) => {
  let value
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

const titleOf = (schema, key) => schema?.title ?? humanize(key)

/*
 * The schema titles of the place an error is in, such as "Repositories > releases > Visibility". An
 * entry of a list is named by its id, and a missing required field by its name.
 */
const labelOf = (schema, data, instancePath, missingProperty) => {
  const segments = instancePath.split('/').slice(1)
  const labels = []
  let node = schema
  let value = data
  for (const segment of segments) {
    if (node?.items !== undefined && Array.isArray(value)) {
      const entry = value[Number(segment)]
      labels.push(entry?.id ?? `#${Number(segment) + 1}`)
      node = node.items
      value = entry
    } else {
      const child = node?.properties?.[segment]
      labels.push(titleOf(child, segment))
      node = child
      value = value?.[segment]
    }
  }
  if (missingProperty !== undefined) labels.push(titleOf(node?.properties?.[missingProperty], missingProperty))
  return labels.join(' > ')
}

const messageOf = (error) => {
  if (error.keyword === 'enum') {
    return `must be one of ${error.params.allowedValues.map(value => JSON.stringify(value)).join(', ')}`
  }
  return error.message
}

/*
 * Checks the file against the schemas of the domains it names. Returns { ok, domains, errors }: `domains`
 * holds the checked values, with the schema defaults filled in, only when there are no errors. An error
 * is { domain, label, message }; `label` is empty when the error is the domain as a whole.
 */
export const validateImport = (file, { domains, schemas, validator }) => {
  const errors = []
  const values = {}

  for (const domain of Object.keys(file)) {
    if (!domains.includes(domain)) {
      errors.push({ domain, label: '', message: 'Unknown settings domain' })
      continue
    }
    const data = JSON.parse(JSON.stringify(file[domain]))
    let validate
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
