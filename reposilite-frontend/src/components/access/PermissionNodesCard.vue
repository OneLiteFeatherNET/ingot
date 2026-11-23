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
import { computed, onMounted, useId, watch, type PropType } from 'vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdSection from '../md/MdSection.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import NodeEditor from './nodes/NodeEditor.vue'
import { permissionNodesApi } from './nodes/permissionNodesApi'
import { teamParentsApi } from './nodes/teamParentsApi'
import { useInheritedNodes } from './nodes/useInheritedNodes'
import { usePermissionNodes, type SaveResult } from './nodes/usePermissionNodes'

/*
 * The "Permission nodes" card of a user, a machine account or a team: it loads the nodes of the
 * subject, hands them to the editor and saves the draft with the entity tag it loaded (the
 * conflict dialog of the editor settles a 412). The editor's own bar is the only save control.
 *
 * Props:
 *   subjectKind  principal or team
 *   name         the user, machine account or team
 *   inheritFrom  the teams whose nodes apply to the subject without being its own: the teams of a
 *                principal, the parents of a team. Their ancestors are read as well, and all of
 *                it is shown read only under the editor.
 *   inheritDepth the depth of the inheritFrom teams: 0 for the teams a principal belongs to, 1
 *                for the parents of a team
 * Emits saved after the nodes were written, so the page can read the subject again (a badge such
 * as instance admin may depend on its nodes).
 */
const props = defineProps({
  subjectKind: {
    type: String as PropType<'principal' | 'team'>,
    required: true,
    validator: (value) => ['principal', 'team'].includes(value as string)
  },
  name: {
    type: String,
    required: true
  },
  inheritFrom: {
    type: Array as PropType<string[]>,
    default: () => []
  },
  inheritDepth: {
    type: Number,
    default: 1
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

const inherited = useInheritedNodes(
  () => ({ teams: props.inheritFrom, depth: props.inheritDepth }),
  () => teamParentsApi(client.value)
)

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

// The inherited nodes follow the teams they come from, and the subject.
watch(
  () => JSON.stringify([props.subjectKind, props.name, props.inheritDepth, props.inheritFrom]),
  () => inherited.load(),
  { immediate: true }
)

// A rejected write is reported as a toast unless the editor already shows it on the rows.
const settle = (result: SaveResult) => {
  if (result.ok) {
    createSuccessToast('Permission nodes saved')
    emit('saved')
  } else if (!result.conflict && Object.keys(rowErrors.value).length === 0 && !formError.value) {
    createErrorToast(result.message!)
  }
}

const save = async () => settle(await nodes.save())
const overwrite = async () => settle(await nodes.overwriteConflict())
</script>

<template>
  <MdCard
    variant="low"
    data-test="permission-nodes"
  >
    <div
      class="flex flex-col gap-3"
      data-ingot-component="permission-nodes-card"
    >
      <MdSection
        title="Permission nodes"
        description="Each node allows or denies one action. A more specific node beats a wildcard, and a deny wins a tie."
        :heading-id="titleId"
        :aria-labelledby="titleId"
      >
        <MdStatePanel
          v-if="failed"
          state="error"
          title="Could not load the permission nodes"
          :description="failure"
          data-test="nodes-error"
        >
          <template #actions>
            <MdButton
              variant="tonal"
              data-test="nodes-retry"
              @click="nodes.load()"
            >
              Retry
            </MdButton>
          </template>
        </MdStatePanel>
        <MdAlert
          v-else-if="missing"
          tone="error"
          data-test="nodes-missing"
        >
          {{ name }} does not exist (any more).
        </MdAlert>
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
          :inherited="inherited.entries.value"
          :inherited-warnings="inherited.warnings.value"
          @update:nodes="nodes.setDraft"
          @save="save()"
          @discard="nodes.discard()"
          @conflict-reload="nodes.reloadConflict()"
          @conflict-overwrite="overwrite()"
          @conflict-dismiss="nodes.dismissConflict()"
        />
      </MdSection>
    </div>
  </MdCard>
</template>
