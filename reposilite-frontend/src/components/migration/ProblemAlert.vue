<script setup lang="ts">
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

import type { PropType } from 'vue'
import MdAlert from '../md/MdAlert.vue'
import type { Problem } from './types'

// A failed call, in words: the title and the detail of the problem the server answered. It never shows a secret,
// because the server does not echo one. It is the shared error alert of the dashboard (MdAlert).
defineProps({
  problem: {
    type: Object as PropType<Problem | null>,
    default: null
  },
  id: {
    type: String,
    default: undefined
  }
})
</script>

<template>
  <div
    v-if="problem"
    :id="id"
    data-ingot-component="problem-alert"
    data-ingot-state="error"
  >
    <MdAlert
      tone="error"
      :title="problem.title || 'The request failed'"
    >
      <p
        v-if="problem.detail"
        class="m-0"
      >
        {{ problem.detail }}
      </p>
    </MdAlert>
  </div>
</template>
