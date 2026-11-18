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
import { readFileSync, readdirSync } from "node:fs"
import { resolve } from "node:path"
import { compileScript, parse } from "@vue/compiler-sfc"

// The API convention of DESIGN.md, checked on the declared props, emits and slots of every primitive. The SFC compiler
// reads the files and nothing mounts, so there is no DOM, no clock and no network. Deprecated names are the rows of the
// "Deprecated aliases" table under "API convention" in DESIGN.md; a name that is not listed there is a violation.
const FRONTEND_ROOT = resolve(import.meta.dirname, "../../..")
const MD_DIR = resolve(import.meta.dirname)
const DESIGN_FILE = resolve(FRONTEND_ROOT, "DESIGN.md")

// The proposal names the icon slot on these two components only. Every other icon slot is leading or trailing.
const ICON_SLOT_COMPONENTS = ["MdButton", "MdChip"]

// Known violations that a later task removes. Each one must still be a violation, so that a fixed entry is noticed and
// taken off this list.
const PENDING: Array<{
  component: string;
  name: string;
  kind: string;
  reason: string;
}> = [
  {
    component: "MdTextField",
    name: "leading-icon",
    kind: "slot",
    reason:
      "the field family moves onto MdFieldShell in task 3.2, which renames the icon slots",
  },
  {
    component: "MdTextField",
    name: "trailing-icon",
    kind: "slot",
    reason:
      "the field family moves onto MdFieldShell in task 3.2, which renames the icon slots",
  },
  {
    component: "MdNavigationBar",
    name: "icon",
    kind: "slot",
    reason:
      "the navigation pair is built on MdNavigationItem in task 3.4, which decides the slot name",
  },
  {
    component: "MdNavigationRail",
    name: "icon",
    kind: "slot",
    reason:
      "the navigation pair is built on MdNavigationItem in task 3.4, which decides the slot name",
  },
]

interface Surface {
  component: string;
  props: Map<string, string>;
  emits: string[];
  slots: string[];
}

interface Alias {
  component: string;
  name: string;
  kind: string;
}

interface TemplateNode {
  type: number;
  tag?: string;
  props?: Array<{ type: number; name: string; value?: { content: string } }>;
  children?: TemplateNode[];
  branches?: TemplateNode[];
}

const ELEMENT = 1

const collectSlots = (node: TemplateNode, out: string[]) => {
  if (node.type === ELEMENT && node.tag === "slot") {
    const name = node.props?.find(
      (prop) => prop.type === 6 && prop.name === "name",
    )
    out.push(name?.value?.content ?? "default")
  }
  node.children?.forEach((child) => collectSlots(child, out))
  node.branches?.forEach((branch) => collectSlots(branch, out))
}

// The compiled script declares each prop with its runtime type, for both the type based and the object based forms.
const readSurface = (file: string): Surface => {
  const component = file
    .split("/")
    .pop()!
    .replace(/\.vue$/, "")
  const { descriptor, errors } = parse(readFileSync(file, "utf8"), {
    filename: file,
  })
  if (errors.length > 0)
    throw new Error(`${component} does not parse: ${String(errors[0])}`)

  const props = new Map<string, string>()
  let emits: string[] = []
  if (descriptor.script || descriptor.scriptSetup) {
    const compiled = compileScript(descriptor, { id: component })
    for (const [name, binding] of Object.entries(compiled.bindings ?? {})) {
      if ((binding as string) !== "props") continue
      const type = compiled.content.match(
        new RegExp(`\\b${name}: \\{ type: (\\[[^\\]]*\\]|\\w+)`),
      )
      props.set(name, type?.[1] ?? "Any")
    }
    const declared = compiled.content.match(/emits: \[([^\]]*)\]/)
    emits = [...(declared?.[1] ?? "").matchAll(/["']([^"']+)["']/g)].map(
      (match) => match[1],
    )
  }

  const slots: string[] = []
  if (descriptor.template)
    collectSlots(descriptor.template.ast as unknown as TemplateNode, slots)

  return { component, props, emits, slots }
}

// Rows of the first markdown table under the "Deprecated aliases" heading of "API convention".
const readAliases = (): Alias[] => {
  const section =
    readFileSync(DESIGN_FILE, "utf8").match(
      /^## API convention\n([\s\S]*?)(?=^## )/m,
    )?.[1] ?? ""
  return section
    .split("\n")
    .filter((line) => line.startsWith("|"))
    .map((line) =>
      line
        .split("|")
        .slice(1, -1)
        .map((cell) => cell.trim().replace(/`/g, "")),
    )
    .filter(
      (cells) =>
        cells.length >= 3 && cells[0] !== "Component" && !/^-+$/.test(cells[0]),
    )
    .map(([component, name, kind]) => ({ component, name, kind }))
}

const surfaces = [
  ...readdirSync(MD_DIR)
    .filter((file) => file.endsWith(".vue"))
    .map((file) => readSurface(resolve(MD_DIR, file))),
  // CardMenu sits outside md/, and the proposal names its emit, so it is read here as well.
  readSurface(resolve(FRONTEND_ROOT, "src/components/card/CardMenu.vue")),
]
const aliases = readAliases()
const isAlias = (component: string, name: string, kind: string) =>
  aliases.some(
    (alias) =>
      alias.component === component &&
      alias.name === name &&
      alias.kind === kind,
  )
const isPending = (component: string, name: string, kind: string) =>
  PENDING.some(
    (pending) =>
      pending.component === component &&
      pending.name === name &&
      pending.kind === kind,
  )

const KEBAB = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/
const UPDATE_EVENT = /^update:[a-z][a-zA-Z0-9]*$/

describe("API convention of the md primitives", () => {
  it("no prop takes a class string unless it is a listed deprecated alias", () => {
    const violations = surfaces.flatMap((surface) =>
      [...surface.props]
        .filter(([name, type]) => /class/i.test(name) && type === "String")
        .filter(([name]) => !isAlias(surface.component, name, "prop"))
        .map(([name]) => `${surface.component}.${name}`),
    )

    expect(
      violations,
      "class string props that are not deprecated aliases in DESIGN.md",
    ).toEqual([])
  })

  it("every emit is kebab-case unless it is a listed deprecated alias", () => {
    const violations = surfaces.flatMap((surface) =>
      surface.emits
        .filter((event) => !KEBAB.test(event) && !UPDATE_EVENT.test(event))
        .filter((event) => !isAlias(surface.component, event, "emit"))
        .map((event) => `${surface.component} emits ${event}`),
    )

    expect(
      violations,
      "emits that are neither kebab-case nor listed deprecated aliases",
    ).toEqual([])
  })

  it("every icon slot is leading or trailing, and icon is only a slot of MdButton and MdChip", () => {
    const violations = surfaces.flatMap((surface) =>
      surface.slots
        .filter(
          (slot) =>
            /icon/i.test(slot) && slot !== "leading" && slot !== "trailing",
        )
        .filter((slot) => !isPending(surface.component, slot, "slot"))
        .filter(
          (slot) =>
            !(
              slot === "icon" &&
              ICON_SLOT_COMPONENTS.includes(surface.component) &&
              isAlias(surface.component, slot, "slot")
            ),
        )
        .map((slot) => `${surface.component} slot ${slot}`),
    )

    expect(violations, "icon slots that are not leading or trailing").toEqual(
      [],
    )
  })

  it("a toggle prop is a Boolean unless it is a listed deprecated alias", () => {
    const violations = surfaces.flatMap((surface) => {
      const type = surface.props.get("toggle")
      return type !== undefined &&
        type !== "Boolean" &&
        !isAlias(surface.component, "toggle", "prop")
        ? [`${surface.component}.toggle is ${type}`]
        : []
    })

    expect(
      violations,
      "toggle props that are not Boolean and not listed deprecated aliases",
    ).toEqual([])
  })

  it("every pending violation still violates the convention, so a fixed one leaves the list", () => {
    const stale = PENDING.filter((pending) => {
      const surface = surfaces.find(
        (candidate) => candidate.component === pending.component,
      )
      return surface === undefined || !surface.slots.includes(pending.name)
    })

    expect(stale, "pending entries whose violation is gone").toEqual([])
  })

  it("every deprecated alias names a primitive or CardMenu that exists", () => {
    const unknown = aliases.filter(
      (alias) =>
        !surfaces.some((surface) => surface.component === alias.component),
    )

    expect(
      unknown,
      "deprecated aliases of components that do not exist",
    ).toEqual([])
  })
})
