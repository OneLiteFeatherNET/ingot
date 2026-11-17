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
 * The sections of a settings form as tabs. The form of a domain is one layout (see entryLayout): a "General" group of the
 * scalar fields, then one control per nested object, each drawn as a titled section. A domain that opts in shows each of
 * those elements as a tab. The helpers know nothing about Vue, so the tab rules can be tested directly.
 */

import type { GroupLayout, UISchemaElement, VerticalLayout } from '@jsonforms/core'
import type { SettingsSchema } from './collections'

/* One tab: its key is the `tab` parameter of the address, and `property` is the field of the domain it draws (null for General). */
export interface SectionTab {
  key: string
  label: string
  property: string | null
  layout: VerticalLayout
}

/* An error as the validator reports it. The path is a JSON pointer to the value, and `required` names the field that is missing. */
export interface SectionError {
  instancePath?: string
  keyword?: string
  params?: Record<string, unknown>
}

/* The part of an ajv instance the tabs use: compiling a schema gives a function that reports its errors. */
export interface SectionValidator {
  compile(schema: unknown): ((data: unknown) => unknown) & { errors?: SectionError[] | null }
}

const CONTROL_SCOPE = /^#\/properties\/([^/]+)$/

// "Rebuild schedule" -> "rebuild-schedule". The key is what the address carries, so it is kept short and readable.
const slugOf = (text: string): string => text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'section'

/*
 * The tabs of a layout, in layout order. The General group becomes the tab "General"; every other element is a control of a
 * nested field and takes that field's title. Two tabs never share a key, so the address always names one of them.
 */
export const tabsOfLayout = (layout: VerticalLayout, schema: SettingsSchema): SectionTab[] => {
  const used = new Set<string>()
  const unique = (base: string): string => {
    let key = base
    for (let suffix = 2; used.has(key); suffix++) key = `${base}-${suffix}`
    used.add(key)
    return key
  }
  const single = (element: UISchemaElement): VerticalLayout => ({ type: 'VerticalLayout', elements: [element] })

  return layout.elements.flatMap((element): SectionTab[] => {
    if (element.type === 'Group') {
      const group = element as GroupLayout
      return [{ key: unique('general'), label: group.label ?? 'General', property: null, layout: single(element) }]
    }
    const match = CONTROL_SCOPE.exec((element as { scope?: string }).scope ?? '')
    if (match === null) return []
    const property = match[1]
    const label = schema.properties?.[property]?.title ?? property
    return [{ key: unique(slugOf(label)), label, property, layout: single(element) }]
  })
}

/* The tab that is shown for the address: the one the `tab` parameter names, else the first. Undefined without tabs. */
export const activeTabKey = (tabs: SectionTab[], requested: unknown): string | undefined => {
  const named = tabs.find(tab => tab.key === requested)
  return (named ?? tabs[0])?.key
}

/*
 * Every error of the domain, as ajv reports it for the data. Ajv fills the defaults into what it validates, so a copy is
 * validated and the draft stays as it is. A schema that does not compile reports nothing: the form reports that itself.
 */
export const validationErrorsOf = (validator: SectionValidator, schema: unknown, data: unknown): SectionError[] => {
  try {
    const validate = validator.compile(schema)
    validate(JSON.parse(JSON.stringify(data)))
    return validate.errors ?? []
  } catch {
    return []
  }
}

// The top-level field an error belongs to: the first segment of its path, or the missing field of a `required` error at the top.
const fieldOf = (error: SectionError): string | undefined => {
  const first = (error.instancePath ?? '').split('/')[1]
  if (first) return first.replace(/~1/g, '/').replace(/~0/g, '~')
  const missing = error.params?.missingProperty
  if (error.keyword === 'required' && typeof missing === 'string') return missing
  return undefined
}

/*
 * The number of errors each tab holds. A field that is not a section (a scalar) belongs to General. Errors of a field no tab
 * draws are not counted, because no tab could show them.
 */
export const errorCountsOf = (tabs: SectionTab[], errors: SectionError[]): Record<string, number> => {
  const counts: Record<string, number> = Object.fromEntries(tabs.map(tab => [tab.key, 0]))
  const general = tabs.find(tab => tab.property === null)
  const sections = new Map(tabs.filter(tab => tab.property !== null).map(tab => [tab.property as string, tab.key]))
  for (const error of errors) {
    const field = fieldOf(error)
    if (field === undefined) continue
    const key = sections.get(field) ?? general?.key
    if (key !== undefined) counts[key] += 1
  }
  return counts
}

/* The first tab, in tab order, that holds an error. Undefined when no tab does. */
export const firstErrorTab = (tabs: SectionTab[], counts: Record<string, number>): string | undefined => tabs.find(tab => (counts[tab.key] ?? 0) > 0)?.key

/* The label of a tab with its error count, as the tab bar shows it: "Rebuild schedule (1 error)". */
export const labelWithErrors = (label: string, count: number): string => {
  if (count === 0) return label
  return `${label} (${count} ${count === 1 ? 'error' : 'errors'})`
}
