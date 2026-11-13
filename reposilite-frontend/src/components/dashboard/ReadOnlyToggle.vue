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

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdSwitch from '../md/MdSwitch.vue'
import { SCRIM_STYLE } from '../settings/dialog'

/*
 * Turns read-only mode on or off, on the Maintenance page. Only a principal with the maintenance area
 * may change it, and the server says so: the probe answers 200 for them and 403 for everyone else. The
 * section is therefore not shown until the probe has answered 200, and it stays hidden for any other
 * answer, so a principal without the area never sees a control that would fail.
 *
 * Turning the mode on refuses writes on every instance, so it asks first. Turning it off only restores
 * writes, so it needs no dialog.
 */

// The sentence the switch describes and the dialog opens with. Both use the same text, so a screen reader
// hears on focus what the confirmation then says.
const CONSEQUENCE = 'Every upload, deploy and delete is refused with 503 on every instance until read-only mode is turned off. Reads continue, and scheduled cleanup and index runs wait.'
const SWITCH_ID = 'read-only-switch'

const { client } = useSession()

const visible = ref(false)
const enabled = ref(false)
// The environment forces the state with INGOT_READ_ONLY. The server answers with that source, and the
// switch then shows the state and cannot change it, because a change would only be refused.
const forced = ref(false)
const busy = ref(false)
const confirmOpen = ref(false)
const failure = ref('')

const applyState = (data) => {
  enabled.value = data.enabled
  forced.value = data.source === 'environment'
}

onMounted(() => {
  client.value.readOnly.get()
    .then(response => {
      applyState(response.data)
      visible.value = true
    })
    .catch(() => {
      visible.value = false
    })
})

// The server answers with the state it now holds, and that is what the switch shows. A 409 means the
// environment forces the state; its problem body names the variable, and that text is shown as it is.
const request = (next) => {
  busy.value = true
  failure.value = ''
  const call = next ? client.value.readOnly.enable() : client.value.readOnly.disable()
  call
    .then(response => applyState(response.data))
    .catch(error => {
      failure.value = error?.response?.data?.message || 'Read-only mode could not be changed.'
    })
    .finally(() => {
      busy.value = false
    })
}

const focusSwitch = () => nextTick(() => document.getElementById(SWITCH_ID)?.focus())

const onSwitch = (next) => {
  if (busy.value || forced.value) return
  if (next) {
    confirmOpen.value = true
    return
  }
  request(false)
}

const cancel = () => {
  confirmOpen.value = false
  focusSwitch()
}

const confirm = () => {
  if (busy.value) return
  confirmOpen.value = false
  request(true)
  focusSwitch()
}
</script>

<template>
  <section
    v-if="visible"
    aria-labelledby="read-only-heading"
    class="flex flex-col gap-3"
    data-ingot-component="read-only-toggle"
  >
    <h3
      id="read-only-heading"
      class="md-title-medium text-on-surface"
    >
      Writes
    </h3>
    <div class="flex flex-col gap-1 px-1">
      <MdSwitch
        :id="SWITCH_ID"
        :model-value="enabled"
        :disabled="busy || forced"
        aria-describedby="read-only-consequence"
        @update:model-value="onSwitch"
      >
        Read-only mode
      </MdSwitch>
      <p
        id="read-only-consequence"
        class="md-body-small text-on-surface-variant"
      >
        {{ CONSEQUENCE }}
      </p>
      <p
        v-if="forced"
        class="md-body-small text-on-surface-variant"
      >
        Set by INGOT_READ_ONLY on this instance. Change the variable and restart the instance to change it here.
      </p>
      <p
        v-if="failure"
        data-ingot-state="error"
        role="alert"
        class="md-body-small text-error"
      >
        {{ failure }}
      </p>
    </div>

    <VueFinalModal
      :model-value="confirmOpen"
      teleport-to="body"
      :overlay-style="SCRIM_STYLE"
      :esc-to-close="true"
      class="flex min-h-full items-center justify-center p-4"
      @update:model-value="(open) => { if (!open) cancel() }"
    >
      <div
        class="w-[calc(100vw-2rem)] max-w-lg max-h-[90dvh] overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
        role="dialog"
        aria-modal="true"
        aria-labelledby="read-only-dialog-title"
        aria-describedby="read-only-dialog-body"
      >
        <h2
          id="read-only-dialog-title"
          class="md-headline-small text-on-surface"
        >
          Turn on read-only mode?
        </h2>
        <p
          id="read-only-dialog-body"
          class="mt-3 md-body-medium text-on-surface-variant"
        >
          {{ CONSEQUENCE }}
        </p>
        <div class="mt-6 flex flex-wrap justify-end gap-2">
          <MdButton
            id="read-only-cancel"
            variant="text"
            type="button"
            @click="cancel"
          >
            Cancel
          </MdButton>
          <MdButton
            id="read-only-confirm"
            variant="filled"
            type="button"
            :disabled="busy"
            @click="confirm"
          >
            Turn on read-only mode
          </MdButton>
        </div>
      </div>
    </VueFinalModal>
  </section>
</template>
