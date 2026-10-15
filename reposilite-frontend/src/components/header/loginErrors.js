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

import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createErrorToast } from '../../helpers/toast'

/*
 * A single sign on provider that could not finish the sign in sends the visitor back with
 * ?loginError=<code> on the page they started from. The code is shown once, in words a visitor can
 * act on, and then taken out of the address so that a reload does not show it again.
 */
const MESSAGES = {
  access_denied: 'The provider did not grant access. Try another account, or ask for access there.',
  invalid_state: 'The sign in expired or was started in another browser. Start it again from here.',
  email_domain_not_allowed: 'Your email domain is not allowed to sign in to this instance.',
  provider_error: 'The provider could not complete the sign in. Try again in a moment.'
}

const GENERIC_MESSAGE = 'The sign in did not complete. Try again, or use another sign in method.'

const loginErrorMessage = (code) => MESSAGES[code] ?? GENERIC_MESSAGE

// The code of the address, or undefined. A repeated parameter counts by its first value.
const loginErrorOf = (query) => {
  const value = Array.isArray(query.loginError) ? query.loginError[0] : query.loginError
  return typeof value === 'string' && value !== '' ? value : undefined
}

// Shows the failure of a provider sign in, if the address names one, and removes it from the address.
// Every other query parameter stays where it is.
const useLoginErrorNotice = () => {
  const route = useRoute()
  const router = useRouter()

  watch(
    () => loginErrorOf(route.query),
    (code) => {
      if (code === undefined) return
      createErrorToast(loginErrorMessage(code))
      const query = { ...route.query }
      delete query.loginError
      router.replace({ path: route.path, query, hash: route.hash })
    },
    { immediate: true }
  )
}

export { loginErrorMessage, loginErrorOf, useLoginErrorNotice }
