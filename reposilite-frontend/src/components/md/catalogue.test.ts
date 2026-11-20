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

// Reads files and nothing else: no DOM, no clock, no network. The primitives are the .vue files of this folder, so a new
// primitive is covered without a list to update here.
const MD_DIR = resolve(import.meta.dirname)
const DESIGN_FILE = resolve(import.meta.dirname, "../../../DESIGN.md")

const primitives = readdirSync(MD_DIR)
  .filter((file) => file.endsWith(".vue"))
  .map((file) => file.replace(/\.vue$/, ""))
  .sort()

const designLines = readFileSync(DESIGN_FILE, "utf8").split("\n")
const headings = designLines
  .map((line) => line.match(/^(#{1,6})\s+(.*)$/))
  .filter((match): match is RegExpMatchArray => match !== null)
  .map((match) => ({ level: match[1].length, text: match[2] }))

// A heading names a primitive when it carries the component name as a whole word, such as "### MdButton" or
// "### Side sheet (`MdSideSheet`)".
const primitivesNamedBy = (text: string) =>
  primitives.filter((name) => new RegExp(`\\b${name}\\b`).test(text))

describe("catalogue of the md primitives", () => {
  it("every primitive has a heading in DESIGN.md", () => {
    const named = new Set(
      headings.flatMap((heading) => primitivesNamedBy(heading.text)),
    )
    const missing = primitives.filter((name) => !named.has(name))

    expect(
      missing,
      "primitives without a catalogue heading in DESIGN.md",
    ).toEqual([])
  })

  it("no heading in DESIGN.md names a primitive file that does not exist", () => {
    const names = headings.flatMap(
      (heading) => heading.text.match(/\bMd[A-Z]\w*\b/g) ?? [],
    )
    const unknown = [...new Set(names)].filter(
      (name) => !primitives.includes(name),
    )

    expect(
      unknown,
      "names in DESIGN.md headings without a file under src/components/md/",
    ).toEqual([])
  })

  it("DESIGN.md has no Known gaps heading", () => {
    const gaps = headings.filter((heading) =>
      /^Known gaps$/i.test(heading.text),
    )

    expect(gaps, "a Known gaps heading is still in DESIGN.md").toEqual([])
  })
})
