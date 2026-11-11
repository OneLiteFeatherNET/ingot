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

import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import {
  DYNAMIC_ALLOWLIST,
  PENDING_AREAS,
  aliasProblems,
  dynamicProblems,
  listSourceFiles,
  listSourceTree,
  nameProblems,
  partAndStateProblems,
  registryProblems,
  selectorProblems,
} from "./checks.js"
import registry from "./registry.json"
import { renderAnchorsDoc } from "./render.js"

// Reads files and nothing else: no server, no clock, no network. Every fixture below is a string built inside its test.
const FRONTEND_ROOT = resolve(import.meta.dirname, "../..")
const REPO_ROOT = resolve(import.meta.dirname, "../../..")
const read = (relative) => readFileSync(`${FRONTEND_ROOT}/${relative}`, "utf8")

const REPOSITORIES_TABLE = "src/components/repositories/RepositoriesTable.vue"
const NAVIGATION_FILE = "src/components/layout/SideNavLayout.vue"

const entryFor = (name, overrides = {}) => ({
  name,
  file: NAVIGATION_FILE,
  stability: "stable",
  parts: [],
  states: [],
  ...overrides,
})

const registryOf = (components, aliases = []) => ({ contract: 1, components, aliases })

describe("anchor checks on a single template", () => {
  it("accepts a root element that carries its registered name", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"><p>x</p></nav></template>`

    expect(nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))).toEqual([])
  })

  it("reports a root element without data-ingot-component", () => {
    const source = `<template><nav><p>x</p></nav></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("side-nav-layout")])
  })

  it("names the first plain element inside a root that is a child component, not the child itself", () => {
    const source = `<template><MdCard><div data-ingot-component="side-nav-layout"></div></MdCard></template>`

    expect(nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))).toEqual([])
  })

  it("accepts a component that renders no element of its own when it carries no name", () => {
    const source = `<template><MdSelect :model-value="value" /></template>`
    const entry = entryFor("md-select-renderer", { ownElement: false })

    expect(nameProblems(NAVIGATION_FILE, source, entry, registryOf([]))).toEqual([])
  })

  it("reports a name on a component that renders no element of its own", () => {
    const source = `<template><MdSelect data-ingot-component="md-select-renderer" /></template>`
    const entry = entryFor("md-select-renderer", { ownElement: false })

    const problems = nameProblems(NAVIGATION_FILE, source, entry, registryOf([]))

    expect(problems).toEqual([expect.stringContaining("no element of its own")])
  })

  it("reports a component whose template has no plain element to carry the name", () => {
    const source = `<template><slot></slot></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("no plain element")])
  })

  it("accepts the named element inside a root Teleport", () => {
    const source = `<template><Teleport to="body"><div data-ingot-component="side-nav-layout"></div></Teleport></template>`

    expect(nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))).toEqual([])
  })

  it("accepts a multi-root component whose every root carries the name", () => {
    const source = `<template><header data-ingot-component="side-nav-layout"></header><footer data-ingot-component="side-nav-layout"></footer></template>`

    expect(nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))).toEqual([])
  })

  it("reports a root of a multi-root component that lacks the name", () => {
    const source = `<template><header data-ingot-component="side-nav-layout"></header><footer></footer></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("a root element has no")])
  })

  it("reports the name on an element that is not a root", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"><p data-ingot-component="side-nav-layout"></p></nav></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("2 times")])
  })

  it("reports a data-ingot-component value that is not the registered name", () => {
    const source = `<template><nav data-ingot-component="side-nav"></nav></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("side-nav")])
  })

  it("reports an unknown data-ingot attribute", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout" data-ingot-colour="red"></nav></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("data-ingot-colour")])
  })

  it("reports a state value that is outside the closed set", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"><a data-ingot-state="busy"></a></nav></template>`
    const entry = entryFor("side-nav-layout")

    const problems = partAndStateProblems(NAVIGATION_FILE, source, entry)

    expect(problems).toEqual([expect.stringContaining("busy")])
  })

  it("reports a state that the registry does not list for this component", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"><a data-ingot-state="open"></a><i data-ingot-state="current"></i></nav></template>`
    const entry = entryFor("side-nav-layout", { states: ["current"] })

    const problems = partAndStateProblems(NAVIGATION_FILE, source, entry)

    expect(problems).toEqual([expect.stringContaining("open")])
  })

  it("reports a part that the registry does not list", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"><ul data-ingot-part="rail"></ul></nav></template>`

    const problems = partAndStateProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"))

    expect(problems).toEqual([expect.stringContaining("rail")])
  })

  it("reports a registered part that the template never uses", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout"></nav></template>`
    const entry = entryFor("side-nav-layout", { parts: ["rail"] })

    const problems = partAndStateProblems(NAVIGATION_FILE, source, entry)

    expect(problems).toEqual([expect.stringContaining("rail")])
  })

  it("reports a dynamic data-ingot-principal binding, which is not on the allow-list", () => {
    const source = `<template><tr data-ingot-component="side-nav-layout" :data-ingot-principal="row.id"></tr></template>`

    const problems = dynamicProblems(NAVIGATION_FILE, source)

    expect(problems).toEqual([expect.stringContaining("data-ingot-principal")])
  })

  it("accepts the dynamic repository binding on the repository row", () => {
    const source = `<template><tr data-ingot-component="repositories-table" :data-ingot-repository="repo.name"></tr></template>`

    expect(dynamicProblems(REPOSITORIES_TABLE, source)).toEqual([])
  })

  it("reports the dynamic repository binding when it appears in a file that is not allowed", () => {
    const source = `<template><tr :data-ingot-repository="repo.name"></tr></template>`

    const problems = dynamicProblems(NAVIGATION_FILE, source)

    expect(problems).toEqual([expect.stringContaining("data-ingot-repository")])
  })

  it("reports an alias attribute that no registry alias declares for this component", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout" data-ingot-component-alias="side-nav"></nav></template>`

    const problems = nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([]))

    expect(problems).toEqual([expect.stringContaining("side-nav")])
  })

  it("accepts an alias attribute that the registry declares for this component", () => {
    const source = `<template><nav data-ingot-component="side-nav-layout" data-ingot-component-alias="side-nav"></nav></template>`
    const aliases = [{ name: "side-nav", replacedBy: "side-nav-layout", removeIn: "9.0.0" }]

    expect(nameProblems(NAVIGATION_FILE, source, entryFor("side-nav-layout"), registryOf([], aliases))).toEqual([])
  })
})

describe("anchor checks on the registry", () => {
  it("reports a component name that is not kebab-case", () => {
    const problems = registryProblems(registryOf([entryFor("SideNavLayout")]), [NAVIGATION_FILE], [])

    expect(problems).toEqual([expect.stringContaining("SideNavLayout")])
  })

  it("reports a name that two components share", () => {
    const problems = registryProblems(
      registryOf([entryFor("side-nav-layout"), entryFor("side-nav-layout", { file: REPOSITORIES_TABLE })]),
      [NAVIGATION_FILE, REPOSITORIES_TABLE],
      [],
    )

    expect(problems).toEqual([expect.stringContaining("side-nav-layout")])
  })

  it("reports a registry entry whose file is not in the source", () => {
    const problems = registryProblems(
      registryOf([entryFor("side-nav-layout", { file: "src/components/layout/Missing.vue" })]),
      [],
      [],
    )

    expect(problems).toEqual([expect.stringContaining("Missing.vue")])
  })

  it("reports a source component that is neither registered nor in a pending area", () => {
    const problems = registryProblems(registryOf([]), [NAVIGATION_FILE], [])

    expect(problems).toEqual([expect.stringContaining(NAVIGATION_FILE)])
  })

  it("reports a stability value other than stable or experimental", () => {
    const problems = registryProblems(
      registryOf([entryFor("side-nav-layout", { stability: "beta" })]),
      [NAVIGATION_FILE],
      [],
    )

    expect(problems).toEqual([expect.stringContaining("beta")])
  })

  it("reports an alias whose replacement is not a registered component", () => {
    const problems = aliasProblems(
      registryOf([entryFor("side-nav-layout")], [{ name: "side-nav", replacedBy: "gone", removeIn: "9.0.0" }]),
      "1.1.1",
    )

    expect(problems).toEqual([expect.stringContaining("gone")])
  })

  it("reports an alias whose removal version has been reached", () => {
    const problems = aliasProblems(
      registryOf([entryFor("side-nav-layout")], [{ name: "side-nav", replacedBy: "side-nav-layout", removeIn: "1.1.0" }]),
      "1.1.1",
    )

    expect(problems).toEqual([expect.stringContaining("side-nav")])
  })

  it("accepts an alias whose removal version is still ahead", () => {
    const problems = aliasProblems(
      registryOf([entryFor("side-nav-layout")], [{ name: "side-nav", replacedBy: "side-nav-layout", removeIn: "1.2.0" }]),
      "1.1.1",
    )

    expect(problems).toEqual([])
  })
})

describe("anchor checks across the dashboard source", () => {
  const sourceFiles = listSourceFiles(FRONTEND_ROOT)
  const sourceOf = Object.fromEntries(sourceFiles.map((file) => [file.path, read(file.path)]))
  const registered = registry.components

  it("registers every component outside the pending areas, and no component in a pending area", () => {
    const problems = registryProblems(registry, sourceFiles.map((file) => file.path), PENDING_AREAS)

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("names the root element of every registered component with its registered name", () => {
    const problems = registered.flatMap((entry) => nameProblems(entry.file, sourceOf[entry.file], entry, registry))

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("uses only parts and states that the registry lists, and lists only those the template uses", () => {
    const problems = registered.flatMap((entry) => partAndStateProblems(entry.file, sourceOf[entry.file], entry))

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("allows dynamic data-ingot bindings only on the allow-list", () => {
    const problems = registered.flatMap((entry) => dynamicProblems(entry.file, sourceOf[entry.file]))

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("keeps every pending area a real folder, so the exemption cannot hide a typo", () => {
    const folders = new Set(sourceFiles.map((file) => file.area))

    expect(PENDING_AREAS.filter((area) => !folders.has(area))).toEqual([])
  })

  it("selects on no data-ingot attribute outside the anchor suite", () => {
    const tree = listSourceTree(FRONTEND_ROOT).map((path) => ({ path, source: read(path) }))
    const problems = selectorProblems(tree)

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("keeps the dynamic allow-list to the files that exist", () => {
    const missing = DYNAMIC_ALLOWLIST.map((rule) => rule.file).filter((file) => !sourceOf[file])

    expect(missing).toEqual([])
  })

  it("has no alias whose removal version is reached", () => {
    const packageJson = JSON.parse(read("package.json"))
    const problems = aliasProblems(registry, packageJson.version)

    expect(problems, problems.join("\n")).toEqual([])
  })

  it("matches the generated reference page to the registry", () => {
    const page = readFileSync(`${REPO_ROOT}/docs/gitbook/reference/dashboard-anchors.md`, "utf8")

    expect(page, "run `npm run anchors:doc` in reposilite-frontend to regenerate the page").toBe(renderAnchorsDoc(registry))
  })
})
