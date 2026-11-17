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

import { describe, it, expect, vi } from 'vitest'
import { createUploadQueue } from './uploadQueue'

// A task whose outcome the test decides, so the batch can be held in flight on purpose.
const controlledTask = (key) => {
  let resolve
  let reject
  const run = vi.fn(() => new Promise((res, rej) => {
    resolve = res
    reject = rej
  }))
  return { key, run, succeed: () => resolve(), fail: () => reject(new Error('rejected')) }
}

describe('upload queue', () => {
  it('runs every task of a batch once', async () => {
    const queue = createUploadQueue()
    const first = controlledTask('a.jar')
    const second = controlledTask('b.jar')

    const batch = queue.start([first, second])
    first.succeed()
    second.succeed()
    await batch

    expect(first.run, 'the first file is sent once').toHaveBeenCalledTimes(1)
    expect(second.run, 'the second file is sent once').toHaveBeenCalledTimes(1)
  })

  it('refuses a second start while the first batch is still in flight', () => {
    const queue = createUploadQueue()
    const task = controlledTask('a.jar')

    queue.start([task])
    const accepted = queue.start([task])

    expect(accepted, 'a double click must not start a second batch').toBe(false)
    expect(task.run, 'the file is sent once, not once per click').toHaveBeenCalledTimes(1)
  })

  it('accepts a new batch once the previous one has settled', async () => {
    const queue = createUploadQueue()
    const first = controlledTask('a.jar')
    const batch = queue.start([first])
    first.succeed()
    await batch

    const second = controlledTask('a.jar')
    const accepted = queue.start([second])

    expect(accepted, 'a settled batch no longer blocks uploads').not.toBe(false)
    expect(second.run, 'the next upload runs').toHaveBeenCalledTimes(1)
    second.succeed()
    await accepted
  })

  it('reports busy until every task has settled', async () => {
    const queue = createUploadQueue()
    const first = controlledTask('a.jar')
    const second = controlledTask('b.jar')

    const batch = queue.start([first, second])
    first.succeed()
    await Promise.resolve()

    expect(queue.state.busy, 'one file is still uploading').toBe(true)

    second.succeed()
    await batch

    expect(queue.state.busy, 'the batch is over once both files settled').toBe(false)
  })

  it('marks a failed task as failed and still lets the others finish', async () => {
    const queue = createUploadQueue()
    const failing = controlledTask('a.jar')
    const passing = controlledTask('b.jar')

    const batch = queue.start([failing, passing])
    failing.fail()
    passing.succeed()
    await batch

    expect(queue.state.statuses['a.jar'], 'the failed file is reported').toBe('failed')
    expect(queue.state.statuses['b.jar'], 'the other file still completes').toBe('done')
  })

  it('marks a task that throws synchronously as failed', async () => {
    const queue = createUploadQueue()
    const broken = { key: 'broken.jar', run: () => { throw new Error('no client') } }

    await queue.start([broken])

    expect(queue.state.statuses['broken.jar'], 'a synchronous throw is a failed upload').toBe('failed')
    expect(queue.state.busy, 'the queue is free again after a throw').toBe(false)
  })
})
