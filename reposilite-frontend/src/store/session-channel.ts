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
 * Tells the other tabs of the browser that a cookie session started or ended. A message is
 * {type: "signed-in" | "signed-out", principal?}, and the principal is only the public
 * identity {id, kind, name, displayName}. No password, token or cookie value ever travels:
 * the receiving tab asks the server for its own view of the session. Where BroadcastChannel
 * does not exist the channel is a no-op and each tab simply learns on its next request.
 */
const SESSION_CHANNEL_NAME = 'ingot-session'

const TYPES = ['signed-in', 'signed-out']

const publicPrincipal = (principal) =>
  principal
    ? {
        id: principal.id,
        kind: principal.kind,
        name: principal.name,
        displayName: principal.displayName
      }
    : undefined

// Only the two known types get through, and only with the public fields of the principal.
const parse = (data) => {
  if (!data || typeof data !== 'object' || !TYPES.includes(data.type)) {
    return null
  }
  const message = { type: data.type }
  if (data.type === 'signed-in' && data.principal && typeof data.principal === 'object') {
    message.principal = publicPrincipal(data.principal)
  }
  return message
}

const defaultFactory = (name) => new BroadcastChannel(name)

const openSessionChannel = (factory = defaultFactory) => {
  let transport = null
  try {
    transport = factory(SESSION_CHANNEL_NAME)
  } catch {
    transport = null
  }

  const post = (message) => {
    try {
      transport?.postMessage(message)
    } catch {
      // A closed or unavailable channel must never break a sign in.
    }
  }

  return {
    signedIn(principal) {
      post({ type: 'signed-in', principal: publicPrincipal(principal) })
    },
    signedOut() {
      post({ type: 'signed-out' })
    },
    subscribe(handler) {
      if (!transport) {
        return () => {}
      }
      const listener = (event) => {
        const message = parse(event?.data)
        if (message) handler(message)
      }
      transport.addEventListener('message', listener)
      return () => transport.removeEventListener('message', listener)
    },
    close() {
      try {
        transport?.close()
      } catch {
        // Nothing left to close.
      }
    }
  }
}

export { openSessionChannel, SESSION_CHANNEL_NAME }
