<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import type { AxiosResponse } from 'axios'
import { VueFinalModal } from 'vue-final-modal'
import { createErrorToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import type { components } from '../../types/api'
import { copyToClipboard, selectText } from '../card/copySnippet'
import CloseIcon from '../icons/CloseIcon.vue'
import RefreshIcon from '../icons/RefreshIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import { absoluteTime, relativeTime } from './failures'

type FailureResponse = components['schemas']['FailureResponse']
type FailuresResponse = components['schemas']['FailuresResponse']

/*
 * The failures behind the Failures tile. A side sheet from 840px up and a full-screen dialog
 * below, which is where a stack trace has room to be read. Nothing is fetched until the sheet
 * opens, so a dashboard nobody inspects never asks for the failure list.
 */
const props = withDefaults(defineProps<{
  modelValue?: boolean
}>(), {
  modelValue: false
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  cleared: []
}>()

const { client } = useSession()
const titleId = useId()
const failures = ref<FailureResponse[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const now = ref(0)
const expandedIds = ref<number[]>([])
const copiedId = ref<number | null>(null)

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const hasFailures = computed(() => failures.value.length > 0)

const load = () => {
  loading.value = true
  return client.value.status.failures()
    .then((response: AxiosResponse<FailuresResponse>) => {
      failures.value = response.data.failures
      loadFailed.value = false
      now.value = Date.now()
    })
    .catch(error => {
      console.error(error)
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

// Each opening starts from the server's current list, with every entry folded again.
watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    expandedIds.value = []
    copiedId.value = null
    load()
  }
}, { immediate: true })

const isExpanded = (failure: FailureResponse) => expandedIds.value.includes(failure.id)

const toggle = (failure: FailureResponse) => {
  expandedIds.value = isExpanded(failure)
    ? expandedIds.value.filter(id => id !== failure.id)
    : [...expandedIds.value, failure.id]
}

const reportOf = (failure: FailureResponse) =>
  [
    `${failure.type}: ${failure.message ?? '(no message)'}`,
    `Source: ${failure.source}`,
    `Time: ${absoluteTime(failure.timestamp)}`,
    `Occurrences: ${failure.count}`,
    '',
    failure.stacktrace
  ].join('\n')

// Without a clipboard the text is selected instead, so the copy can still be finished by hand.
const copy = async (failure: FailureResponse, event: Event) => {
  const copied = await copyToClipboard(reportOf(failure), navigator.clipboard)
  if (copied) {
    copiedId.value = failure.id
    return
  }
  const stackTrace = (event.currentTarget as HTMLElement).closest('li')?.querySelector('pre')
  if (stackTrace && selectText(stackTrace)) {
    createErrorToast('The clipboard is not available here. The stack trace is selected for copying.')
  }
}

const clear = () =>
  client.value.status.clearFailures()
    .then(() => {
      failures.value = []
      expandedIds.value = []
      emit('cleared')
    })
    .catch(error => {
      console.error(error)
      createErrorToast('Cannot clear the recorded failures')
    })
</script>

<template>
  <VueFinalModal
    v-model="open"
    teleport-to="body"
    :overlay-class="'vfm__overlay bg-scrim/md-scrim!'"
    class="flex justify-end"
  >
    <section
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      class="flex h-dvh w-screen flex-col bg-surface-container-low text-on-surface shadow-elevation-3 expanded:w-[28rem] expanded:rounded-l-corner-extra-large"
      data-ingot-component="failures-sheet"
    >
      <header class="flex items-center gap-2 border-b border-outline-variant px-4 py-3">
        <div class="min-w-0 flex-1">
          <h2
            :id="titleId"
            class="md-title-large"
          >
            Recorded failures
          </h2>
          <p class="md-body-small text-on-surface-variant">
            Since the last restart, newest first
          </p>
        </div>
        <MdIconButton
          aria-label="Refresh failures"
          title="Refresh failures"
          :disabled="loading"
          @click="load()"
        >
          <RefreshIcon />
        </MdIconButton>
        <MdIconButton
          aria-label="Close failures"
          title="Close"
          @click="open = false"
        >
          <CloseIcon />
        </MdIconButton>
      </header>

      <div class="flex-1 overflow-y-auto p-4">
        <MdStatePanel
          v-if="loadFailed"
          state="error"
          title="Cannot load failures"
          description="The server may be down or unreachable."
          @retry="load()"
        />

        <MdStatePanel
          v-else-if="loading && !hasFailures"
          state="loading"
          title="Loading failures"
        />

        <MdStatePanel
          v-else-if="!hasFailures"
          state="empty"
          description="No failures recorded."
        />

        <ol
          v-else
          class="flex flex-col gap-3"
        >
          <li
            v-for="failure in failures"
            :key="failure.id"
            class="rounded-corner-medium bg-surface-container-high p-3"
          >
            <div class="flex flex-wrap items-baseline gap-x-2 md-body-small text-on-surface-variant">
              <time
                :datetime="failure.timestamp"
                :title="absoluteTime(failure.timestamp)"
              >
                {{ relativeTime(failure.timestamp, now) }}
              </time>
              <span v-if="failure.count > 1">Seen {{ failure.count }} times</span>
            </div>

            <p class="pt-1 md-title-small break-words">
              {{ failure.type }}
            </p>
            <p class="md-body-medium break-words">
              {{ failure.message || 'No message' }}
            </p>
            <p class="pt-1 font-mono md-body-small break-all text-on-surface-variant">
              {{ failure.source }}
            </p>

            <MdButton
              variant="text"
              class="mt-2 -ml-3"
              :aria-expanded="isExpanded(failure) ? 'true' : 'false'"
              :aria-controls="`${titleId}-trace-${failure.id}`"
              @click="toggle(failure)"
            >
              {{ isExpanded(failure) ? 'Hide stack trace' : 'Show stack trace' }}
            </MdButton>

            <div
              v-if="isExpanded(failure)"
              :id="`${titleId}-trace-${failure.id}`"
              class="mt-2"
            >
              <div class="flex justify-end">
                <MdButton
                  variant="text"
                  @click="copy(failure, $event)"
                >
                  {{ copiedId === failure.id ? 'Copied' : 'Copy' }}
                </MdButton>
              </div>
              <pre
                tabindex="0"
                class="max-h-72 overflow-auto rounded-corner-small bg-surface-container-highest p-3 font-mono text-xs leading-5 whitespace-pre text-on-surface"
              >{{ failure.stacktrace }}</pre>
            </div>
          </li>
        </ol>
      </div>

      <footer class="flex justify-end border-t border-outline-variant px-4 py-3">
        <MdButton
          variant="text"
          error
          :disabled="!hasFailures || loading"
          @click="clear()"
        >
          Clear all
        </MdButton>
      </footer>
    </section>
  </VueFinalModal>
</template>
