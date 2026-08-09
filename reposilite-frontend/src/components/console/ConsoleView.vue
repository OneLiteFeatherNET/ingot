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
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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

/*
 * The panel used to be 36rem tall on every screen, which overflows a laptop and leaves a
 * tall monitor half empty.
 *
 * It measures where it actually starts rather than subtracting a constant for the chrome
 * above it. A constant would have to encode the height of the header, the tab bar and the
 * filter row, and would be wrong the moment any of them changes: the header lost 188px
 * only one commit ago. The floor keeps a phone, where that chrome is most of the screen,
 * from ending up with a two line console.
 */
const BOTTOM_GUTTER_PX = 48
const MIN_HEIGHT_PX = 320

const panel = ref(null)
const panelHeight = ref(`${MIN_HEIGHT_PX}px`)

const measurePanel = () => {
  if (!panel.value) return
  const top = panel.value.getBoundingClientRect().top
  const available = window.innerHeight - top - BOTTOM_GUTTER_PX
  panelHeight.value = `${Math.max(MIN_HEIGHT_PX, Math.round(available))}px`
}

onMounted(() => {
  nextTick(measurePanel)
  window.addEventListener('resize', measurePanel)
})

onBeforeUnmount(() => window.removeEventListener('resize', measurePanel))

/*
 * Warn and Error are the two levels a reader looks for first, so they keep a colour while
 * they are on. The colour never carries the state on its own: a chip that is on has a solid
 * border, a filled marker and a heavier label, and one that is off has a dashed border, a
 * hollow marker and muted text, whatever its level.
 */
const ENABLED_LEVEL_COLOURS = {
  Warn: 'text-yellow-700 dark:text-yellow-500',
  Error: 'text-red-700 dark:text-red-400'
}

const chipClasses = (level) =>
  level.enabled
    ? `border-solid border-gray-400 bg-white font-semibold dark:border-gray-500 dark:bg-gray-900 ${ENABLED_LEVEL_COLOURS[level.name] ?? 'text-gray-800 dark:text-gray-100'}`
    : 'border-dashed border-gray-300 bg-transparent font-normal text-gray-500 dark:border-gray-700 dark:text-gray-400'
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
      <div class="flex w-full flex-row flex-wrap items-center gap-2 pt-2 xl:w-1/2 xl:justify-end xl:pt-0">
        <button
          v-for="level in levels"
          :key="level.name"
          type="button"
          :aria-pressed="level.enabled"
          class="inline-flex cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-1 font-sans text-xs focus-visible:outline-2 focus-visible:outline-offset-2"
          :class="chipClasses(level)"
          @click="level.enabled = !level.enabled"
        >
          <span
            aria-hidden="true"
            class="h-1.5 w-1.5 shrink-0 rounded-full"
            :class="level.enabled ? 'bg-current' : 'ring-1 ring-current'"
          />
          {{ level.name }}
          <span class="tabular-nums">{{ level.count }}</span>
        </button>
      </div>
    </div>

    <div
      ref="panel"
      class="flex flex-col rounded-lg bg-white dark:bg-gray-900"
      :style="{ height: panelHeight }"
    >
      <!-- scrollToEnd() drives this element by id, so it has to stay the one that scrolls. -->
      <div
        id="console"
        class="min-h-0 flex-1 overflow-auto whitespace-pre-wrap px-4 py-2 font-mono text-xs"
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
