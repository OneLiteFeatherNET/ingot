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
import { countLabel, formatBytes, formatDateTime } from './format'
import { isNothingToDelete } from './cleanup'

/*
 * What a dry run of a policy would delete. Nothing is deleted by showing it; the list is the
 * answer to "what would a run remove now", computed by the server at the time shown.
 */
const props = defineProps({
  preview: {
    type: Object,
    required: true
  }
})

const empty = computed(() => isNothingToDelete(props.preview))
</script>

<template>
  <div class="flex flex-col gap-4">
    <p class="md-body-large text-on-surface">
      <template v-if="empty">
        Nothing would be deleted.
      </template>
      <template v-else>
        {{ countLabel(preview.count, 'artifact') }} would be deleted,
        <span class="md-title-medium">{{ formatBytes(preview.totalBytes) }}</span> in total.
      </template>
    </p>
    <p
      v-if="preview.evaluatedAt"
      class="md-body-small text-on-surface-variant"
    >
      Evaluated {{ formatDateTime(preview.evaluatedAt) }}
    </p>

    <ul
      v-if="preview.warnings.length"
      class="flex flex-col gap-2"
      role="status"
      aria-label="Warnings"
    >
      <li
        v-for="warning in preview.warnings"
        :key="warning"
        class="md-body-medium text-warning"
      >
        {{ warning }}
      </li>
    </ul>

    <div
      v-if="!empty"
      class="max-h-[60dvh] overflow-auto rounded-corner-small border border-outline-variant"
    >
      <table class="w-full min-w-[32rem] border-collapse text-left">
        <caption class="sr-only">
          Artifacts a run would delete
        </caption>
        <thead class="sticky top-0 bg-surface-container-high">
          <tr class="md-label-large text-on-surface-variant">
            <th
              scope="col"
              class="px-3 py-2"
            >
              Repository
            </th>
            <th
              scope="col"
              class="px-3 py-2"
            >
              Path
            </th>
            <th
              scope="col"
              class="px-3 py-2"
            >
              Version
            </th>
            <th
              scope="col"
              class="px-3 py-2 text-right"
            >
              Size
            </th>
            <th
              scope="col"
              class="px-3 py-2"
            >
              Reason
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in preview.deletions"
            :key="`${row.repository}:${row.path}`"
            class="border-t border-outline-variant align-top md-body-medium text-on-surface"
          >
            <td class="px-3 py-2">
              {{ row.repository }}
            </td>
            <td class="px-3 py-2 font-mono md-body-small break-words">
              {{ row.path }}
            </td>
            <td class="px-3 py-2">
              {{ row.version ?? '' }}
            </td>
            <td class="px-3 py-2 text-right whitespace-nowrap">
              {{ formatBytes(row.sizeBytes) }}
            </td>
            <td class="px-3 py-2 md-body-small text-on-surface-variant">
              {{ row.reason }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
