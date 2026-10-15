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
 * Routes marked `manager` in their meta need the manager permission. Three visitors meet
 * them differently:
 *
 * - a manager goes straight in;
 * - an anonymous visitor stays on the URL they asked for. The page shows a prompt in place of
 *   the section, and asks for the sign in dialog itself once it is on screen, because the
 *   dialog lives in the page header and a request made before the page exists is lost;
 * - a signed in user without the permission is sent to overview with a notice, because
 *   signing in again would not change anything.
 *
 * Nothing is decided before the session is known, or a manager would be bounced to overview
 * while the page is still loading.
 */

const managerAccess = ({ isManager, isLogged }) => {
  if (isManager) return 'granted'
  return isLogged ? 'denied' : 'sign-in'
}

const createManagerGuard = ({
  whenSessionKnown,
  isManager,
  isLogged,
  notify
}) => async (to) => {
  if (!to.meta?.manager) {
    return true
  }

  await whenSessionKnown()

  switch (managerAccess({ isManager: isManager(), isLogged: isLogged() })) {
    case 'granted':
    case 'sign-in':
      return true
    default:
      notify('That section is only open to managers.')
      return { name: 'overview', replace: true }
  }
}

/*
 * While the server asks for a new password (mustChangePassword), every route but the account
 * page redirects there. The flag is read as it stands: the guard does not wait for the session,
 * so a plain page load is not held back, and the page itself sends the user to the account page
 * when the flag arrives with the session.
 */
const createPasswordChangeGuard = ({ mustChangePassword }) => (to) => {
  if (!mustChangePassword() || to.name === 'account') {
    return true
  }
  return { name: 'account', replace: true }
}

export { managerAccess, createManagerGuard, createPasswordChangeGuard }
