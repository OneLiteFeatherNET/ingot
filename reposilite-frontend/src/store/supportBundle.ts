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
 * Reads whether the instance offers the support pages, once a visitor is signed in. An anonymous visitor is not asked:
 * the endpoint answers 401 to anyone else, and the pages are not offered to anyone who is not signed in. An opt-out
 * instance answers 404, which means no support page is listed.
 */

import axios, { type AxiosRequestConfig, type AxiosRequestHeaders } from 'axios'
import { ref } from 'vue'
import { createURL, handleRequest } from './client'
import { setSupportBundleOffered } from '../navigation/registry'
import type { components } from '../types/api'

// False until the answer for the current session is known, so a support page that waits for it is not judged.
const supportBundleReady = ref(false)

const loadSupportBundle = async (credentials: Pick<AxiosRequestConfig, 'headers'> = {}) => {
  supportBundleReady.value = false
  try {
    const { data } = await axios.get<components['schemas']['SupportAvailability']>(createURL('/api/admin/support/availability'), handleRequest({ withCredentials: true, headers: { ...credentials.headers } as AxiosRequestHeaders }))
    setSupportBundleOffered(data?.enabled === true)
  } catch {
    setSupportBundleOffered(false)
  } finally {
    supportBundleReady.value = true
  }
}

const clearSupportBundle = () => {
  setSupportBundleOffered(false)
  supportBundleReady.value = true
}

export { supportBundleReady, loadSupportBundle, clearSupportBundle }
