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
import { computed, useId } from 'vue'
import { useRouter } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'

/*
 * The review behind the "Review public repositories" step. It lists the public repositories and asks
 * the admin to confirm them. Each row leads to that repository's visibility setting. The confirmation
 * itself is stored by the card, which owns the acknowledged set.
 */
// A public repository as the review lists it: its id and an optional description.
interface PublicRepository {
  id: string
  description?: string
}

const props = withDefaults(defineProps<{
  modelValue?: boolean
  // The public repositories, already sorted: each has an id and an optional description.
  repositories?: PublicRepository[]
}>(), {
  modelValue: false,
  repositories: () => []
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const router = useRouter()
const titleId = useId()
const explanationId = useId()

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const hrefOf = (repository: PublicRepository) => router.resolve(visibilityRoute(repository)).href

const visibilityRoute = (repository: PublicRepository) => `/settings/repositories/${encodeURIComponent(repository.id)}`

const follow = (repository: PublicRepository) => {
  open.value = false
  router.push(visibilityRoute(repository))
}
</script>

<template>
  <MdDialog
    v-model="open"
    size="md"
    :labelledby="titleId"
    :describedby="explanationId"
  >
    <template #header>
      <div
        class="flex flex-col gap-2"
        data-ingot-component="public-repositories-dialog"
      >
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
    </template>

    <div class="mt-4 flex flex-col gap-2">
      <ul
        v-if="repositories.length > 0"
        aria-label="Public repositories"
        class="flex flex-col gap-2"
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
    </div>

    <template
      v-if="repositories.length > 0"
      #actions
    >
      <MdDialogActions class="mt-6">
        <MdButton
          variant="filled"
          type="button"
          @click="emit('confirm')"
        >
          Looks right
        </MdButton>
      </MdDialogActions>
    </template>
  </MdDialog>
</template>
