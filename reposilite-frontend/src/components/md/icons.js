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

// The glyphs that MdIcon draws by name. Each entry is the `d` string of a 24 by 24 viewBox, drawn as a stroke. A glyph is
// named by the surface it serves until its icon family is decided (proposal question 3), and one path has one name.
export const GLYPHS = Object.freeze({
  // The close glyph of the side sheet, the path of components/icons/CloseIcon.vue.
  'sheet-close': 'M6 18L18 6M6 6l12 12'
})
