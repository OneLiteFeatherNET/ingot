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

// Writes docs/gitbook/reference/dashboard-anchors.md from src/anchors/registry.json. Run with `npm run anchors:doc`.

import { readFileSync, writeFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { renderAnchorsDoc } from "../src/anchors/render.js"

const frontendRoot = fileURLToPath(new URL("..", import.meta.url))
const registry = JSON.parse(readFileSync(`${frontendRoot}/src/anchors/registry.json`, "utf8"))
const target = fileURLToPath(new URL("../../docs/gitbook/reference/dashboard-anchors.md", import.meta.url))

writeFileSync(target, renderAnchorsDoc(registry))
console.log(`wrote ${target}`)
