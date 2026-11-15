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

import { ref } from 'vue'

/*
 * A request to open the sign in dialog. The dialog lives in the header, but a view deep in
 * the page can need it, for instance when a session expires under the console. Such a view
 * calls openLogin(), and the dialog watches the counter. A counter is used rather than a
 * boolean so that asking again while the dialog is already closed opens it again.
 */
const loginRequests = ref(0)

const openLogin = () => {
  loginRequests.value++
}

export { loginRequests, openLogin }
