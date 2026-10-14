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
import { isNearBottom } from './scroll'
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdTextField from '../md/MdTextField.vue'

const { levels, log, hasLines, logMessage, filter, addSeparator } = useLog()

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

// Whether the reader is at the end of the output. New lines are only pulled into view while
// they are, so somebody reading earlier output is not dragged down by every line that arrives.
const atBottom = ref(true)

const updateFollowing = (event) => {
  atBottom.value = isNearBottom(event.currentTarget)
}

const jumpToLatest = () => {
  scrollToEnd()
  atBottom.value = true
}

const setupConnection = () => {
  // A reconnect keeps what was already printed. The gap is marked rather than hidden, so the
  // reader can tell output that came before the drop from output after it.
  onOpen.value = () => {
    if (hasLines.value) addSeparator('Reconnected')
  }
  onMessage.value = message => {
    // Decided before the line lands: the scroll event for the new line has not fired yet, so
    // the flag still says where the reader was.
    const following = atBottom.value
    logMessage(message)
    nextTick(() => {
      if (following) scrollToEnd()
    })
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
// Two different emptinesses. Nothing has been printed yet, so waiting is right. Lines have
// arrived but the filter or the level switches hide all of them, and waiting would be wrong:
// the output exists, it is just not on show.
const awaitingFirstLine = computed(() => status.value === 'open' && !hasLines.value)
const noLinesMatch = computed(() =>
  status.value === 'open' && hasLines.value && !log.value.some(entry => entry.kind !== 'separator'))

const announcement = computed(() => {
  switch (status.value) {
    case 'connecting': return 'Connecting to the remote console.'
    case 'open': return 'Connected to the remote console.'
    case 'error':
    case 'closed': return 'Not connected to the remote console.'
    default: return ''
  }
})

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

const SEND_ICON = 'M120-160v-640l760 320-760 320Zm80-120 474-200-474-200v140l240 60-240 60v140Z'
</script>

<template>
  <div class="container mx-auto px-15 pt-7 pb-12 max-sm:px-4">
    <div class="flex w-full flex-col gap-3 pb-3 xl:flex-row xl:items-start xl:justify-between">
      <div class="w-full xl:mr-5 xl:w-1/2">
        <MdTextField
          v-model="filter"
          label="Filter log output"
          variant="outlined"
        />
      </div>
      <div
        role="group"
        aria-label="Log levels"
        class="flex w-full flex-row flex-wrap items-center gap-2 xl:w-1/2 xl:justify-end"
      >
        <MdChip
          v-for="level in levels"
          :key="level.name"
          variant="filter"
          :selected="level.enabled"
          @click="level.enabled = !level.enabled"
        >
          {{ level.name }}
          <span class="tabular-nums">{{ level.count }}</span>
        </MdChip>
      </div>
    </div>

    <section
      ref="panel"
      aria-label="Remote console"
      class="flex flex-col overflow-hidden rounded-corner-medium bg-surface-container-highest"
      :style="{ height: panelHeight }"
    >
      <!--
        The one live region in the view, and it carries the connection state only. The log
        itself is not announced: a busy server would otherwise read out every line it prints.
      -->
      <p
        class="sr-only"
        role="status"
      >
        {{ announcement }}
      </p>

      <div
        v-if="connecting || disconnected"
        class="flex flex-wrap items-center gap-3 px-4 py-3 md-body-medium"
        :class="disconnected ? 'bg-error-container text-on-error-container' : 'bg-surface-container-high text-on-surface-variant'"
      >
        <template v-if="connecting">
          <span>Connecting to the remote console...</span>
        </template>
        <template v-else>
          <div class="min-w-0 flex-1">
            <p class="md-title-small">
              Not connected to the remote console
            </p>
            <p class="pt-0.5">
              {{ failure || 'The stream was closed.' }}
              Log output stops until the connection is restored. Anything already printed
              stays visible below.
            </p>
          </div>
          <MdButton
            variant="tonal"
            @click="setupConnection()"
          >
            Reconnect
          </MdButton>
        </template>
      </div>

      <div class="relative flex min-h-0 flex-1 flex-col">
        <!-- scrollToEnd() drives this element by id, so it has to stay the one that scrolls. -->
        <div
          id="console"
          role="region"
          aria-label="Console output"
          tabindex="0"
          class="min-h-0 flex-1 overflow-auto whitespace-pre-wrap px-4 py-2 font-mono text-xs text-on-surface"
          @scroll="updateFollowing"
        >
          <template
            v-for="entry in log"
            :key="entry.id"
          >
            <p
              v-if="entry.kind === 'separator'"
              class="whitespace-normal py-1 font-sans text-on-surface-variant"
            >
              {{ entry.message }}
            </p>
            <p
              v-else
              class="whitespace-nowrap"
              v-html="entry.message"
            />
          </template>

          <!--
          whitespace-normal on each of these: the panel around them keeps pre-wrap so log
          lines survive verbatim, and without resetting it the indentation of this template
          is rendered as part of the sentence.
        -->
          <p
            v-if="awaitingFirstLine"
            class="whitespace-normal font-sans md-body-medium text-on-surface-variant"
          >
            Connected. Waiting for the first log line.
          </p>

          <p
            v-else-if="noLinesMatch"
            class="whitespace-normal font-sans md-body-medium text-on-surface-variant"
          >
            No lines match the filter. Clear the filter or switch a level back on to see the output.
          </p>
        </div>

        <div
          v-if="!atBottom"
          class="pointer-events-none absolute inset-x-0 bottom-3 flex justify-center"
        >
          <MdButton
            variant="tonal"
            class="pointer-events-auto shadow-elevation-1"
            @click="jumpToLatest"
          >
            Jump to latest
          </MdButton>
        </div>
      </div>

      <div class="flex items-start gap-2 border-t border-outline-variant p-3">
        <MdTextField
          id="consoleInput"
          v-model="command"
          label="Console command"
          variant="outlined"
          class="min-w-0 flex-1"
          :disabled="disconnected"
          :supporting-text="disconnected ? 'Reconnect to run commands' : `Type a command or '?' for help`"
          autocomplete="off"
          @keyup.enter="execute()"
          @keyup.up="previousCommand()"
          @keyup.down="nextCommand()"
        />
        <MdIconButton
          variant="filled"
          aria-label="Send command"
          class="mt-1"
          :disabled="disconnected || command.trim() === ''"
          @click="execute()"
        >
          <svg
            viewBox="0 -960 960 960"
            class="size-6"
            aria-hidden="true"
            focusable="false"
          >
            <path
              fill="currentColor"
              :d="SEND_ICON"
            />
          </svg>
        </MdIconButton>
      </div>
    </section>
  </div>
</template>
