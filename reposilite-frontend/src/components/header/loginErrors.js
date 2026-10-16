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
// One sentence for each code of the callback (identity model, section 14.4). The codes stay in the address.
const MESSAGES = {
  access_denied: 'The provider did not grant access. Try another account, or ask for access there.',
  invalid_state: 'The sign in expired or was started in another browser. Start it again from here.',
  provider_error: 'The provider could not complete the sign in. Try again in a moment.',
  token_exchange_failed: 'The provider did not accept the sign in. Try again, or ask an administrator to check the client settings.',
  invalid_id_token: 'The answer of the provider could not be verified. Ask an administrator to check the provider settings.',
  email_domain_not_allowed: 'Your email domain is not allowed to sign in to this instance.',
  user_disabled: 'This account is disabled. Ask an administrator to enable it.',
  user_not_provisioned: 'No account exists for you on this instance yet. Ask an administrator to create one.',
  username_taken: 'Another account already uses your user name here. Ask an administrator to resolve it.',
  invalid_username: 'Your user name at the provider is not a valid user name on this instance.',
  setup_required: 'This instance has no administrator yet. Create the first administrator with the setup code from the log.',
  provider_unavailable: 'The provider cannot be reached right now. Try again in a moment.',
  no_database: 'Signing in needs the database of this instance, which is not available right now.'
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
