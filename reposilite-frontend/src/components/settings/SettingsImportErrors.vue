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
import { computed } from 'vue'
import MdButton from '../md/MdButton.vue'

/*
 * Why a settings file was not imported. Nothing of the file is in the draft, so every error has to
 * be fixed in the file itself; the list names the domain and the field of each one.
 */
const props = defineProps({
  errors: {
    type: Array,
    required: true
  }
})

const emit = defineEmits(['dismiss'])

const groups = computed(() => {
  const byDomain = new Map()
  for (const error of props.errors) {
    if (!byDomain.has(error.domain)) byDomain.set(error.domain, [])
    byDomain.get(error.domain).push(error)
  }
  return [...byDomain.entries()].map(([domain, errors]) => ({ domain, errors }))
})
</script>

<template>
  <section
    role="alert"
    class="mb-6 flex flex-col gap-4 rounded-corner-medium bg-error-container p-4 text-on-error-container"
  >
    <div class="flex flex-wrap items-start justify-between gap-2">
      <h2 class="md-title-medium">
        The file was not imported
      </h2>
      <MdButton
        variant="text"
        type="button"
        @click="emit('dismiss')"
      >
        Dismiss
      </MdButton>
    </div>
    <p class="md-body-medium">
      Nothing was loaded into the draft. Fix these values in the file and import it again.
    </p>
    <div
      v-for="group in groups"
      :key="group.domain"
      class="flex flex-col gap-2"
    >
      <h3
        v-if="group.domain"
        class="md-label-large"
      >
        {{ group.domain }}
      </h3>
      <ul class="flex flex-col gap-1 md-body-medium">
        <li
          v-for="(error, index) in group.errors"
          :key="index"
        >
          <span
            v-if="error.label"
            class="font-medium"
          >{{ error.label }}: </span>{{ error.message }}
        </li>
      </ul>
    </div>
  </section>
</template>
