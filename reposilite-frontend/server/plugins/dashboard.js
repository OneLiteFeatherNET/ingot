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

// Runs before the server listens. The configuration is read synchronously, so a value the server cannot use
// throws here and the process exits with the message before it answers a request. The document is read from
// the bundled assets as soon as the process starts; a document missing from the image ends the process too.

/* global defineNitroPlugin */

import { useStorage } from 'nitropack/runtime'
import segments from '../../src/router/dashboard-segments.json'
import { readDashboardConfig } from '../lib/config.js'
import { applyBasePath } from '../lib/index-html.js'
import { setDashboard } from '../lib/state.js'

export default defineNitroPlugin(() => {
  const config = readDashboardConfig(process.env)
  for (const warning of config.warnings) {
    console.warn(`ingot-dashboard: ${warning}`)
  }

  const storage = useStorage('assets:dashboard')

  let document = null
  const loadDocument = () => {
    if (!document) {
      document = storage.getItemRaw('index.html').then((raw) => {
        if (raw === null) {
          throw new Error('the dashboard document index.html is missing from the image')
        }
        return applyBasePath(Buffer.from(raw).toString('utf8'), config.basePath)
      })
    }
    return document
  }
  loadDocument().catch((error) => {
    console.error(`ingot-dashboard: ${error.message}`)
    process.exit(1)
  })

  setDashboard({ config, segments, storage, document: loadDocument })
})
