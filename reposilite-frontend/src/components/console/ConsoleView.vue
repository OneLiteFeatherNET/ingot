<!--
  - Copyright (c) 2023 dzikoysk
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
import { computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useSession } from '../../store/session'
import useLog from '../../store/console/log'
import useConsole from '../../store/console/connection'

const { levels, log, logMessage, filter, clearLog } = useLog()

const {
  onOpen, onMessage, onClose, onError,
  connect,
  close,
  status,
  failure,
  command,
  execute,
  previousCommand,
  nextCommand
} = useConsole()

const scrollToEnd = () => {
  const console = document.getElementById('console')
  if (console) console.scrollTop = console.scrollHeight
}

const setupConnection = () => {
  onOpen.value = () => clearLog()
  onMessage.value = message => {
    logMessage(message)
    nextTick(() => scrollToEnd())
  }
  // The connection used to announce itself through three toasts, one of which landed on
  // the header while the panel below stayed an empty white rectangle. The state belongs
  // where the reader is already looking, so the panel renders it and the toasts are gone.
  onError.value = error => console.error(error)
  onClose.value = () => {}
  const { token } = useSession()
  connect(token.value)
}

// This view is mounted only while its tab is open, so the stream follows the same life.
// Previously nothing closed it and every visit left another one running.
onMounted(setupConnection)
onBeforeUnmount(close)

const connecting = computed(() => status.value === 'connecting')
const disconnected = computed(() => status.value === 'error' || status.value === 'closed')
const awaitingFirstLine = computed(() => status.value === 'open' && log.value.length === 0)
</script>

<template>
  <div class="container mx-auto px-15 pt-7 pb-12 text-xs max-sm:px-4">
    <div class="flex w-full flex-col justify-between py-2 text-sm xl:flex-row">
      <label class="w-full xl:w-1/2 xl:mr-5">
        <span class="sr-only">Filter log output</span>
        <input
          v-model="filter"
          placeholder="Filter"
          class="w-full rounded-lg bg-white px-4 py-1 dark:bg-gray-900"
        >
      </label>
      <div class="flex w-full flex-row justify-around max-md:flex-wrap xl:w-1/2">
        <label
          v-for="level in levels"
          :key="level.name"
          class="flex cursor-pointer items-center gap-2 whitespace-nowrap py-1 font-sans"
        >
          <input
            type="checkbox"
            :checked="level.enabled"
            @change="level.enabled = !level.enabled"
          >
          <span class="pr-4">{{ level.name }} ({{ level.count }})</span>
        </label>
      </div>
    </div>

    <div class="rounded-lg bg-white dark:bg-gray-900">
      <div
        id="console"
        class="h-144 overflow-scroll whitespace-pre-wrap px-4 py-2 font-mono text-xs"
        aria-live="polite"
      >
        <p
          v-for="entry in log"
          :key="entry.id"
          v-html="entry.message"
          class="whitespace-nowrap"
        />

        <!--
          whitespace-normal on each of these: the panel around them keeps pre-wrap so log
          lines survive verbatim, and without resetting it the indentation of this template
          is rendered as part of the sentence.
        -->
        <p v-if="connecting" class="whitespace-normal font-sans text-gray-500 dark:text-gray-400">
          Connecting to the remote console...
        </p>

        <p v-else-if="awaitingFirstLine" class="whitespace-normal font-sans text-gray-500 dark:text-gray-400">
          Connected. Waiting for the first log line.
        </p>

        <div v-else-if="disconnected" class="max-w-prose whitespace-normal font-sans">
          <p class="font-semibold text-gray-800 dark:text-gray-100">
            Not connected to the remote console
          </p>
          <p class="pt-1 text-gray-500 dark:text-gray-400">
            {{ failure || 'The stream was closed.' }}
            Log output stops until the connection is restored. Anything already printed
            stays visible above.
          </p>
          <button
            class="mt-3 rounded-md bg-blue-700 px-3 py-2 font-medium text-white hover:bg-blue-800"
            @click="setupConnection()"
          >
            Reconnect
          </button>
        </div>
      </div>

      <hr class="dark:border-gray-800">

      <label>
        <span class="sr-only">Console command</span>
        <input
          id="consoleInput"
          v-model="command"
          :disabled="disconnected"
          :placeholder="disconnected ? 'Reconnect to run commands' : `Type command or '?' to get help`"
          class="w-full rounded-b-lg bg-white px-4 py-2 disabled:cursor-not-allowed disabled:text-gray-400 dark:bg-gray-900 dark:text-white"
          autocomplete="off"
          @keyup.enter="execute()"
          @keyup.up="previousCommand()"
          @keyup.down="nextCommand()"
        >
      </label>
    </div>
  </div>
</template>
