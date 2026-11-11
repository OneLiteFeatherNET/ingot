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

// Static checks for the data-ingot-* anchors. They read Vue templates with the SFC compiler and never mount a component,
// so they need no DOM, no clock and no network. Used by anchors.test.js and by the generator in scripts/anchors-doc.mjs.

import { parse } from "@vue/compiler-sfc"
import { readdirSync } from "node:fs"
import { join } from "node:path"

const PREFIX = "data-ingot-"
const NODE_ELEMENT = 1
const PROP_ATTRIBUTE = 6
const PROP_DIRECTIVE = 7
const TAG_ELEMENT = 0
const TAG_SLOT = 2

export const KEBAB_CASE = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/
export const STATES = Object.freeze(["current", "loading", "error", "empty", "open"])
export const STABILITY = Object.freeze(["stable", "experimental"])
export const KNOWN_ATTRIBUTES = Object.freeze([
  "data-ingot-component",
  "data-ingot-component-alias",
  "data-ingot-part",
  "data-ingot-state",
  "data-ingot-block",
  "data-ingot-repository",
  "data-ingot-plugin-page",
])

// Folders that are not annotated yet. The suite skips their components and fails if a registry entry sits in one.
// The last batch empties the list.
export const PENDING_AREAS = Object.freeze([
  "access", "identity", "tokens", "account",
  "dashboard", "maintenance", "audit", "console",
  "md", "migration", "util", "support", "pages", "App.vue",
])

// The only dynamic data-ingot-* bindings. Each reads a value that the same element already shows as text (see the
// reference page, Decision 4 and 5). Anything else must be a literal.
export const DYNAMIC_ALLOWLIST = Object.freeze([
  { file: "src/components/repositories/RepositoriesTable.vue", attribute: "data-ingot-repository" },
  { file: "src/components/repositories/RepositoryDetail.vue", attribute: "data-ingot-repository" },
  { file: "src/components/plugins/PluginBlock.vue", attribute: "data-ingot-block" },
  { file: "src/components/plugins/PluginPageView.vue", attribute: "data-ingot-plugin-page" },
])

export function areaOf(file) {
  if (file === "src/App.vue") return "App.vue"
  const parts = file.split("/")
  if (parts[1] === "pages") return "pages"
  return parts[2]
}

function isTransparent(node) {
  return node.tag === "template" || node.tag === "Teleport" || node.tag === "teleport"
}

function elementsIn(children, out = []) {
  for (const node of children) {
    if (node.type === NODE_ELEMENT) {
      out.push(node)
      elementsIn(node.children, out)
    }
  }
  return out
}

// The elements that Vue renders at the top level. A Teleport or a plain template wrapper is looked through, because the
// element inside it is what reaches the page. A slot renders content of its parent, not an element of its own.
function rootsIn(children) {
  return children
    .filter((node) => node.type === NODE_ELEMENT && node.tagType !== TAG_SLOT)
    .flatMap((node) => (isTransparent(node) ? rootsIn(node.children) : [node]))
}

function anchorsOf(node) {
  const found = []
  for (const prop of node.props) {
    if (prop.type === PROP_ATTRIBUTE && prop.name.startsWith(PREFIX)) {
      found.push({ attribute: prop.name, value: prop.value ? prop.value.content : "", dynamic: false })
    } else if (
      prop.type === PROP_DIRECTIVE &&
      prop.name === "bind" &&
      prop.arg &&
      prop.arg.isStatic &&
      prop.arg.content.startsWith(PREFIX)
    ) {
      found.push({ attribute: prop.arg.content, value: prop.exp ? prop.exp.content : "", dynamic: true })
    }
  }
  return found
}

export function inspectTemplate(source, file) {
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length > 0) {
    throw new Error(`${file}: ${errors[0].message}`)
  }
  const children = descriptor.template ? descriptor.template.ast.children : []
  const elements = elementsIn(children)
  return {
    roots: rootsIn(children),
    elements,
    anchors: elements.flatMap((element) => anchorsOf(element)),
  }
}

// The plain elements that carry the name: every plain root element, or, when the component's roots are all other
// components (a card or a dialog around its content), the first plain element of its template.
function carriersOf(roots, elements) {
  const native = roots.filter((node) => node.tagType === TAG_ELEMENT)
  if (native.length > 0) return native
  return elements.filter((node) => node.tagType === TAG_ELEMENT).slice(0, 1)
}

// The root name, the closed attribute set, and the alias attributes of one component.
export function nameProblems(file, source, entry, registry) {
  const { roots, elements, anchors } = inspectTemplate(source, file)
  // A component that only hands its props to one child component renders no element of its own. A name on it would
  // land on the child's element, so it has none, and the registry says so with ownElement: false.
  if (entry.ownElement === false) {
    return anchors.some((anchor) => anchor.attribute === "data-ingot-component")
      ? [`${file}: "${entry.name}" renders no element of its own, so it must not carry data-ingot-component`]
      : []
  }
  const carriers = carriersOf(roots, elements)
  if (carriers.length === 0) {
    return [`${file}: no plain element is there to carry the name, so the component cannot be anchored`]
  }

  const problems = []
  // A component with several root elements (v-if branches, a Teleport) names each of them, so a selector reaches
  // whichever branch is on screen. No other element of the component carries the name.
  for (const element of carriers) {
    const rootAnchor = anchorsOf(element).find((anchor) => anchor.attribute === "data-ingot-component")
    if (!rootAnchor) {
      problems.push(`${file}: a root element has no data-ingot-component="${entry.name}"`)
    } else if (rootAnchor.dynamic || rootAnchor.value !== entry.name) {
      problems.push(`${file}: data-ingot-component is "${rootAnchor.value}", the registry says "${entry.name}"`)
    }
  }

  const componentAttributes = anchors.filter((anchor) => anchor.attribute === "data-ingot-component")
  if (componentAttributes.length > carriers.length) {
    problems.push(
      `${file}: data-ingot-component appears ${componentAttributes.length} times, but the component has ${carriers.length} named element(s)`,
    )
  }

  for (const anchor of anchors) {
    if (!KNOWN_ATTRIBUTES.includes(anchor.attribute)) {
      problems.push(`${file}: unknown anchor attribute ${anchor.attribute}`)
    }
  }

  const declared = (registry.aliases ?? []).filter((alias) => alias.replacedBy === entry.name).map((alias) => alias.name)
  const present = anchors.filter((anchor) => anchor.attribute === "data-ingot-component-alias").map((anchor) => anchor.value)
  for (const value of present) {
    if (!declared.includes(value)) {
      problems.push(`${file}: alias "${value}" is not declared in the registry for "${entry.name}"`)
    }
  }
  for (const name of declared) {
    if (!present.includes(name)) {
      problems.push(`${file}: the registry alias "${name}" is missing from "${entry.name}"`)
    }
  }
  return problems
}

export function partAndStateProblems(file, source, entry) {
  const { anchors } = inspectTemplate(source, file)
  const parts = anchors.filter((anchor) => anchor.attribute === "data-ingot-part")
  const states = anchors.filter((anchor) => anchor.attribute === "data-ingot-state")
  const problems = []

  for (const part of parts) {
    if (part.dynamic) {
      problems.push(`${file}: data-ingot-part must be a literal`)
    } else if (!entry.parts.includes(part.value)) {
      problems.push(`${file}: part "${part.value}" is not in the registry for "${entry.name}"`)
    }
  }
  for (const state of states) {
    if (state.dynamic) {
      problems.push(`${file}: data-ingot-state must be a literal`)
    } else if (!STATES.includes(state.value)) {
      problems.push(`${file}: state "${state.value}" is not in the closed set ${STATES.join(", ")}`)
    } else if (!entry.states.includes(state.value)) {
      problems.push(`${file}: state "${state.value}" is not in the registry for "${entry.name}"`)
    }
  }
  for (const part of entry.parts) {
    if (!parts.some((anchor) => anchor.value === part)) {
      problems.push(`${file}: registered part "${part}" is not used by "${entry.name}"`)
    }
  }
  for (const state of entry.states) {
    if (!states.some((anchor) => anchor.value === state)) {
      problems.push(`${file}: registered state "${state}" is not used by "${entry.name}"`)
    }
  }
  return problems
}

export function dynamicProblems(file, source) {
  const { anchors } = inspectTemplate(source, file)
  return anchors
    .filter((anchor) => anchor.dynamic)
    .filter((anchor) => !DYNAMIC_ALLOWLIST.some((rule) => rule.file === file && rule.attribute === anchor.attribute))
    .map((anchor) => `${file}: dynamic ${anchor.attribute} binding is not on the allow-list`)
}

export function registryProblems(registry, files, pendingAreas) {
  const problems = []
  const counts = new Map()
  const registered = new Set()

  for (const entry of registry.components) {
    if (!KEBAB_CASE.test(entry.name)) {
      problems.push(`component name "${entry.name}" is not kebab-case`)
    }
    counts.set(entry.name, (counts.get(entry.name) ?? 0) + 1)
    if (!files.includes(entry.file)) {
      problems.push(`"${entry.name}": ${entry.file} is not in the source`)
    }
    if (pendingAreas.includes(areaOf(entry.file))) {
      problems.push(`"${entry.name}": ${entry.file} is in a pending area, so it must not be registered yet`)
    }
    if (!STABILITY.includes(entry.stability)) {
      problems.push(`"${entry.name}": stability "${entry.stability}" is neither stable nor experimental`)
    }
    registered.add(entry.file)
  }
  for (const [name, count] of counts) {
    if (count > 1) {
      problems.push(`component name "${name}" is used ${count} times`)
    }
  }
  for (const file of files) {
    if (!registered.has(file) && !pendingAreas.includes(areaOf(file))) {
      problems.push(`${file} is neither registered nor in a pending area`)
    }
  }
  return problems
}

export function aliasProblems(registry, version) {
  const names = new Set(registry.components.map((entry) => entry.name))
  const problems = []
  for (const alias of registry.aliases ?? []) {
    if (!names.has(alias.replacedBy)) {
      problems.push(`alias "${alias.name}" points at "${alias.replacedBy}", which is not a registered component`)
    }
    if (compareVersions(version, alias.removeIn) >= 0) {
      problems.push(`alias "${alias.name}" reached its removal version ${alias.removeIn}, remove it from the registry and the source`)
    }
  }
  return problems
}

function compareVersions(a, b) {
  const left = a.split(".").map(Number)
  const right = b.split(".").map(Number)
  for (let i = 0; i < 3; i++) {
    if ((left[i] ?? 0) !== (right[i] ?? 0)) {
      return (left[i] ?? 0) - (right[i] ?? 0)
    }
  }
  return 0
}

const SELECTOR = /\[data-ingot|querySelector(All)?\([^)]*data-ingot|closest\([^)]*data-ingot/

export function selectorProblems(files) {
  return files
    .filter((file) => !file.path.startsWith("src/anchors/"))
    .filter((file) => SELECTOR.test(file.source))
    .map((file) => `${file.path}: selects on a data-ingot attribute, plugins must not depend on this from the source`)
}

// Every component of the dashboard that carries an anchor: components without the icons, the pages and App.vue.
export function listSourceFiles(frontendRoot) {
  const src = join(frontendRoot, "src")
  const relative = [
    ...readdirSync(join(src, "components"), { recursive: true }).map((path) => `components/${path}`),
    ...readdirSync(join(src, "pages"), { recursive: true }).map((path) => `pages/${path}`),
    "App.vue",
  ]
  return relative
    .filter((path) => path.endsWith(".vue"))
    .filter((path) => !path.startsWith("components/icons/"))
    .map((path) => `src/${path}`)
    .sort()
    .map((path) => ({ path, area: areaOf(path) }))
}

// Every .js and .vue file under src, for the selector rule.
export function listSourceTree(frontendRoot) {
  return readdirSync(join(frontendRoot, "src"), { recursive: true })
    .filter((path) => path.endsWith(".js") || path.endsWith(".vue"))
    .map((path) => `src/${path}`)
    .sort()
}
