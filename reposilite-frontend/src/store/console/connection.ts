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
import type { AxiosError } from 'axios'
import { createURL } from '../client'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import { EventSource as Eventsource } from 'extended-eventsource'
import { useSession } from "../session"
import { ConsoleSocket, type CompleteFrame, type ConfirmFrame, type ResultFrame, type SocketState } from './socket'

const { client, authMode } = useSession()

const connection = ref<Eventsource | undefined>()
const command = ref("")

/**
 * Where the stream stands, as something a template can render. `readyState` alone cannot
 * do that job: it is not reactive, and it collapses "never tried" and "gave up" into the
 * same value, which are the two cases a reader most needs told apart.
 *
 * One of 'idle', 'connecting', 'open', 'closed' or 'error'.
 */
export type ConsoleStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

const status = ref<ConsoleStatus>('idle')
const failure = ref<string | null>(null)
// Set when the session check answers 401 after the stream failed. The reader can fix that
// with the sign in button, which a generic "stream closed" message would never tell them.
const sessionExpired = ref(false)

/*
 * The command history belongs to the session, not to the view. The view is mounted each time
 * its tab is opened, and a history created per mount would start empty on every visit.
 */
const HISTORY_KEY = 'ingot.console.history'
const HISTORY_LIMIT = 500

// The history of this browser tab. sessionStorage is per tab and survives a reload, and the server never sees it.
const loadHistory = (): string[] => {
  try {
    const stored: unknown = JSON.parse(sessionStorage.getItem(HISTORY_KEY) ?? '[]')
    if (Array.isArray(stored)) return [...stored.filter((line): line is string => typeof line === 'string'), '']
  } catch {
    // Storage blocked or unreadable: the history then lives in memory only.
  }
  return ['']
}

const history = ref<string[]>(loadHistory())
const historyIdx = ref(history.value.length - 1)

// The names and aliases of the commands the listing marks as sensitive, or null while the listing is not known. A line
// that starts with one of them is kept out of the history. While the listing is unknown nothing is written to the tab
// storage, because nothing can be told apart yet.
let sensitiveNames: Set<string> | null = null

const isSensitive = (line: string): boolean =>
  sensitiveNames?.has(line.trim().split(/\s+/)[0]?.toLowerCase() ?? '') === true

const saveHistory = () => {
  if (sensitiveNames === null) return
  try {
    sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history.value.filter(line => line !== '').slice(-HISTORY_LIMIT)))
  } catch {
    // The history stays in memory.
  }
}

// The socket of protocol 1, from the hello on. Commands use it once the server has welcomed it. Against a server without
// protocol 1 it is dropped, and the console runs over the event stream and the HTTP execute as before.
let socket: ConsoleSocket | undefined
let welcomed = false

// The dangerous command that waits for the user, or null. The server holds it until the answer arrives, and every other
// command is answered busy meanwhile.
const confirmation = ref<ConfirmFrame | null>(null)

// The exit code of a command that did not run because it was not confirmed, the code of the HTTP 409 answer.
const NOT_CONFIRMED_EXIT_CODE = 3

export default function useConsole() {
  const consoleAddress = createURL("/api/console/log")

  const isConnected = () => {
    // using built-in EventSource for readystate constants
    // the ones from extended-eventsource return undefined for some reason
    return connection.value?.readyState === EventSource.OPEN
  }

  const close = () => {
    const current = socket
    socket = undefined
    confirmation.value = null
    current?.close()
    // Not gated on isConnected: a stream still opening has a readyState of CONNECTING and
    // would slip through, which is how leaving the tab mid-handshake used to leave the
    // request running.
    connection.value?.close()
    connection.value = undefined
    status.value = 'closed'
  }

  const addCommandToHistory = (command: string) => {
    if (history.value[history.value.length - 1] == '')
      history.value.pop()
    if (!isSensitive(command)) history.value.push(command)
    historyIdx.value = history.value.length - 1
    saveHistory()
  }

  // Asks the server which commands are sensitive. Lines entered before the answer are checked once it is here.
  const loadSensitiveNames = (token: { name: string; secret: string }) => {
    const options: RequestInit = authMode?.value === 'cookie'
      ? { credentials: 'include' }
      : { headers: { Authorization: `xBasic ${btoa(`${token.name}:${token.secret}`)}` } }
    fetch(createURL('/api/console/commands'), options)
      .then(response => response.ok ? response.json() : Promise.reject(new Error(String(response.status))))
      .then((listing: { commands?: { name: string; aliases?: string[]; sensitive?: boolean }[] }) => {
        sensitiveNames = new Set((listing.commands ?? [])
          .filter(entry => entry.sensitive === true)
          .flatMap(entry => [entry.name, ...(entry.aliases ?? [])])
          .map(name => name.toLowerCase()))
        history.value = history.value.filter(line => !isSensitive(line))
        if (history.value.length === 0 || history.value[history.value.length - 1] !== '') history.value.push('')
        historyIdx.value = history.value.length - 1
        saveHistory()
      })
      .catch(() => {
        // An older server has no listing, and its history stays in memory.
      })
  }

  const execute = () => {
    const sent = command.value
    addCommandToHistory(sent)
    command.value = ''

    if (socket && welcomed) {
      // A command is never queued while the socket reconnects, so it goes back into the input instead.
      if (socket.execute(sent) === null) {
        if (command.value === '') command.value = sent
        createErrorToast('Command not sent: the console is reconnecting.')
      }
      return Promise.resolve()
    }

    return client.value.console.execute(sent)
      .catch(error => {
        console.error(error)
        // The text goes back so it can be fixed and sent again, unless the reader has already
        // started typing the next command, which must not be overwritten.
        if (command.value === '') command.value = sent
        createErrorToast(`Command not sent: ${errorMessage(error)}`)
      })
  }

  // The line and cursor the last completion was asked for. An answer for a line the reader has since changed is dropped.
  let completing: { id: string; line: string } | null = null

  // Asks the server for the words that complete the line. Over the event stream there is nobody to ask, so nothing happens.
  const complete = () => {
    if (!socket || !welcomed) return
    const line = command.value
    const id = socket.complete(line, line.length)
    completing = id === null ? null : { id, line }
  }

  const commonPrefix = (words: string[]) =>
    words.reduce((prefix, word) => {
      let end = 0
      while (end < prefix.length && end < word.length && prefix[end] === word[end]) end++
      return prefix.slice(0, end)
    })

  const applyCompletion = (frame: CompleteFrame) => {
    if (completing?.id !== frame.id || command.value !== completing.line) return
    completing = null
    if (frame.candidates.length === 0) return
    const { start, end } = frame.replace
    const word = frame.candidates.length === 1 ? frame.candidates[0] : commonPrefix(frame.candidates)
    command.value = command.value.slice(0, start) + word + command.value.slice(end)
    if (frame.candidates.length > 1) onMessage.value?.(frame.candidates.join('  '), 'INFO')
  }

  const previousCommand = () =>
    traverseHistory(-1)
  
  const nextCommand = () =>
    traverseHistory(1)

  const traverseHistory = (direction: number) => {
    const currentCommand = command.value
    const commands = history.value
    const lastCommandIdx = commands.length - 1

    if (lastCommandIdx === historyIdx.value && commands[lastCommandIdx] !== currentCommand)
      addCommandToHistory(currentCommand)
    
    historyIdx.value = Math.max(0, Math.min(commands.length - 1, historyIdx.value + direction))
    command.value = history.value[historyIdx.value]
  }

  const onOpen = ref<(() => void) | undefined>()
  // The level is given for the lines of the socket, which name it in the frame. A line of the stream carries it in its text.
  const onMessage = ref<((data: string, level?: string) => void) | undefined>()
  const onError = ref<((error: unknown) => void) | undefined>()
  const onClose = ref<(() => void) | undefined>()

  // An EventSource error carries no status code, so a 401 cannot be read from the stream. The
  // session is asked directly instead. Its answer only counts while the stream still reports
  // the failure: a reconnect that started in the meantime has nothing to explain.
  const checkSession = () =>
    client.value.auth.me()
      .catch((error: AxiosError | undefined) => {
        if (error?.response?.status !== 401 || status.value !== 'error') return
        sessionExpired.value = true
        failure.value = 'Your session has expired. Sign in again, then reconnect.'
      })

  // A cookie session has no token to send: the browser attaches the HttpOnly session cookie to
  // the stream (withCredentials). A token session, or one not known yet, sends the token as before.
  const streamOptions = (token: { name: string; secret: string }) =>
    authMode?.value === 'cookie'
      ? { withCredentials: true }
      : { headers: { Authorization: `xBasic ${btoa(`${token.name}:${token.secret}`)}` } }

  const connect = (token: { name: string; secret: string }) => {
    status.value = 'connecting'
    failure.value = null
    sessionExpired.value = false

    try {
      connection.value = new Eventsource(consoleAddress, {
        ...streamOptions(token),
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
        onOpen.value?.()
      }

      connection.value.addEventListener("log", (event: MessageEvent<string>) => {
        if (!event.data.toString().includes("GET /api/status/instance from"))
          onMessage.value?.(event.data)
      })

      connection.value.onerror = (error) => {
        status.value = 'error'
        failure.value = describe(error)
        onError.value?.(error)
        void checkSession()
      }

      ;(connection.value as Eventsource & { onclose?: () => void }).onclose = () => {
        // An error already says more than a close does, so it keeps the field.
        if (status.value !== 'error') status.value = 'closed'
        onClose.value?.()
      }

    } catch (error) {
      status.value = 'error'
      failure.value = describe(error)
      onError.value?.(error)
    }
  }

  // Sends the answer of the waiting command and closes the question. Without a ready socket nothing is sent, and the
  // server drops the command with the connection, so it does not run.
  const answerConfirmation = (accept: boolean) => {
    const asked = confirmation.value
    if (asked === null) return
    confirmation.value = null
    if (socket?.confirm(asked.id, accept) !== true) {
      onMessage.value?.(`The connection dropped before "${asked.command}" was answered. It did not run.`, 'WARN')
    }
  }

  const printResult = (result: ResultFrame) => {
    const output = (result.output as { stream: string; text: string }[] | undefined) ?? []
    if (result.exitCode === NOT_CONFIRMED_EXIT_CODE && result.confirmed === false && output.length === 0) {
      onMessage.value?.('The command did not run, because it was not confirmed.', 'WARN')
    }
    output.forEach(line => onMessage.value?.(line.text, line.stream === 'stderr' ? 'ERROR' : 'INFO'))
    const exitCode = result.exitCode as number | undefined
    if (exitCode !== undefined && exitCode !== 0) onMessage.value?.(`Exit code ${exitCode}`, 'ERROR')
  }

  /**
   * Opens the console over the socket of protocol 1 when the server offers it, and over the event stream otherwise. A
   * server without protocol 1 answers the hello as a first message without credentials and hangs up, and anything else
   * that ends the socket before its welcome falls back the same way. A cookie session has no token for the hello, so it
   * stays on the event stream.
   */
  const open = (token: { name: string; secret: string }) => {
    loadSensitiveNames(token)
    if (authMode?.value === 'cookie') return connect(token)
    status.value = 'connecting'
    failure.value = null
    sessionExpired.value = false

    welcomed = false
    const candidate: ConsoleSocket = new ConsoleSocket({
      url: createURL('/api/console/sock').replace(/^http/, 'ws'),
      client: 'ingot-dashboard',
      token: `${token.name}:${token.secret}`,
      listener: {
        onState: (state: SocketState) => {
          if (socket !== candidate) return
          if (welcomed) {
            if (state === 'waiting' || state === 'connecting') status.value = 'connecting'
            if (state === 'refused') {
              socket = undefined
              status.value = 'error'
              failure.value = 'The server ended the console session.'
              void checkSession()
            }
          } else if (state === 'waiting' || state === 'refused') {
            socket = undefined
            // Closed once the client has finished with the drop, so the retry it schedules is cancelled too.
            queueMicrotask(() => candidate.close())
            connect(token)
          }
        },
        onWelcome: () => {
          if (socket !== candidate) return
          welcomed = true
          status.value = 'open'
          onOpen.value?.()
        },
        onLog: frame => {
          if (socket === candidate) onMessage.value?.(frame.text, frame.level)
        },
        onResult: frame => {
          if (socket === candidate) printResult(frame)
        },
        onError: frame => {
          if (socket !== candidate) return
          if (frame.code === 'busy') createErrorToast('Command not sent: another command is still running.')
          else if (frame.id !== undefined) onMessage.value?.(frame.message, 'ERROR')
        },
        onUnknown: (id, sent) => {
          if (socket !== candidate) return
          // The server drops a command that still waits for its confirmation together with the connection, so it did
          // not run, and the question has no one left to answer it.
          if (confirmation.value?.id === id) {
            confirmation.value = null
            onMessage.value?.(`The connection dropped while "${sent}" waited for its confirmation. It did not run.`, 'WARN')
            return
          }
          onMessage.value?.(`The connection dropped before "${sent}" answered. It may or may not have run.`, 'WARN')
        },
        onConfirm: frame => {
          if (socket === candidate) confirmation.value = frame
        },
        onComplete: frame => {
          if (socket === candidate) applyCompletion(frame)
        }
      }
    })
    socket = candidate
    candidate.connect()
  }

  return {
    connection,
    open,
    connect,
    close,
    status,
    failure,
    sessionExpired,
    onOpen,
    onMessage,
    onError,
    onClose,
    command,
    execute,
    complete,
    confirmation,
    answerConfirmation,
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
function describe(error: unknown): string {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'This browser is offline.'
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'The server closed the stream or could not be reached.'
}
