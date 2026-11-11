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

// Renders the reference page from the registry alone. The output is deterministic: the same registry always gives the
// same bytes, which is what the docs-sync check in anchors.test.js compares against.

const code = (values) => (values.length === 0 ? "none" : values.map((value) => `\`${value}\``).join(", "))

export function renderAnchorsDoc(registry) {
  const components = [...registry.components].sort((a, b) => a.name.localeCompare(b.name))
  const aliases = [...(registry.aliases ?? [])].sort((a, b) => a.name.localeCompare(b.name))

  const lines = [
    "---",
    "description: Reference for the data-ingot-* names on the Ingot dashboard, which components and parts carry them, the state values and the stability of each name.",
    "---",
    "",
    "# Dashboard anchors",
    "",
    "<!-- Generated from reposilite-frontend/src/anchors/registry.json by `npm run anchors:doc`. Change the registry, not this page. -->",
    "",
    `This page lists every stable name that the dashboard puts into the DOM, contract ${registry.contract}. A plugin author or an operator who builds a dashboard can target these names instead of the utility classes, which change with each restyle. For what a plugin can use today, see [Extend the dashboard from a plugin](../how-to/extend-the-dashboard-from-a-plugin.md).`,
    "",
    "## Attributes",
    "",
    "| Attribute | Meaning |",
    "| --- | --- |",
    "| `data-ingot-component` | The component, on its root element. The value is the name in the table below. |",
    "| `data-ingot-part` | A named inner part of a component, such as its header or its actions. Listed per component. |",
    "| `data-ingot-state` | The state a part or component is in. One of `current`, `loading`, `error`, `empty` or `open`. Listed per component. |",
    "| `data-ingot-component-alias` | The old name of a renamed component, kept for one minor release. Present only while an alias is listed below. |",
    "| `data-ingot-repository` | The repository name, on the repository row and on the repository header. The name is already on screen. |",
    "| `data-ingot-block` | The type of a plugin block, one of the closed set of block types. |",
    "| `data-ingot-plugin-page` | The id of a plugin page, when it passes the same validation as on the server. |",
    "",
    "## Stability",
    "",
    "A `stable` name is part of the contract. It is added in a minor release, and it is renamed or removed only through an alias that lives for one minor release. An `experimental` name may change in any release.",
    "",
    "## Components",
    "",
    "| Name | Stability | Source | Parts | States |",
    "| --- | --- | --- | --- | --- |",
    ...components.map(
      (entry) =>
        `| \`${entry.name}\`${entry.ownElement === false ? " (no element of its own)" : ""} | ${entry.stability} | \`${entry.file}\` | ${code(entry.parts)} | ${code(entry.states)} |`,
    ),
    "",
    "## Aliases",
    "",
    aliases.length === 0
      ? "No alias is in effect."
      : [
          "| Old name | Replaced by | Removed in |",
          "| --- | --- | --- |",
          ...aliases.map((alias) => `| \`${alias.name}\` | \`${alias.replacedBy}\` | ${alias.removeIn} |`),
        ].join("\n"),
    "",
    "## Selecting in tests",
    "",
    "End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The attributes stay useful for structural checks and for plugins, which cannot read a role from a utility class.",
    "",
  ]
  return `${lines.join("\n")}\n`
}
