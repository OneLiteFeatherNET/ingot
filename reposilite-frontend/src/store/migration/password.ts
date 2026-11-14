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
 * Holds a literal password while the wizard is open. The value sits in a closure, not in reactive state, so
 * no store, devtools view, serialised state or template can show it. It is needed again for each call that
 * carries the credential, and it is forgotten when the visitor leaves the wizard, signs out, gets a 401 or
 * asks for it. Nothing is written to storage or to the address.
 *
 * Only a counter is reactive: it changes when the holder changes, so a view that asks whether a password is
 * held is updated. The password itself is never read through the counter.
 */

import { ref } from 'vue'

const createPasswordHolder = () => {
  let value = null
  const changes = ref(0)
  const changed = () => {
    changes.value += 1
  }
  return {
    remember(password) {
      value = password ? String(password) : null
      changed()
    },
    recall() {
      // Reading the counter makes a computed that asks whether a password is held follow the holder.
      void changes.value
      return value
    },
    forget() {
      value = null
      changed()
    },
  }
}

export { createPasswordHolder }
