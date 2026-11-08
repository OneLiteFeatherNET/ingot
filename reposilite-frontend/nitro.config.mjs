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

// The dashboard image's server. It is built after the dashboard build (npm run build:dashboard) and runs as
// `node .output/server/index.mjs`. The assets are bundled into the output, so the image has no other files to
// read and nothing it has to write.
import { fileURLToPath } from 'node:url'

export default {
  preset: 'node-server',
  compatibilityDate: '2026-11-08',
  srcDir: 'server',
  // Nitro resolves a relative directory against srcDir, so the build output is named from this file.
  serverAssets: [{ baseName: 'dashboard', dir: fileURLToPath(new URL('./build/dashboard', import.meta.url)) }],
  // The console socket is relayed through the upgrade hooks of the catch-all route.
  experimental: { websocket: true },
  output: { dir: '.output' },
}
