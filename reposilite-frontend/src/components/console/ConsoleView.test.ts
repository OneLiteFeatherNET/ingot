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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Component, type Ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
// Loaded while the file is collected, not inside the first test: a fresh worker evaluates the
// view's whole module graph on first use, which under load can outlast the test timeout. Each
// test still loads its own copy in mountView(), after vi.resetModules().
import './ConsoleView.vue'

// The credentials the session hands the view, and the client it calls for the session check
// and for commands.
interface Credentials {
  name: string
  secret: string
}

interface TestClient {
  auth: { me: () => Promise<unknown> }
  console: { execute: (command: string) => unknown }
}

// The view reads the session for its token and client, and streams through EventSource. The
// stream is a fake the test drives event by event: nothing here opens a socket or waits.
const session = vi.hoisted(() => ({
  client: null as Ref<TestClient> | null,
  token: null as Ref<Credentials> | null,
  authMode: null as Ref<string | undefined> | null
}))
vi.mock('../../store/session', () => ({ useSession: () => session }))
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))
vi.mock('../../store/login', () => ({ openLogin: login.openLogin }))
vi.mock('../../helpers/toast', () => ({
  createErrorToast: vi.fn(),
  errorMessage: (error: Error) => error.message
}))

type Listener = (event: { data: string }) => void

// The part of the fake stream the tests use: the listeners the view registers, and the
// handlers it assigns to the stream as properties.
interface FakeStream {
  url: string
  options: unknown
  listeners: Map<string, Listener>
  closed?: boolean
  onopen: () => void
  onerror: (error: unknown) => void
  addEventListener: (type: string, handler: Listener) => void
  close: () => void
}

const { FakeEventSource } = vi.hoisted(() => {
  class FakeEventSource implements FakeStream {
    static instances: FakeEventSource[] = []

    url: string
    options: unknown
    listeners: Map<string, Listener>
    closed?: boolean
    onopen!: () => void
    onerror!: (error: unknown) => void

    constructor(url: string, options: unknown) {
      this.url = url
      this.options = options
      this.listeners = new Map()
      FakeEventSource.instances.push(this)
    }

    addEventListener(type: string, handler: Listener) {
      this.listeners.set(type, handler)
    }

    close() {
      this.closed = true
    }
  }
  FakeEventSource.instances = []
  return { FakeEventSource }
})
vi.mock('extended-eventsource', () => ({ EventSource: FakeEventSource }))

// The console socket, driven by the test frame by frame. By default it plays a server without protocol 1: that server
// reads the hello as a first message without credentials, answers with its error and hangs up, as upstream does.
const { FakeWebSocket } = vi.hoisted(() => {
  class FakeWebSocket {
    static instances: FakeWebSocket[] = []
    static speaksVersion1 = false

    readyState = 0
    sent: string[] = []
    onopen: (() => void) | null = null
    onmessage: ((event: { data: unknown }) => void) | null = null
    onclose: ((event: { code: number }) => void) | null = null

    constructor(readonly url: string) {
      FakeWebSocket.instances.push(this)
      queueMicrotask(() => {
        this.readyState = 1
        this.onopen?.()
      })
    }

    send(data: string) {
      this.sent.push(data)
      if (!FakeWebSocket.speaksVersion1 && (JSON.parse(data) as { type?: string }).type === 'hello') {
        this.onmessage?.({ data: '{"status":401,"message":"Unauthorized CLI access request (missing credentials)"}' })
        this.drop(1000)
      }
    }

    close() {
      this.readyState = 3
    }

    receive(frame: object) {
      this.onmessage?.({ data: JSON.stringify(frame) })
    }

    drop(code: number) {
      this.readyState = 3
      this.onclose?.({ code })
    }

    frames(): Record<string, unknown>[] {
      return this.sent.map(frame => JSON.parse(frame) as Record<string, unknown>)
    }
  }
  return { FakeWebSocket }
})

const emitLine = (stream: FakeStream, text: string) => {
  const handler = stream.listeners.get('log')
  if (!handler) throw new Error('the view did not listen for log lines')
  handler({ data: text })
}

// Loaded after the reset of beforeEach, so the panel is the same copy the view draws.
const loadStatePanel = async (): Promise<Component> => (await import('../md/MdStatePanel.vue') as { default: Component }).default

const buttonLabelled = (wrapper: VueWrapper, label: string) =>
  wrapper.findAll('button').find(button => button.text() === label)

describe('ConsoleView', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    // The log is module state that outlives a mount, so each test loads a fresh copy of the
    // view and its stores. Otherwise the lines of one test would show up in the next.
    vi.resetModules()
    FakeEventSource.instances.length = 0
    FakeWebSocket.instances.length = 0
    FakeWebSocket.speaksVersion1 = false
    vi.stubGlobal('WebSocket', FakeWebSocket)
    session.authMode = null
    session.token = ref({ name: 'ops', secret: 'secret' })
    session.client = ref({ auth: { me: vi.fn(() => Promise.resolve()) }, console: { execute: vi.fn() } })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  // Settles the hello before the test goes on: a server without protocol 1 has refused it by then, and the view has
  // opened the event stream in its place.
  const mountView = async () => {
    const { default: ConsoleView } = await import('./ConsoleView.vue')
    wrapper = mount(ConsoleView, { attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  const levelCount = (view: VueWrapper, level: string) =>
    view.findAll('button').find(button => button.attributes('aria-checked') !== undefined && button.text().startsWith(level))!
      .text().replace(level, '').trim()

  const sendCommand = async (view: VueWrapper, line: string) => {
    const input = view.find('#consoleInput')
    await input.setValue(line)
    await input.trigger('keyup.enter')
    await flushPromises()
  }

  describe('console socket', () => {
    const welcomed = async () => {
      FakeWebSocket.speaksVersion1 = true
      await mountView()
      const socket = FakeWebSocket.instances[0]
      socket.receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null })
      await flushPromises()
      return socket
    }

    it('runs the console over the socket and opens no event stream when the server offers protocol 1', async () => {
      await welcomed()

      expect(FakeEventSource.instances, 'a server that speaks protocol 1 needs no event stream').toHaveLength(0)
      expect(wrapper.find('[role="status"]').text(), 'the welcome connects the console').toContain('Connected')
    })

    it('sends the token of the session in the hello', async () => {
      const socket = await welcomed()

      expect(socket.frames()[0], 'the hello carries protocol 1 and the token').toMatchObject({ type: 'hello', protocols: [1], token: 'ops:secret' })
    })

    it('counts a log frame under the level the frame names and filters it in the client', async () => {
      const socket = await welcomed()
      socket.receive({ type: 'log', seq: 1, node: 'node-a', level: 'WARN', text: 'disk almost full', dropped: 0 })
      await flushPromises()

      expect(levelCount(wrapper, 'Warn'), 'the level comes from the frame, not from the text').toBe('1')
      await wrapper.findAll('button').find(button => button.text().startsWith('Warn'))!.trigger('click')
      expect(wrapper.find('#console').text(), 'a switched off level hides its lines without asking the server').not.toContain('disk almost full')
    })

    it('sends an execute frame on Enter and no HTTP request', async () => {
      const socket = await welcomed()

      await sendCommand(wrapper, 'status')

      expect(socket.frames().at(-1), 'the command goes over the socket').toMatchObject({ type: 'execute', command: 'status' })
      expect(session.client!.value.console.execute, 'no HTTP request runs a command on the socket').not.toHaveBeenCalled()
    })

    it('shows the stderr lines of a failed result as errors together with its exit code', async () => {
      const socket = await welcomed()
      await sendCommand(wrapper, 'status')
      const id = socket.frames().at(-1)!.id
      socket.receive({
        type: 'result', id, status: 'FAILED', exitCode: 1, data: null, node: 'node-a', confirmed: false,
        output: [{ stream: 'stdout', text: 'checking' }, { stream: 'stderr', text: 'boom' }]
      })
      await flushPromises()

      const output = wrapper.find('#console').text()
      expect(output, 'the output of the command is shown').toContain('checking')
      expect(output, 'the failure names its exit code').toContain('Exit code 1')
      expect(levelCount(wrapper, 'Error'), 'the stderr line and the exit code count as errors').toBe('2')
    })

    it('reports a command that had no result when the socket dropped as unknown', async () => {
      const socket = await welcomed()
      await sendCommand(wrapper, 'status')

      socket.drop(1006)
      await flushPromises()

      expect(wrapper.find('#console').text(), 'the reader must learn the command may have run').toContain('may or may not have run')
    })
  })

  describe('server without protocol 1', () => {
    it('falls back to the event stream and sends commands over HTTP', async () => {
      session.client!.value.console.execute = vi.fn(() => Promise.resolve())
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      await sendCommand(wrapper, 'status')

      expect(FakeEventSource.instances, 'the log arrives over the event stream as before').toHaveLength(1)
      expect(session.client!.value.console.execute, 'the command is sent over HTTP as before').toHaveBeenCalledWith('status')
    })

    it('keeps a cookie session on the event stream', async () => {
      session.authMode = ref('cookie')
      await mountView()

      expect(FakeWebSocket.instances, 'a cookie session has no token to send in a hello').toHaveLength(0)
      expect(FakeEventSource.instances, 'the log arrives over the event stream as before').toHaveLength(1)
    })
  })

  it('keeps the lines printed before a reconnect and marks where the connection came back', async () => {
    await mountView()
    const first = FakeEventSource.instances[0]
    first.onopen()
    emitLine(first, 'hello')
    await flushPromises()
    first.onerror(new Event('error'))
    await flushPromises()

    buttonLabelled(wrapper, 'Reconnect')!.trigger('click')
    await flushPromises()
    FakeEventSource.instances[1].onopen()
    await flushPromises()

    const output = wrapper.find('#console').text()
    expect(output, 'lines printed before the drop must stay visible after reconnecting').toContain('hello')
    expect(output, 'the gap in the stream should be marked in the log').toContain('Reconnected')
  })

  describe('log text', () => {
    it('renders markup in a log line as literal text instead of elements', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, '<img src=x onerror=alert(1)> &amp;')
      await flushPromises()

      const output = wrapper.find('#console')
      expect(output.text(), 'the payload must show up verbatim').toContain('<img src=x onerror=alert(1)> &amp;')
      expect(output.find('img').exists(), 'a log line must never create elements').toBe(false)
    })

    it('colours a segment with a class instead of an inline style', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, '\u001b[31mERROR\u001b[0m done')
      await flushPromises()

      const coloured = wrapper.find('#console span')
      expect(coloured.text(), 'only the coloured part sits in the span').toBe('ERROR')
      expect(coloured.classes().join(' '), 'the colour comes from the fixed class table').toContain('text-red-700')
      expect(coloured.attributes('style'), 'no inline style may come from the log').toBeUndefined()
    })
  })

  describe('empty output', () => {
    it('says the first line is awaited while nothing has been printed', async () => {
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      expect(wrapper.text(), 'an open console with no output yet should say it is waiting').toContain('Waiting for the first log line')
    })

    it('says no line matches the filter instead of waiting for a first line', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, 'hello')
      await flushPromises()

      await wrapper.find('input').setValue('zzz')
      await flushPromises()

      expect(wrapper.text(), 'a filter that hides everything should say so').toContain('No lines match the filter')
      expect(wrapper.text(), 'lines have arrived, so the view must not claim to be waiting for one').not.toContain('Waiting for the first log line')
    })

    it('shows the wait for the first line as an empty state panel', async () => {
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      const MdStatePanel = await loadStatePanel()
      const panel = wrapper.findAllComponents(MdStatePanel).find(candidate => candidate.text().includes('Waiting for the first log line'))
      expect(panel, 'the wait must be drawn by an MdStatePanel').toBeDefined()
      expect((panel!.props() as { state: string }).state, 'the wait is the empty state of the panel').toBe('empty')
    })

    it('shows the no-match message as an empty state panel', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, 'hello')
      await flushPromises()

      await wrapper.find('input').setValue('zzz')
      await flushPromises()

      const MdStatePanel = await loadStatePanel()
      const panel = wrapper.findAllComponents(MdStatePanel).find(candidate => candidate.text().includes('No lines match the filter'))
      expect(panel, 'the no-match message must be drawn by an MdStatePanel').toBeDefined()
      expect((panel!.props() as { state: string }).state, 'the no-match message is the empty state of the panel').toBe('empty')
    })
  })

  describe('following the output', () => {
    // jsdom has no layout, so the panel's geometry is set by hand and its scroll position is
    // a plain value the view can read and write.
    const measurePanel = (panel: HTMLElement, { top, height = 1000, view = 200 }: { top: number; height?: number; view?: number }) => {
      let position = top
      Object.defineProperty(panel, 'scrollTop', {
        configurable: true,
        get: () => position,
        set: (value) => { position = value }
      })
      Object.defineProperty(panel, 'scrollHeight', { configurable: true, value: height })
      Object.defineProperty(panel, 'clientHeight', { configurable: true, value: view })
      panel.dispatchEvent(new Event('scroll'))
    }

    it('keeps the reader in place when new output arrives while they have scrolled up', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      await flushPromises()
      const panel = wrapper.find<HTMLElement>('#console').element
      measurePanel(panel, { top: 100 })

      emitLine(stream, 'hello')
      await flushPromises()

      expect(panel.scrollTop, 'a reader reading earlier output must not be pulled to the end').toBe(100)
    })

    it('follows new output while the reader is at the end of the panel', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      await flushPromises()
      const panel = wrapper.find<HTMLElement>('#console').element
      measurePanel(panel, { top: 800 })

      emitLine(stream, 'hello')
      await flushPromises()

      expect(panel.scrollTop, 'a reader at the end should keep seeing the newest line').toBe(1000)
    })

    it('offers a jump to the latest output once the reader has scrolled away from the end', async () => {
      await mountView()
      const panel = wrapper.find<HTMLElement>('#console').element
      expect(buttonLabelled(wrapper, 'Jump to latest'), 'the jump should not be offered while at the end').toBeUndefined()

      measurePanel(panel, { top: 100 })
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Jump to latest'), 'scrolling away from the end should offer the jump').toBeDefined()
    })

    it('does not announce every log line to assistive technology', async () => {
      await mountView()

      expect(wrapper.find('#console').attributes('aria-live'), 'the whole log must not be a live region').toBeUndefined()
    })

    it('sets the log output in the md3 body small role instead of a fixed text size', async () => {
      await mountView()

      expect(wrapper.find('#console').classes(), 'the log uses the md-body-small type role').toContain('md-body-small')
      expect(wrapper.find('#console').classes(), 'the log has no Tailwind text size class').not.toContain('text-xs')
    })

    it('announces the connection state through a status region', async () => {
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      expect(wrapper.find('[role="status"]').text(), 'a change in the connection should be announced').toContain('Connected')
    })
  })

  describe('disconnected banner', () => {
    const drop = async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, 'hello')
      await flushPromises()
      stream.onerror(new Event('error'))
      await flushPromises()
    }

    it('draws the disconnected banner as a state panel', async () => {
      await drop()

      expect(wrapper.findComponent({ name: 'MdStatePanel' }).exists(), 'the disconnected banner must be the shared state panel').toBe(true)
      expect(wrapper.findComponent({ name: 'MdStatePanel' }).text(), 'the panel must name the disconnection').toContain('Not connected to the remote console')
    })

    it('draws the banner on the error container with the matching text colour', async () => {
      await drop()

      const banner = wrapper.findComponent({ name: 'MdStatePanel' }).element as HTMLElement
      expect(banner.className, 'the banner must sit on the error container').toContain('bg-error-container')
      expect(banner.className, 'the banner text must pair with the error container').toContain('text-on-error-container')
      expect(banner.className, 'the surface text colour must not win over the container').not.toContain('text-on-surface')
    })

    it('announces the disconnect through exactly one live region', async () => {
      await drop()

      const liveRegions = wrapper.findAll('[role="alert"], [role="status"], [aria-live]')
        .filter(region => region.text().includes('Not connected to the remote console'))
      expect(liveRegions, 'the disconnect must be announced once, not by the status line and the banner together').toHaveLength(1)
    })

    it('offers Reconnect in the banner after a plain stream failure', async () => {
      await drop()

      expect(buttonLabelled(wrapper, 'Reconnect'), 'a dropped stream must offer Reconnect').toBeDefined()
    })

    it('offers no default Retry button in the banner', async () => {
      await drop()

      expect(wrapper.findAll('button').map(button => button.text()), 'the banner must offer its own actions, not the default Retry').not.toContain('Retry')
    })

    it('keeps the log below the banner visible after a drop', async () => {
      await drop()

      expect(wrapper.find('#console').text(), 'lines printed before the drop must stay in the log').toContain('hello')
    })

    it('shows the connecting line instead of the banner while connecting', async () => {
      await mountView()

      expect(wrapper.text(), 'a connecting console must not claim it is disconnected').not.toContain('Not connected to the remote console')
      expect(wrapper.text(), 'a connecting console must say so').toContain('Connecting to the remote console...')
    })
  })

  describe('expired session', () => {
    const unauthorized = () => Promise.reject(Object.assign(new Error('Unauthorized'), { response: { status: 401 } }))

    // The stream fails and the session check answers 401, which is the case the console
    // reports as an expired session.
    const expireSession = async () => {
      session.client = ref({ auth: { me: vi.fn(unauthorized) }, console: { execute: vi.fn() } })
      await mountView()
      FakeEventSource.instances[0].onerror(new Event('error'))
      await flushPromises()
    }

    it('offers a sign in button when the session has expired', async () => {
      await expireSession()

      expect(buttonLabelled(wrapper, 'Sign in'), 'an expired session should offer a sign in button').toBeDefined()
    })

    it('does not tell the reader to look for sign in in the menu', async () => {
      await expireSession()

      expect(wrapper.text(), 'the instruction is now a button, not a pointer to the menu').not.toContain('from the menu')
    })

    it('opens the sign in dialog when the sign in button is pressed', async () => {
      await expireSession()

      buttonLabelled(wrapper, 'Sign in')!.trigger('click')

      expect(login.openLogin, 'pressing sign in should ask the header for the sign in dialog').toHaveBeenCalledTimes(1)
    })

    it('offers no sign in button for an ordinary stream failure', async () => {
      session.client = ref({ auth: { me: vi.fn(() => Promise.reject(new Error('Network Error'))) }, console: { execute: vi.fn() } })
      await mountView()
      FakeEventSource.instances[0].onerror(new Event('error'))
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Sign in'), 'a server that cannot be reached is not an expired session').toBeUndefined()
    })
  })

  describe('address', () => {
    const mountWith = async (props: { level?: string; search?: string } = {}) => {
      const { default: ConsoleView } = await import('./ConsoleView.vue')
      wrapper = mount(ConsoleView, { props, attachTo: document.body })
      return wrapper
    }

    // The level chips are the only checkable buttons on the view, and each names its level.
    const levelChips = (view: VueWrapper) =>
      view.findAll('button').filter(button => button.attributes('aria-checked') !== undefined)
    const shownLevelNames = (view: VueWrapper) =>
      levelChips(view)
        .filter(chip => chip.attributes('aria-checked') === 'true')
        .map(chip => chip.text().replace(/\d+/g, '').trim())

    it('shows only the levels the address names', async () => {
      await mountWith({ level: 'info,error' })

      expect(shownLevelNames(wrapper)).toEqual(['Info', 'Error'])
    })

    it('shows every level when the address names none of them', async () => {
      await mountWith({})

      expect(shownLevelNames(wrapper)).toEqual(['Other', 'Trace', 'Debug', 'Info', 'Warn', 'Error'])
    })

    it('shows no level at all when the address lists none', async () => {
      await mountWith({ level: '' })

      expect(shownLevelNames(wrapper)).toEqual([])
    })

    it('filters the output by the text the address holds', async () => {
      await mountWith({ search: 'boom' })

      expect(wrapper.find<HTMLInputElement>('input').element.value).toBe('boom')
    })

    it('reports a switched level so the address can hold it', async () => {
      await mountWith({})

      await levelChips(wrapper).find(chip => chip.text().startsWith('Info'))!.trigger('click')

      expect(wrapper.emitted('update:level')?.at(-1)).toEqual(['other,trace,debug,warn,error'])
    })

    it('reports the filter text as it is typed', async () => {
      await mountWith({})

      await wrapper.find('input').setValue('boom')

      expect(wrapper.emitted('update:search')?.at(-1)).toEqual(['boom'])
    })

    it('does not report the address back when it set the level itself', async () => {
      await mountWith({ level: 'info' })

      expect(wrapper.emitted('update:level')).toBeUndefined()
    })
  })
})
