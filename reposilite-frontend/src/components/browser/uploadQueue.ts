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

import { reactive } from 'vue'

/*
 * Runs a batch of uploads and records how each one ends. While a batch is in flight the queue
 * is busy and refuses to start another, so a second click on "Upload" cannot send the same
 * files again. A task is `{ key, run }`, where `run` returns a promise for its upload.
 *
 * Statuses are 'uploading', 'done' or 'failed', keyed by the task's key. Byte-level progress
 * would need a progress hook in the API client; the queue reports per-file state without it.
 */
const createUploadQueue = () => {
  const state = reactive({
    busy: false,
    statuses: {}
  })

  const runTask = (task) => {
    state.statuses[task.key] = 'uploading'

    let upload
    try {
      upload = Promise.resolve(task.run())
    } catch (error) {
      upload = Promise.reject(error)
    }

    return upload.then(
      () => { state.statuses[task.key] = 'done' },
      () => { state.statuses[task.key] = 'failed' }
    )
  }

  const start = (tasks) => {
    if (state.busy) {
      return false
    }

    state.busy = true
    return Promise.allSettled(tasks.map(runTask))
      .finally(() => { state.busy = false })
  }

  return {
    state,
    start
  }
}

export {
  createUploadQueue
}
