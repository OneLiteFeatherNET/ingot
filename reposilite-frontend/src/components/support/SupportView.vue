<!--
  - Copyright (c) 2026 OneLiteFeather
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<script setup>
import { onMounted, ref } from 'vue'
import download from 'downloadjs'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'

// The page offers the support bundle of the instance that answers. The download is a zip the operator reads before sharing it.
const { client } = useSession()
// loading, ready, off (the opt-out) or failed
const state = ref('loading')
const busy = ref(false)

const DEFAULT_FILE_NAME = 'ingot-support-bundle.zip'

const fileNameOf = (disposition) => disposition?.match(/filename="([^"]+)"/)?.[1] ?? DEFAULT_FILE_NAME

onMounted(async () => {
  try {
    const { data } = await client.value.support.availability()
    state.value = data?.enabled === true ? 'ready' : 'off'
  } catch (error) {
    state.value = error?.response?.status === 404 ? 'off' : 'failed'
  }
})

const downloadBundle = async () => {
  busy.value = true
  try {
    const response = await client.value.support.bundle()
    download(response.data, fileNameOf(response.headers?.['content-disposition']), 'application/zip')
  } catch (error) {
    createErrorToast(errorMessage(error))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div
    class="flex flex-col gap-6"
    data-ingot-component="support-view"
  >
    <p
      v-if="state === 'off'"
      class="md-body-medium text-on-surface-variant"
    >
      The support pages are turned off on this instance.
    </p>
    <p
      v-else-if="state === 'failed'"
      class="md-body-medium text-error"
    >
      The support bundle cannot be checked right now.
    </p>

    <template v-else-if="state === 'ready'">
      <p class="md-body-large text-on-surface-variant">
        A zip with what a maintainer needs to look at this instance: the system information, the local and shared
        configuration, the cluster members, the plugins and the recent log lines of this instance only.
      </p>

      <section
        aria-labelledby="support-removed"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-removed"
          class="md-title-large text-on-surface"
        >
          What is removed
        </h2>
        <p class="md-body-medium text-on-surface-variant">
          Secrets are removed or masked before the bundle is built: passwords, access keys, tokens, bus credentials and the
          database user and password. Values of settings that hold a secret are masked, and every log line is scrubbed of
          known secrets and of credentials in headers and URLs.
        </p>
        <p class="md-body-medium text-on-surface-variant">
          Free text in a log line can still hold something no rule recognises, such as a customer name in a path.
          Read the zip before you attach it to a public issue.
        </p>
      </section>

      <button
        type="button"
        class="md-label-large w-fit rounded-full bg-primary px-6 py-3 text-on-primary disabled:opacity-60"
        :disabled="busy"
        @click="downloadBundle"
      >
        Download support bundle
      </button>
    </template>
  </div>
</template>
