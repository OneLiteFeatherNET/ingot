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

import type { JsonFormsRendererRegistryEntry } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'

// Tests only. A tester and a renderer imported from a .vue file are not typed for the linter, and the vanilla package
// types its list as any[]. One cast here keeps every renderer list in the tests typed.
export const rendererOf = (tester: unknown, renderer: unknown): JsonFormsRendererRegistryEntry => ({ tester, renderer } as JsonFormsRendererRegistryEntry)

export const vanilla = vanillaRenderers as JsonFormsRendererRegistryEntry[]
