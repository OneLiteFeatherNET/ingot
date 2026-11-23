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

// The console socket, driven by the test frame by frame. It plays a server that has not answered yet: the test sends the
// welcome, the log frames and the closes itself.
const { FakeWebSocket } = vi.hoisted(() => {
  class FakeWebSocket {
    static instances: FakeWebSocket[] = []

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

// What the tests do to the console socket, under the names of a stream: the welcome opens it, a line is a log frame and
// a refusal is the server closing with 1008.
interface FakeStream {
  socket: InstanceType<typeof FakeWebSocket>
  onopen: () => void
  onerror: (error: unknown) => void
}

const streamAt = (index: number): FakeStream => {
  const socket = FakeWebSocket.instances[index]
  return {
    socket,
    onopen: () => socket.receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null }),
    onerror: () => socket.drop(1008)
  }
}

let lineSeq = 0
const emitLine = (stream: FakeStream, text: string, level = 'INFO') => {
  stream.socket.receive({ type: 'log', seq: ++lineSeq, node: 'node-a', level, text, dropped: 0 })
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
    lineSeq = 0
    FakeWebSocket.instances.length = 0
    vi.stubGlobal('WebSocket', FakeWebSocket)
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('the command listing is not answered'))))
    sessionStorage.clear()
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
    const { default: ConsoleView } = await import('./ConsoleView.vue') as { default: Component }
    wrapper = mount(ConsoleView, { attachTo: document.body }) as VueWrapper
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
      await mountView()
      const socket = FakeWebSocket.instances[0]
      socket.receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null })
      await flushPromises()
      return socket
    }

    it('runs the console over the socket', async () => {
      await welcomed()

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

    describe('history', () => {
      const listing = (commands: { name: string; aliases?: string[]; sensitive: boolean }[]) =>
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ version: 1, commands: commands.map(command => ({ aliases: [], ...command })) })
        })))

      const step = async (key: 'up' | 'down') => {
        await wrapper.find('#consoleInput').trigger(`keyup.${key}`)
        return (wrapper.find('#consoleInput').element as HTMLInputElement).value
      }

      it('shows the last command again after the page is reloaded in the same tab', async () => {
        listing([{ name: 'status', sensitive: false }])
        await welcomed()
        await sendCommand(wrapper, 'status')
        wrapper.unmount()
        vi.resetModules()
        FakeWebSocket.instances.length = 0

        await welcomed()

        expect(await step('up'), 'Up shows the command entered before the reload').toBe('status')
      })

      it('does not show a sensitive command on Up or keep it in the tab storage', async () => {
        listing([{ name: 'token-generate', sensitive: true }, { name: 'status', sensitive: false }])
        await welcomed()
        await sendCommand(wrapper, 'status')
        await sendCommand(wrapper, 'token-generate ci s3cr3t')

        expect(await step('up'), 'the sensitive line is skipped and the one before it is shown').toBe('status')
        expect(JSON.stringify({ ...sessionStorage }), 'the tab storage never holds the secret').not.toContain('s3cr3t')
      })

      it('matches a sensitive command by its alias', async () => {
        listing([{ name: 'token-generate', aliases: ['tg'], sensitive: true }])
        await welcomed()
        await sendCommand(wrapper, 'tg ci s3cr3t')

        expect(JSON.stringify({ ...sessionStorage }), 'an alias is the same command').not.toContain('s3cr3t')
        expect(await step('up'), 'nothing was kept to step back to').toBe('')
      })

      it('keeps nothing in the tab storage while the listing of sensitive commands is unknown', async () => {
        await welcomed()
        await sendCommand(wrapper, 'status')

        expect(sessionStorage.length, 'without the listing nothing can be told apart, so nothing is written').toBe(0)
      })
    })

    describe('completion', () => {
      it('sends no frame while the reader types', async () => {
        const socket = await welcomed()
        const before = socket.sent.length

        await wrapper.find('#consoleInput').setValue('sta')

        expect(socket.sent, 'typing is a local matter').toHaveLength(before)
      })

      it('sends one complete frame on Tab and keeps the focus in the input', async () => {
        const socket = await welcomed()
        await wrapper.find('#consoleInput').setValue('sta')

        const event = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true, bubbles: true })
        wrapper.find('#consoleInput').element.dispatchEvent(event)
        await flushPromises()

        const completes = socket.frames().filter(frame => frame.type === 'complete')
        expect(completes, 'Tab asks the server once').toHaveLength(1)
        expect(completes[0], 'the frame holds the line').toMatchObject({ line: 'sta' })
        expect(event.defaultPrevented, 'Tab does not leave the input').toBe(true)
      })

      it('puts the only candidate into the input', async () => {
        const socket = await welcomed()
        await wrapper.find('#consoleInput').setValue('stat')
        wrapper.find('#consoleInput').element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true, bubbles: true }))
        const id = socket.frames().at(-1)!.id
        socket.receive({ type: 'complete', id, replace: { start: 0, end: 4 }, candidates: ['status'] })
        await flushPromises()

        expect((wrapper.find('#consoleInput').element as HTMLInputElement).value, 'the candidate replaces the word').toBe('status')
      })

      it('lists several candidates in the output and completes their common prefix', async () => {
        const socket = await welcomed()
        await wrapper.find('#consoleInput').setValue('to')
        wrapper.find('#consoleInput').element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true, bubbles: true }))
        const id = socket.frames().at(-1)!.id
        socket.receive({ type: 'complete', id, replace: { start: 0, end: 2 }, candidates: ['token-generate', 'token-revoke', 'tokens'] })
        await flushPromises()

        expect((wrapper.find('#consoleInput').element as HTMLInputElement).value, 'the common prefix is filled in').toBe('token')
        expect(wrapper.find('#console').text(), 'the candidates are shown').toContain('token-generate  token-revoke  tokens')
      })
    })

    describe('confirmation of a dangerous command', () => {
      const askedToConfirm = async () => {
        const socket = await welcomed()
        await sendCommand(wrapper, 'token-revoke ci')
        const id = socket.frames().at(-1)!.id
        socket.receive({ type: 'confirm', id, command: 'token-revoke', reason: 'Revoke token' })
        await flushPromises()
        return { socket, id }
      }

      const dialog = () => document.querySelector('[role="dialog"]')

      it('opens a dialog that names the command and its reason', async () => {
        await askedToConfirm()

        const text = document.body.textContent ?? ''
        expect(dialog(), 'a confirm frame opens the dialog').not.toBeNull()
        expect(text, 'the dialog names the command').toContain('token-revoke')
        expect(text, 'the dialog gives the reason the server sent').toContain('Revoke token')
      })

      it('puts the focus on Cancel, so Enter does not run the command', async () => {
        await askedToConfirm()

        expect(document.activeElement?.textContent?.trim(), 'the safe answer is the default').toBe('Cancel')
      })

      it('declines the command when Cancel is pressed', async () => {
        const { socket, id } = await askedToConfirm()

        const cancel = [...document.querySelectorAll('button')].find(button => button.textContent?.trim() === 'Cancel')!
        cancel.click()
        await flushPromises()

        expect(socket.frames().at(-1), 'the server learns that the command must not run').toEqual({ type: 'confirm', id, accept: false })
      })

      it('accepts the command when Run is pressed', async () => {
        const { socket, id } = await askedToConfirm()

        const run = document.querySelector<HTMLButtonElement>('[data-test="confirm-button"]')!
        run.click()
        await flushPromises()

        expect(socket.frames().at(-1), 'the server runs the command once it is accepted').toEqual({ type: 'confirm', id, accept: true })
        expect(socket.frames().filter(frame => frame.type === 'confirm'), 'one answer per confirmation').toHaveLength(1)
      })

      it('tells the reader that a declined command did not run', async () => {
        const { socket, id } = await askedToConfirm()
        const cancel = [...document.querySelectorAll('button')].find(button => button.textContent?.trim() === 'Cancel')!
        cancel.click()
        await flushPromises()

        socket.receive({ type: 'result', id, status: 'FAILED', exitCode: 3, output: [], data: null, node: 'node-a', confirmed: false })
        await flushPromises()

        expect(wrapper.find('#console').text(), 'the declined command is reported as not run').toContain('did not run')
      })
    })

    it('reports a command that had no result when the socket dropped as unknown', async () => {
      const socket = await welcomed()
      await sendCommand(wrapper, 'status')

      socket.drop(1006)
      await flushPromises()

      expect(wrapper.find('#console').text(), 'the reader must learn the command may have run').toContain('may or may not have run')
    })
  })

  describe('session cookie', () => {
    it('sends a hello without a token in cookie mode, so the browser cookie carries the login', async () => {
      session.authMode = ref('cookie')
      session.token = ref({ name: '', secret: '' })
      await mountView()

      expect(FakeWebSocket.instances, 'a cookie session opens the one console socket').toHaveLength(1)
      const hello = FakeWebSocket.instances[0].frames()[0]
      expect(hello, 'the hello names protocol 1').toMatchObject({ type: 'hello', protocols: [1] })
      expect(hello, 'a cookie session has no secret to put in the hello').not.toHaveProperty('token')
    })

    it('ignores a token that is still around in cookie mode', async () => {
      session.authMode = ref('cookie')
      await mountView()

      expect(FakeWebSocket.instances[0].frames()[0], 'the cookie is the credential, not the token').not.toHaveProperty('token')
    })

    it('sends a command over the socket in cookie mode and makes no HTTP request for it', async () => {
      session.authMode = ref('cookie')
      await mountView()
      streamAt(0).onopen()
      await flushPromises()

      await sendCommand(wrapper, 'status')

      expect(FakeWebSocket.instances[0].frames().at(-1), 'the command goes over the socket').toMatchObject({ type: 'execute', command: 'status' })
      expect(session.client!.value.console.execute, 'no HTTP request runs a command').not.toHaveBeenCalled()
    })
  })

  describe('server that closes the hello', () => {
    it('keeps trying over the socket and never falls back to an HTTP request', async () => {
      await mountView()
      FakeWebSocket.instances[0].drop(1000)
      await flushPromises()

      expect(wrapper.text(), 'the console says it is connecting').toContain('Connecting to the remote console')
      expect(fetch, 'no listing of commands is needed to connect').not.toHaveBeenCalledWith(expect.stringContaining('/api/console/log'), expect.anything())
      expect(session.client!.value.console.execute, 'a command never goes over HTTP').not.toHaveBeenCalled()
    })
  })

  describe('structured data of a result', () => {
    const resultWith = async (data: unknown) => {
      await mountView()
      const stream = streamAt(0)
      stream.onopen()
      await flushPromises()
      await sendCommand(wrapper, 'tokens')
      const id = stream.socket.frames().at(-1)!.id
      stream.socket.receive({
        type: 'result', id, status: 'SUCCEEDED', exitCode: 0, data, node: 'node-a', confirmed: false,
        output: [{ stream: 'stdout', text: 'two tokens' }]
      })
      await flushPromises()
    }

    it('shows a list of records as a table with their keys as the head', async () => {
      await resultWith([{ name: 'ci', path: '/releases' }, { name: 'ops', path: '/private' }])

      const table = wrapper.find('#console table')
      expect(table.exists(), 'data is shown as a table').toBe(true)
      expect(table.findAll('th').map(cell => cell.text()), 'the keys are the column heads').toEqual(['name', 'path'])
      expect(table.findAll('tbody tr'), 'every record is a row').toHaveLength(2)
      expect(table.text(), 'the cells hold the values').toContain('/private')
    })

    it('keeps the text lines of the command below the table', async () => {
      await resultWith([{ name: 'ci' }])

      const output = wrapper.find('#console')
      expect(output.text(), 'the text lines stay').toContain('two tokens')
      expect(output.html().indexOf('<table'), 'the table comes first').toBeLessThan(output.html().indexOf('two tokens'))
    })

    it('renders markup inside a cell as literal text', async () => {
      await resultWith([{ name: '<img src=x onerror=alert(1)>' }])

      expect(wrapper.find('#console table').text(), 'the payload is shown verbatim').toContain('<img src=x onerror=alert(1)>')
      expect(wrapper.find('#console table img').exists(), 'a cell never creates elements').toBe(false)
    })

    it('shows no table when the result has no data', async () => {
      await resultWith(null)

      expect(wrapper.find('#console table').exists(), 'a result without data has no table').toBe(false)
    })
  })

  describe('refusal by the server', () => {
    it('shows the sign-in state and does not reconnect in a loop when the socket closes with 1008', async () => {
      session.client = ref({ auth: { me: vi.fn(() => Promise.reject(Object.assign(new Error('Unauthorized'), { response: { status: 401 } }))) }, console: { execute: vi.fn() } })
      await mountView()
      streamAt(0).onerror(null)
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Sign in'), 'the page asks the reader to sign in again').toBeDefined()
      expect(FakeWebSocket.instances, 'no further attempt until the reader acts').toHaveLength(1)
    })
  })

  it('keeps the lines printed before a reconnect and marks where the connection came back', async () => {
    await mountView()
    const first = streamAt(0)
    first.onopen()
    emitLine(first, 'hello')
    await flushPromises()
    first.onerror(new Event('error'))
    await flushPromises()

    await buttonLabelled(wrapper, 'Reconnect')!.trigger('click')
    await flushPromises()
    streamAt(1).onopen()
    await flushPromises()

    const output = wrapper.find('#console').text()
    expect(output, 'lines printed before the drop must stay visible after reconnecting').toContain('hello')
    expect(output, 'the gap in the stream should be marked in the log').toContain('Reconnected')
  })

  describe('log text', () => {
    it('renders markup in a log line as literal text instead of elements', async () => {
      await mountView()
      const stream = streamAt(0)
      stream.onopen()
      emitLine(stream, '<img src=x onerror=alert(1)> &amp;')
      await flushPromises()

      const output = wrapper.find('#console')
      expect(output.text(), 'the payload must show up verbatim').toContain('<img src=x onerror=alert(1)> &amp;')
      expect(output.find('img').exists(), 'a log line must never create elements').toBe(false)
    })

    it('renders a log line with exactly its own characters and no template whitespace', async () => {
      await mountView()
      const stream = streamAt(0)
      stream.onopen()
      emitLine(stream, '  indented \u001b[31mred\u001b[0m tail')
      await flushPromises()

      const line = wrapper.find('#console p.whitespace-pre')
      expect(line.element.textContent, 'the line keeps its spacing and gains none from the template').toBe('  indented red tail')
    })

    it('colours a segment with a class instead of an inline style', async () => {
      await mountView()
      const stream = streamAt(0)
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
      streamAt(0).onopen()
      await flushPromises()

      expect(wrapper.text(), 'an open console with no output yet should say it is waiting').toContain('Waiting for the first log line')
    })

    it('says no line matches the filter instead of waiting for a first line', async () => {
      await mountView()
      const stream = streamAt(0)
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
      streamAt(0).onopen()
      await flushPromises()

      const MdStatePanel = await loadStatePanel()
      const panel = wrapper.findAllComponents(MdStatePanel).find(candidate => candidate.text().includes('Waiting for the first log line'))
      expect(panel, 'the wait must be drawn by an MdStatePanel').toBeDefined()
      expect((panel!.props() as { state: string }).state, 'the wait is the empty state of the panel').toBe('empty')
    })

    it('shows the no-match message as an empty state panel', async () => {
      await mountView()
      const stream = streamAt(0)
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
        set: (value: number) => { position = value }
      })
      Object.defineProperty(panel, 'scrollHeight', { configurable: true, value: height })
      Object.defineProperty(panel, 'clientHeight', { configurable: true, value: view })
      panel.dispatchEvent(new Event('scroll'))
    }

    it('keeps the reader in place when new output arrives while they have scrolled up', async () => {
      await mountView()
      const stream = streamAt(0)
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
      const stream = streamAt(0)
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

    it('sets the log output in the small code role instead of a fixed text size', async () => {
      await mountView()

      expect(wrapper.find('#console').classes(), 'the log uses the md-code-small role, body small in monospace').toContain('md-code-small')
      expect(wrapper.find('#console').classes(), 'the log has no Tailwind text size class').not.toContain('text-xs')
    })

    it('announces the connection state through a status region', async () => {
      await mountView()
      streamAt(0).onopen()
      await flushPromises()

      expect(wrapper.find('[role="status"]').text(), 'a change in the connection should be announced').toContain('Connected')
    })
  })

  describe('disconnected banner', () => {
    const drop = async () => {
      await mountView()
      const stream = streamAt(0)
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

      const banner = wrapper.findComponent({ name: 'MdStatePanel' }).find('div').element as HTMLElement
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
      streamAt(0).onerror(new Event('error'))
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

      await buttonLabelled(wrapper, 'Sign in')!.trigger('click')

      expect(login.openLogin, 'pressing sign in should ask the header for the sign in dialog').toHaveBeenCalledTimes(1)
    })

    it('offers no sign in button for an ordinary stream failure', async () => {
      session.client = ref({ auth: { me: vi.fn(() => Promise.reject(new Error('Network Error'))) }, console: { execute: vi.fn() } })
      await mountView()
      streamAt(0).onerror(new Event('error'))
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Sign in'), 'a server that cannot be reached is not an expired session').toBeUndefined()
    })
  })

  describe('address', () => {
    const mountWith = async (props: { level?: string; search?: string } = {}) => {
      const { default: ConsoleView } = await import('./ConsoleView.vue') as { default: Component }
      wrapper = mount(ConsoleView, { props, attachTo: document.body }) as VueWrapper
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
