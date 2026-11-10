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
import { onMounted, ref } from 'vue'
import { useSession } from '../../store/session'
import MdSwitch from '../md/MdSwitch.vue'

/*
 * Turns read-only mode on or off. Only a principal with the maintenance area may change it, and
 * the server says so: the probe answers 200 for them and 403 for everyone else. The toggle is
 * therefore not shown until the probe has answered 200, and it stays hidden for any other answer,
 * so a principal without the area never sees a control that would fail.
 */
const emit = defineEmits(['changed'])
const { client } = useSession()

const visible = ref(false)
const enabled = ref(false)
const busy = ref(false)
const failure = ref('')

onMounted(() => {
  client.value.readOnly.get()
    .then(response => {
      enabled.value = response.data.enabled
      visible.value = true
    })
    .catch(() => {
      visible.value = false
    })
})

// The server answers with the state it now holds, and that is what the switch shows. A 409 means the
// environment forces the state; its problem body names the variable, and that text is shown as it is.
const setReadOnly = (next) => {
  if (busy.value) return
  busy.value = true
  failure.value = ''
  const request = next ? client.value.readOnly.enable() : client.value.readOnly.disable()
  request
    .then(response => {
      enabled.value = response.data.enabled
      emit('changed')
    })
    .catch(error => {
      failure.value = error?.response?.data?.message || 'Read-only mode could not be changed.'
    })
    .finally(() => {
      busy.value = false
    })
}
</script>

<template>
  <div
    v-if="visible"
    class="flex flex-col gap-1 px-1"
  >
    <MdSwitch
      :model-value="enabled"
      :disabled="busy"
      @update:model-value="setReadOnly"
    >
      Read-only mode
    </MdSwitch>
    <p
      v-if="failure"
      role="alert"
      class="md-body-small text-error"
    >
      {{ failure }}
    </p>
  </div>
</template>
