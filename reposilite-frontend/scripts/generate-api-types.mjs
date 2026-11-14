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

// Generates src/types/api.ts from docs/openapi/ingot-openapi.json. Run with `npm run types:api`.
// `--output <file>` writes somewhere else; the freshness test in src/types/api.test.ts uses it.

import { writeFileSync } from "node:fs"
import { resolve } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"
import openapiTS, { astToString } from "openapi-typescript"

const frontendDir = fileURLToPath(new URL("..", import.meta.url))
const input = resolve(frontendDir, "../docs/openapi/ingot-openapi.json")

const args = process.argv.slice(2)
const outputFlag = args.indexOf("--output")
const output = outputFlag === -1 ? resolve(frontendDir, "src/types/api.ts") : resolve(args[outputFlag + 1])

const ast = await openapiTS(pathToFileURL(input))
writeFileSync(output, astToString(ast))
console.log(`wrote ${output}`)
