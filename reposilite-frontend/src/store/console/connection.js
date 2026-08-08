/*
 * Copyright (c) 2023 dzikoysk
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

import { ref } from "vue"
import { createURL } from '../client'
import { EventSource as Eventsource } from 'extended-eventsource'
import { useSession } from "../session.js"

const { client } = useSession()

const connection = ref()
const command = ref("")

/**
 * Where the stream stands, as something a template can render. `readyState` alone cannot
 * do that job: it is not reactive, and it collapses "never tried" and "gave up" into the
 * same value, which are the two cases a reader most needs told apart.
 *
 * One of 'idle', 'connecting', 'open', 'closed' or 'error'.
 */
const status = ref('idle')
const failure = ref(null)

export default function useConsole() {
  const consoleAddress = createURL("/api/console/log")

  const isConnected = () => {
    // using built-in EventSource for readystate constants
    // the ones from extended-eventsource return undefined for some reason
    return connection.value?.readyState === EventSource.OPEN
  }

  const close = () => {
    // Not gated on isConnected: a stream still opening has a readyState of CONNECTING and
    // would slip through, which is how leaving the tab mid-handshake used to leave the
    // request running.
    connection.value?.close()
    connection.value = undefined
    status.value = 'closed'
  }

  const history = ref([''])
  const historyIdx = ref(0)

  const addCommandToHistory = (command) => {
    if (history.value[history.value.length - 1] == '')
      history.value.pop()
    history.value.push(command)
    historyIdx.value = history.value.length - 1
  }

  const execute = () => {
    addCommandToHistory(command.value)
    client.value.console.execute(command.value)
    command.value = ''
  }

  const previousCommand = () =>
    traverseHistory(-1)
  
  const nextCommand = () =>
    traverseHistory(1)

  const traverseHistory = (direction) => {
    const currentCommand = command.value
    const commands = history.value
    const lastCommandIdx = commands.length - 1

    if (lastCommandIdx === historyIdx.value && commands[lastCommandIdx] !== currentCommand)
      addCommandToHistory(currentCommand)
    
    historyIdx.value = Math.max(0, Math.min(commands.length - 1, historyIdx.value + direction))
    command.value = history.value[historyIdx.value]
  }

  const onOpen = ref()
  const onMessage = ref()
  const onError = ref()
  const onClose = ref()

  const connect = (token) => {
    status.value = 'connecting'
    failure.value = null

    try {
      connection.value = new Eventsource(consoleAddress, {
        headers: {
          Authorization: `xBasic ${btoa(`${token.name}:${token.secret}`)}`
        },
        // No automatic retry, on purpose. A stream that fails because the server is
        // struggling is the worst moment to start reconnecting in a loop. The view offers
        // a button instead, so the retry happens when somebody decides it should.
        disableRetry: true
      })

      connection.value.onopen = () => {
        // this is needed to stop an error from appearing in console when
        // switching/refreshing the page without closing the connection
        window.onbeforeunload = function () {
          close()
        }

        status.value = 'open'
        onOpen?.value()
      }

      connection.value.addEventListener("log", (event) => {
        if (!event.data.toString().includes("GET /api/status/instance from"))
          onMessage?.value(event.data)
      })

      connection.value.onerror = (error) => {
        status.value = 'error'
        failure.value = describe(error)
        onError?.value(error)
      }

      connection.value.onclose = () => {
        // An error already says more than a close does, so it keeps the field.
        if (status.value !== 'error') status.value = 'closed'
        onClose?.value()
      }

    } catch (error) {
      status.value = 'error'
      failure.value = describe(error)
      onError?.value(error)
    }
  }

  return {
    connection,
    connect,
    close,
    status,
    failure,
    onOpen,
    onMessage,
    onError,
    onClose,
    command,
    execute,
    previousCommand,
    nextCommand,
    isConnected
  }
}

/**
 * An EventSource error event carries no reason, so there is nothing to quote back. Say what
 * is actually known instead of inventing a cause: whether the browser is offline is the one
 * distinction that changes what the reader should do next.
 */
function describe(error) {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'This browser is offline.'
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'The server closed the stream or could not be reached.'
}
