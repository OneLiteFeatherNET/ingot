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
import { countLabel, formatBytes, formatDateTime } from './format'
import { isNothingToDelete } from './cleanup'
import type { CleanupPreview } from './cleanup'
import MdDataTable from '../md/MdDataTable.vue'

/*
 * What a dry run of a policy would delete. Nothing is deleted by showing it; the list is the
 * answer to "what would a run remove now", computed by the server at the time shown.
 */
const props = defineProps<{
  preview: CleanupPreview
}>()

const empty = computed(() => isNothingToDelete(props.preview))

const COLUMNS = [
  { key: 'repository', label: 'Repository' },
  { key: 'path', label: 'Path' },
  { key: 'version', label: 'Version' },
  { key: 'size', label: 'Size' },
  { key: 'reason', label: 'Reason' },
]

const rows = computed(() => props.preview.deletions.map((row) => ({
  repository: row.repository,
  path: row.path,
  version: row.version ?? '',
  size: row.sizeBytes,
  reason: row.reason,
})))

// The table gives every cell the same classes, so the path, the size and the reason get theirs here.
const CELL_CLASS: Record<string, string> = {
  repository: 'block text-on-surface',
  path: 'block font-mono md-body-small break-words text-on-surface',
  version: 'block text-on-surface',
  size: 'block text-right whitespace-nowrap text-on-surface',
  reason: 'block md-body-small',
}
</script>

<template>
  <div
    class="flex flex-col gap-4"
    data-ingot-component="dry-run-result"
  >
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
      <MdDataTable
        caption="Artifacts a run would delete"
        :columns="COLUMNS"
        :rows="rows"
      >
        <template #cell="{ column, value }">
          <span :class="CELL_CLASS[column.key]">{{ column.key === 'size' ? formatBytes(value) : value }}</span>
        </template>
      </MdDataTable>
    </div>
  </div>
</template>
