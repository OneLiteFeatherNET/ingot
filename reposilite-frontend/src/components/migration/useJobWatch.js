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
import { onBeforeUnmount, ref } from 'vue'
import { createPoller } from '../../store/migration/poller'
import { isFinished } from '../../store/migration/gating'

const useJobWatch = ({ api, runtime, onFinished = () => {}, onUnauthorized = () => {} }) => {
  const job = ref(null)
  const error = ref(null)
  const watching = ref(false)
  let poller = null

  const stop = () => {
    if (poller) poller.stop()
    poller = null
    watching.value = false
  }

  const follow = (id) => {
    stop()
    error.value = null
    watching.value = true
    poller = createPoller({
      fetch: () => api.getJob(id),
      timers: runtime.timers,
      visibility: runtime.visibility,
      isFinished,
      onUpdate: (value) => {
        job.value = value
        error.value = null
      },
      onFinished: (value) => {
        job.value = value
        poller = null
        watching.value = false
        onFinished(value)
      },
      onError: (failure) => {
        error.value = failure
      },
      onUnauthorized: (failure) => {
        poller = null
        watching.value = false
        onUnauthorized(failure)
      },
      onStopped: (failure) => {
        error.value = failure
        poller = null
        watching.value = false
      },
    })
    poller.start()
  }

  onBeforeUnmount(stop)

  return { job, error, watching, follow, stop, show: (value) => { job.value = value } }
}

export { useJobWatch }
