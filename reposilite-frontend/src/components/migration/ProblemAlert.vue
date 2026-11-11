<script setup>
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

// A failed call, in words: the title and the detail of the problem the server answered. It never shows a secret,
// because the server does not echo one.
defineProps({
  problem: {
    type: Object,
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
    role="alert"
    class="flex flex-col gap-1 rounded-corner-small bg-error-container p-3 text-on-error-container"
  >
    <p class="md-title-small">
      {{ problem.title || 'The request failed' }}
    </p>
    <p
      v-if="problem.detail"
      class="md-body-medium"
    >
      {{ problem.detail }}
    </p>
  </div>
</template>
