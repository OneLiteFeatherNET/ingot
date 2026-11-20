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

/*
 * Loads the manifest of the plugin pages once a visitor is signed in. An anonymous visitor gets no
 * manifest: the pages are for signed in callers, and the server answers 401 to anyone else.
 *
 * The reader pages come from a manifest of their own, which the server answers for every visitor. It carries no
 * stylesheet and no list of enabled plugins.
 */

import axios, { type AxiosRequestConfig, type AxiosRequestHeaders } from 'axios'
import { ref } from 'vue'
import { createURL, handleRequest } from './client'
import { setContributions, setReaderPages } from '../navigation/registry'
import { applyPluginStyles } from './pluginStyles'
import type { components } from '../types/api'

// False until the manifest of the current session is known, so a page that is waiting for it is not judged.
const pluginUiReady = ref(false)

const loadPluginUi = async (credentials: Pick<AxiosRequestConfig, 'headers'> = {}) => {
  pluginUiReady.value = false
  try {
    const { data } = await axios.get<components['schemas']['PluginUiManifest']>(createURL('/api/frontend/plugin-ui'), handleRequest({ withCredentials: true, headers: { ...credentials.headers } as AxiosRequestHeaders }))
    setContributions(data)
    applyPluginStyles(data?.stylesheets)
  } catch {
    setContributions(null)
    applyPluginStyles([])
  } finally {
    pluginUiReady.value = true
  }
}

// False until the reader manifest is known, so a reader page that is waiting for it is not judged.
const readerPagesReady = ref(false)

const loadReaderPages = async () => {
  readerPagesReady.value = false
  try {
    const { data } = await axios.get<components['schemas']['PluginUiReaderManifest']>(createURL('/api/frontend/plugin-ui/readers'))
    setReaderPages(data)
  } catch {
    setReaderPages(null)
  } finally {
    readerPagesReady.value = true
  }
}

const clearPluginUi = () => {
  setContributions(null)
  applyPluginStyles([])
  pluginUiReady.value = true
}

export { pluginUiReady, loadPluginUi, clearPluginUi, readerPagesReady, loadReaderPages }
