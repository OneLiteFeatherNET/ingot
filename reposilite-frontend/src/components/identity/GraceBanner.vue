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

<script setup lang="ts">
import { computed } from 'vue'
import { useSession } from '../../store/session'
import MdAlert from '../md/MdAlert.vue'

/*
 * The notice of a grace period for a covered user without a second factor. It names the day the grace ends and
 * is not dismissible: it is the only signal before the session is restricted to the account page. Nothing is shown
 * when the server names no grace end, or when the end cannot be read.
 */
const { graceUntil } = useSession()

const endsOn = computed<string>(() => {
  if (!graceUntil.value) return ''
  const date = new Date(graceUntil.value)
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
})
</script>

<template>
  <div
    v-if="endsOn"
    class="px-4 pt-4"
    data-ingot-component="grace-banner"
  >
    <MdAlert
      tone="warning"
      title="Add a second factor"
    >
      Your administrator requires a second factor for your account. Add one on your account page before
      {{ endsOn }}. After that day, the session is limited to the account page.
    </MdAlert>
  </div>
</template>
