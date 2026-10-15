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
import { computed, onMounted, useId, watch } from 'vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import NodeEditor from './nodes/NodeEditor.vue'
import { permissionNodesApi } from './nodes/permissionNodesApi'
import { usePermissionNodes } from './nodes/usePermissionNodes'

/*
 * The "Permission nodes" card of a user, a machine account or a team: it loads the nodes of the
 * subject, hands them to the editor and saves the draft with the entity tag it loaded (the
 * conflict dialog of the editor settles a 412). The editor's own bar is the only save control.
 *
 * Props:
 *   subjectKind  principal or team
 *   name         the user, machine account or team
 * Emits saved after the nodes were written, so the page can read the subject again (a badge such
 * as instance admin may depend on its nodes).
 */
const props = defineProps({
  subjectKind: {
    type: String,
    required: true,
    validator: (value) => ['principal', 'team'].includes(value)
  },
  name: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['saved'])

const { client } = useSession()
const titleId = useId()

const nodes = usePermissionNodes(
  () => ({ kind: props.subjectKind, name: props.name }),
  () => permissionNodesApi(client.value)
)
const { draft, saved, status, failure, saving, rowErrors, formError, conflict, catalog, templates } = nodes

const loading = computed(() => status.value === 'loading')
const missing = computed(() => status.value === 'missing')
const failed = computed(() => status.value === 'error')

onMounted(() => {
  nodes.load()
  nodes.loadCatalog()
})

watch(() => [props.subjectKind, props.name], () => {
  nodes.reset()
  nodes.load()
})

// A rejected write is reported as a toast unless the editor already shows it on the rows.
const settle = (result) => {
  if (result.ok) {
    createSuccessToast('Permission nodes saved')
    emit('saved')
  } else if (!result.conflict && Object.keys(rowErrors.value).length === 0 && !formError.value) {
    createErrorToast(result.message)
  }
}

const save = async () => settle(await nodes.save())
const overwrite = async () => settle(await nodes.overwriteConflict())
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    :aria-labelledby="titleId"
    data-test="permission-nodes"
  >
    <div class="flex flex-col gap-1">
      <h4
        :id="titleId"
        class="md-title-medium text-on-surface"
      >
        Permission nodes
      </h4>
      <p class="md-body-small text-on-surface-variant">
        Each node allows or denies one action. A more specific node beats a wildcard, and a deny wins a tie.
      </p>
    </div>

    <div
      v-if="failed"
      role="alert"
      class="flex flex-wrap items-center gap-3"
      data-test="nodes-error"
    >
      <span class="md-body-medium text-error">Could not load the permission nodes: {{ failure }}</span>
      <MdButton
        variant="tonal"
        data-test="nodes-retry"
        @click="nodes.load()"
      >
        Retry
      </MdButton>
    </div>
    <p
      v-else-if="missing"
      role="alert"
      class="md-body-medium text-on-surface-variant"
      data-test="nodes-missing"
    >
      {{ name }} does not exist (any more).
    </p>
    <NodeEditor
      v-else
      :nodes="draft"
      :saved-nodes="saved"
      :row-errors="rowErrors"
      :form-error="formError"
      :loading="loading"
      :saving="saving"
      :catalog="catalog"
      :templates="templates"
      :conflict="conflict"
      @update:nodes="nodes.setDraft"
      @save="save()"
      @discard="nodes.discard()"
      @conflict-reload="nodes.reloadConflict()"
      @conflict-overwrite="overwrite()"
      @conflict-dismiss="nodes.dismissConflict()"
    />
  </section>
</template>
