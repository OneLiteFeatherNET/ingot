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
import { computed, ref, watch } from 'vue'
import MdButton from '../md/MdButton.vue'
import TokenTag from '../tokens/TokenTag.vue'
import GrantEditorDialog from './GrantEditorDialog.vue'
import { ROLES, grantPayload, grantScopeLabel, pathPrefixLabel, roleExplanation } from './accessTeams'

/*
 * The grants of a team. Rows are added, edited and removed in a draft; "Save grants" then sends
 * the whole list to `commit` (PUT grants, with If-Match), and "Discard" drops the draft. A new
 * answer from the server replaces the draft.
 *
 * Props:
 *   grants        [{ repository, pathPrefix, role }] as the server has them
 *   repositories  the ids of the repositories the editor offers
 *   commit        async (grants) => { ok, message } makes the request
 */
const props = defineProps({
  grants: {
    type: Array,
    required: true
  },
  repositories: {
    type: Array,
    default: () => []
  },
  commit: {
    type: Function,
    required: true
  }
})

const draft = ref([])
const editorOpen = ref(false)
const editing = ref(-1)
const busy = ref(false)
const failure = ref('')

const toDraft = (grants) => grants.map(grant => grantPayload(grant))
// Only a change of the stored grants replaces the draft, not any other new answer for the team.
watch(() => JSON.stringify(props.grants), () => { draft.value = toDraft(props.grants) }, { immediate: true })

const dirty = computed(() => JSON.stringify(draft.value) !== JSON.stringify(toDraft(props.grants)))

const rows = computed(() => draft.value.map((grant, index) => ({
  index,
  grant,
  scope: grantScopeLabel(grant.repository),
  prefix: pathPrefixLabel(grant.pathPrefix),
  role: ROLES.find(entry => entry.value === grant.role)?.label ?? grant.role,
  explanation: roleExplanation(grant.role)
})))

const editedGrant = computed(() => (editing.value >= 0 ? draft.value[editing.value] : null))

const openAdd = () => {
  editing.value = -1
  editorOpen.value = true
}

const openEdit = (index) => {
  editing.value = index
  editorOpen.value = true
}

const apply = (grant) => {
  if (editing.value >= 0) {
    draft.value = draft.value.map((entry, index) => (index === editing.value ? grant : entry))
  } else {
    draft.value = [...draft.value, grant]
  }
}

const removeAt = (index) => {
  draft.value = draft.value.filter((_, position) => position !== index)
}

const discard = () => {
  failure.value = ''
  draft.value = toDraft(props.grants)
}

const save = async () => {
  busy.value = true
  failure.value = ''
  try {
    const result = await props.commit(draft.value.map(grant => grantPayload(grant)))
    if (!result.ok) failure.value = result.message ?? ''
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    aria-labelledby="team-grants-title"
    data-test="grants"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h4
        id="team-grants-title"
        class="md-title-medium text-on-surface"
      >
        Grants
      </h4>
      <MdButton
        variant="tonal"
        data-test="add-grant"
        @click="openAdd()"
      >
        Add grant
      </MdButton>
    </div>

    <ul
      v-if="rows.length > 0"
      class="m-0 flex list-none flex-col gap-1 p-0"
    >
      <li
        v-for="row in rows"
        :key="row.index"
        data-test="grant"
        class="flex flex-wrap items-start gap-x-3 gap-y-2 rounded-corner-medium px-2 py-2"
      >
        <div class="flex min-w-0 flex-1 flex-col gap-1">
          <span
            class="md-title-small [overflow-wrap:anywhere]"
            data-test="grant-repository"
          >{{ row.scope }}</span>
          <span
            class="md-body-medium text-on-surface-variant [overflow-wrap:anywhere]"
            data-test="grant-prefix"
          >{{ row.prefix }}</span>
          <span class="flex flex-wrap items-center gap-2">
            <TokenTag data-test="grant-role">{{ row.role }}</TokenTag>
            <span
              class="md-body-small text-on-surface-variant"
              data-test="grant-explanation"
            >{{ row.explanation }}</span>
          </span>
        </div>
        <span class="flex items-center gap-1">
          <MdButton
            variant="text"
            data-test="edit-grant"
            :aria-label="`Edit the grant on ${row.scope}`"
            @click="openEdit(row.index)"
          >
            Edit
          </MdButton>
          <MdButton
            variant="text"
            error
            data-test="remove-grant"
            :aria-label="`Remove the grant on ${row.scope}`"
            @click="removeAt(row.index)"
          >
            Remove
          </MdButton>
        </span>
      </li>
    </ul>
    <p
      v-else
      class="md-body-medium text-on-surface-variant"
    >
      This team has no grants yet.
    </p>

    <p
      v-if="failure"
      role="alert"
      data-test="grants-failure"
      class="md-body-medium text-error"
    >
      {{ failure }}
    </p>
    <div
      v-if="dirty"
      class="flex flex-wrap justify-end gap-2"
    >
      <MdButton
        variant="text"
        data-test="discard-grants"
        :disabled="busy"
        @click="discard()"
      >
        Discard
      </MdButton>
      <MdButton
        data-test="save-grants"
        :disabled="busy"
        @click="save()"
      >
        Save grants
      </MdButton>
    </div>

    <GrantEditorDialog
      v-model="editorOpen"
      :grant="editedGrant"
      :repositories="repositories"
      @save="apply"
    />
  </section>
</template>
