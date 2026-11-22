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

// The dashboard's client of the console socket, version 1: one hello, then JSON text frames. It knows nothing of Vue. The
// connection store and the view read what it reports through the listener.

export const PROTOCOL_VERSION = 1

// Closes that end the session for good. 1008 is a refusal (authentication, origin, or an expired session), and 1002 is
// a protocol mismatch. Neither gets a reconnect, because a retry would only be refused again.
const NO_RETRY_CODES = [1002, 1008]

const BACKOFF_START_MS = 1000
const BACKOFF_CAP_MS = 30_000

export type SocketState = 'idle' | 'connecting' | 'ready' | 'waiting' | 'refused' | 'closed'

// The part of a WebSocket the client uses. A browser WebSocket fits it, and a test can stand in for it.
export interface SocketLike {
  readyState: number
  send(data: string): void
  close(code?: number): void
  onopen: (() => void) | null
  onmessage: ((event: { data: unknown }) => void) | null
  onclose: ((event: { code: number }) => void) | null
}

export interface WelcomeFrame {
  protocol: number
  boot: string
  node: string
  seq: number
  replay: string | null
}

export interface LogFrame {
  seq: number
  node: string
  level: string
  text: string
  dropped: number
}

export interface ResultFrame {
  id: string
  [field: string]: unknown
}

// A dangerous command waits for the user. `command` is the command name only, so its arguments, a secret among them, are
// never sent back.
export interface ConfirmFrame {
  id: string
  command: string
  reason: string
}

// The words that may replace the characters from `replace.start` to `replace.end` of the line that was sent.
export interface CompleteFrame {
  id: string
  replace: { start: number; end: number }
  candidates: string[]
}

export interface ErrorFrame {
  code: string
  message: string
  id?: string
  retryable?: boolean
  supported?: number[]
}

export interface ConsoleSocketListener {
  onState(state: SocketState): void
  onWelcome(frame: WelcomeFrame): void
  // Only lines the client does not show yet. A line replayed after a resume is left out.
  onLog(frame: LogFrame): void
  onResult(frame: ResultFrame): void
  onError(frame: ErrorFrame): void
  // An execute that had no result when the socket dropped. It may or may not have run, so it is never sent again.
  onUnknown(id: string, command: string): void
  // A dangerous command that runs only after the page answers with confirm().
  onConfirm(frame: ConfirmFrame): void
  onComplete(frame: CompleteFrame): void
}

export interface ConsoleSocketOptions {
  url: string
  client: string
  token?: string
  logLevel?: string
  listener: ConsoleSocketListener
  createSocket?: (url: string) => SocketLike
  // Source of the jitter, from 0 to 1. A test fixes it to get exact delays.
  random?: () => number
  nextId?: () => string
}

const browserSocket = (url: string): SocketLike => new WebSocket(url) as unknown as SocketLike

export class ConsoleSocket {
  private socket: SocketLike | null = null
  private state: SocketState = 'idle'
  private boot: string | null = null
  private lastSeq: number | null = null
  // The (node, seq) pairs already shown for the current boot. A resume replays lines that are in here.
  private readonly shown = new Set<string>()
  private readonly pending = new Map<string, string>()
  private attempt = 0
  private timer: ReturnType<typeof setTimeout> | null = null
  private closedByClient = false
  private counter = 0

  constructor(private readonly options: ConsoleSocketOptions) {}

  connect(): void {
    this.closedByClient = false
    this.open()
  }

  close(): void {
    this.closedByClient = true
    this.clearTimer()
    const socket = this.socket
    this.socket = null
    socket?.close(1000)
    this.setState('closed')
  }

  // Returns the id of the request, or null when the socket is not ready. A command is never queued for later.
  execute(command: string): string | null {
    if (this.state !== 'ready' || this.socket === null) {
      return null
    }
    const id = this.nextId()
    this.pending.set(id, command)
    this.socket.send(JSON.stringify({ type: 'execute', id, command }))
    return id
  }

  // Answers the confirmation of the execute [id]. Returns false when the socket is not ready: an answer is never queued,
  // because the server drops a waiting command when its connection closes.
  confirm(id: string, accept: boolean): boolean {
    if (this.state !== 'ready' || this.socket === null) {
      return false
    }
    this.socket.send(JSON.stringify({ type: 'confirm', id, accept }))
    return true
  }

  // Asks for the words that complete [line] at [cursor]. Returns the id of the request, or null when the socket is not
  // ready. A completion is never queued, and it is not a command, so a drop does not report it as unknown.
  complete(line: string, cursor: number): string | null {
    if (this.state !== 'ready' || this.socket === null) {
      return null
    }
    const id = this.nextId()
    this.socket.send(JSON.stringify({ type: 'complete', id, line, cursor }))
    return id
  }

  private nextId(): string {
    return this.options.nextId ? this.options.nextId() : `r${++this.counter}`
  }

  private open(): void {
    this.setState('connecting')
    const socket = (this.options.createSocket ?? browserSocket)(this.options.url)
    this.socket = socket
    socket.onopen = () => {
      if (this.socket === socket) this.sendHello(socket)
    }
    socket.onmessage = event => {
      if (this.socket === socket) this.receive(event.data)
    }
    socket.onclose = event => {
      if (this.socket !== socket) return
      this.socket = null
      this.dropped(event.code)
    }
  }

  private sendHello(socket: SocketLike): void {
    const hello: Record<string, unknown> = {
      type: 'hello',
      protocols: [PROTOCOL_VERSION],
      client: this.options.client,
    }
    if (this.options.token) hello.token = this.options.token
    if (this.options.logLevel) hello.logLevel = this.options.logLevel
    if (this.boot !== null) hello.resume = { boot: this.boot, seq: this.lastSeq ?? 0 }
    socket.send(JSON.stringify(hello))
  }

  private receive(data: unknown): void {
    let frame: Record<string, unknown>
    try {
      frame = JSON.parse(String(data)) as Record<string, unknown>
    } catch {
      return
    }
    switch (frame.type) {
      case 'welcome':
        this.welcome(frame as unknown as WelcomeFrame)
        break
      case 'log':
        this.log(frame as unknown as LogFrame)
        break
      case 'result':
        this.pending.delete(String(frame.id))
        this.options.listener.onResult(frame as ResultFrame)
        break
      case 'complete':
        this.options.listener.onComplete(frame as unknown as CompleteFrame)
        break
      case 'confirm':
        this.options.listener.onConfirm(frame as unknown as ConfirmFrame)
        break
      case 'error': {
        const error = frame as unknown as ErrorFrame
        // A busy answer means the command did not run, so it is not an unknown command.
        if (error.id !== undefined && error.code === 'busy') this.pending.delete(error.id)
        this.options.listener.onError(error)
        break
      }
    }
  }

  private welcome(frame: WelcomeFrame): void {
    if (frame.boot !== this.boot) {
      // Another process, so its sequence numbers start again and nothing shown before applies to it.
      this.boot = frame.boot
      this.lastSeq = null
      this.shown.clear()
    }
    this.attempt = 0
    this.setState('ready')
    this.options.listener.onWelcome(frame)
  }

  private log(frame: LogFrame): void {
    const key = `${frame.node}:${frame.seq}`
    if (this.shown.has(key)) return
    this.shown.add(key)
    this.lastSeq = frame.seq
    this.options.listener.onLog(frame)
  }

  private dropped(code: number): void {
    for (const [id, command] of this.pending) {
      this.options.listener.onUnknown(id, command)
    }
    this.pending.clear()

    if (this.closedByClient) return
    if (NO_RETRY_CODES.includes(code)) {
      this.setState('refused')
      return
    }
    this.setState('waiting')
    this.schedule()
  }

  private schedule(): void {
    const step = Math.min(BACKOFF_CAP_MS, BACKOFF_START_MS * 2 ** this.attempt)
    const jitter = 0.8 + 0.4 * (this.options.random ?? Math.random)()
    const delay = Math.min(BACKOFF_CAP_MS, step * jitter)
    this.attempt += 1
    this.timer = setTimeout(() => {
      this.timer = null
      this.open()
    }, delay)
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer)
      this.timer = null
    }
  }

  private setState(state: SocketState): void {
    this.state = state
    this.options.listener.onState(state)
  }
}
