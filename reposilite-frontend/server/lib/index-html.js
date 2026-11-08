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

// The build leaves one placeholder in the document: the base element that anchors every relative URL of the
// page. The server fills it in once, at start, from INGOT_BASE_PATH. The other placeholders are replaced at
// image build time, because they carry no runtime value.
export function applyBasePath(html, basePath) {
  return html.replaceAll('{{REPOSILITE.BASE_PATH}}', basePath)
}
