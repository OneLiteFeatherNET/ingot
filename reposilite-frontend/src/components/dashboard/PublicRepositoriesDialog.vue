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
import { computed, useId } from 'vue'
import { useRouter } from 'vue-router'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'

/*
 * The review behind the "Review public repositories" step. It lists the public repositories and asks
 * the admin to confirm them. Each row leads to that repository's visibility setting. The confirmation
 * itself is stored by the card, which owns the acknowledged set.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  // The public repositories, already sorted: each has an id and an optional description.
  repositories: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const router = useRouter()
const titleId = useId()
const explanationId = useId()

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const hrefOf = (repository) => router.resolve(visibilityRoute(repository)).href

const visibilityRoute = (repository) => `/settings/maven/repositories/${encodeURIComponent(repository.id)}`

const follow = (repository) => {
  open.value = false
  router.push(visibilityRoute(repository))
}
</script>

<template>
  <VueFinalModal
    v-model="open"
    teleport-to="body"
    :overlay-class="'vfm__overlay bg-scrim/32!'"
    class="flex items-center justify-center p-4"
  >
    <section
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="explanationId"
      class="flex w-full max-w-md flex-col gap-4 rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
    >
      <div class="flex flex-col gap-2">
        <h2
          :id="titleId"
          class="md-headline-small"
        >
          Public repositories
        </h2>
        <p
          :id="explanationId"
          class="md-body-medium text-on-surface-variant"
        >
          Anyone can read these repositories without signing in.
        </p>
      </div>

      <ul
        v-if="repositories.length > 0"
        aria-label="Public repositories"
        class="flex max-h-[50vh] flex-col gap-2 overflow-y-auto"
      >
        <li
          v-for="repository in repositories"
          :key="repository.id"
          class="flex flex-wrap items-center gap-3 rounded-corner-medium bg-surface-container p-3"
        >
          <div class="flex min-w-0 flex-1 basis-40 flex-col">
            <span class="md-title-small break-all">{{ repository.id }}</span>
            <span class="md-body-medium text-on-surface-variant">{{ repository.description || 'No description' }}</span>
          </div>
          <MdButton
            variant="text"
            :href="hrefOf(repository)"
            :aria-label="`Change visibility of ${repository.id}`"
            @click.prevent="follow(repository)"
          >
            Change visibility
          </MdButton>
        </li>
      </ul>
      <p
        v-else
        class="md-body-medium text-on-surface-variant"
      >
        No repository is public.
      </p>

      <div
        v-if="repositories.length > 0"
        class="flex justify-end"
      >
        <MdButton
          variant="filled"
          type="button"
          @click="emit('confirm')"
        >
          Looks right
        </MdButton>
      </div>
    </section>
  </VueFinalModal>
</template>
