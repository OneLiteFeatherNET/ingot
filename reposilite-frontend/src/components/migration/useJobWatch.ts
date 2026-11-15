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

// Follows one job of the server with the poller until it is finished. The timers and the tab come from the runtime
// the component was given, so a test drives the schedule. The watch stops when the component goes away.
import { onBeforeUnmount, ref, type Ref } from 'vue'
import { createPoller } from '../../store/migration/poller'
import { isFinished } from '../../store/migration/gating'
import type { PollerTimers, PollerVisibility } from '../../store/migration/types'
import type { JobView } from './types'

// The part of the API the watch reads, and the runtime the poller takes: the timers and the tab visibility.
interface JobReader {
  getJob: (id: number) => Promise<unknown>
}

interface WatchRuntime {
  timers: PollerTimers
  visibility: PollerVisibility
}

interface JobWatchOptions {
  api: JobReader
  runtime: WatchRuntime
  onUpdate?: (job: JobView) => void
  onFinished?: (job: JobView) => void
  onUnauthorized?: (failure: unknown) => void
}

const useJobWatch = ({ api, runtime, onUpdate = () => {}, onFinished = () => {}, onUnauthorized = () => {} }: JobWatchOptions) => {
  const job: Ref<JobView | null> = ref(null)
  const error: Ref<unknown> = ref(null)
  const watching = ref(false)
  let poller: { start: () => void; stop: () => void } | null = null

  const stop = () => {
    if (poller) poller.stop()
    poller = null
    watching.value = false
  }

  const follow = (id: number) => {
    stop()
    error.value = null
    watching.value = true
    poller = createPoller({
      fetch: async () => (await api.getJob(id)) as JobView,
      timers: runtime.timers,
      visibility: runtime.visibility,
      isFinished,
      onUpdate: (value: JobView) => {
        job.value = value
        error.value = null
        onUpdate(value)
      },
      onFinished: (value: JobView) => {
        job.value = value
        poller = null
        watching.value = false
        onUpdate(value)
        onFinished(value)
      },
      onError: (failure: unknown) => {
        error.value = failure
      },
      onUnauthorized: (failure: unknown) => {
        poller = null
        watching.value = false
        onUnauthorized(failure)
      },
      onStopped: (failure: unknown) => {
        error.value = failure
        poller = null
        watching.value = false
      },
    })
    poller.start()
  }

  onBeforeUnmount(stop)

  return { job, error, watching, follow, stop, show: (value: JobView) => { job.value = value } }
}

export { useJobWatch }
