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
import { computed, onMounted, ref, watch } from 'vue'
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdSwitch from '../md/MdSwitch.vue'
import MdTextArea from '../md/MdTextArea.vue'
import MdTextField from '../md/MdTextField.vue'
import ConflictDialog from '../settings/ConflictDialog.vue'
import TokenTag from '../tokens/TokenTag.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import TeamGrantsCard from './TeamGrantsCard.vue'
import TeamMembersCard from './TeamMembersCard.vue'
import { grantPayload, manualMembers } from './accessTeams'
import { useTeamDetail } from './useTeamDetail'

/*
 * The detail of one team: the header (name, description, external reference, the instance admin
 * switch and the delete action), the members and the grants.
 *
 * Every write sends the entity tag of the detail as If-Match. A 412 opens the conflict dialog.
 * A rejection that names a reason, such as the last instance admin, is shown with the server's
 * own wording in the dialog that caused it.
 *
 * Props:
 *   name  the team to show
 * Emits changed (after any write), deleted (the name, after the team was deleted) and back (the
 * link that closes the detail on a narrow window, where list and detail do not fit side by side).
 */
const props = defineProps({
  name: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['changed', 'deleted', 'back'])

const { client } = useSession()
const api = computed(() => client.value.identity.teams)

const team = useTeamDetail(() => props.name, () => api.value)
const { detail, status, failure, conflict } = team

const principals = ref([])
const principalsError = ref('')
const repositories = ref([])

onMounted(() => {
  team.load()
  loadPrincipals()
  loadRepositories()
})

watch(() => props.name, () => {
  team.reset()
  team.load()
})

const loadPrincipals = async () => {
  try {
    const response = await client.value.identity.principals.list()
    principals.value = response.data ?? []
    principalsError.value = ''
  } catch (error) {
    principalsError.value = errorMessage(error)
  }
}

const loadRepositories = async () => {
  try {
    const response = await client.value.maven.repositories()
    repositories.value = (response.data?.repositories ?? []).map(repository => repository.id)
  } catch {
    // The editor still offers "Every repository" and the repository of the grant being edited.
    repositories.value = []
  }
}

// A write: the helper sends the tag; a conflict is the dialog's business, a failure is returned.
const commit = async (run) => {
  const result = await team.write(run)
  if (result.ok) emit('changed')
  return result
}

const setMembers = (names) => commit(etag => api.value.setMembers(props.name, names, etag))

const setGrants = async (grants) => {
  const result = await commit(etag => api.value.setGrants(props.name, grants.map(grantPayload), etag))
  if (result.ok) createSuccessToast('Grants saved')
  else if (!result.conflict) createErrorToast(result.message)
  return result
}

// The description and the external reference.
const editing = ref(false)
const editDescription = ref('')
const editExternalRef = ref('')
const editBusy = ref(false)

const startEdit = () => {
  editDescription.value = detail.value.description ?? ''
  editExternalRef.value = detail.value.externalRef ?? ''
  editing.value = true
}

const saveEdit = async () => {
  editBusy.value = true
  const body = {
    description: editDescription.value.trim(),
    externalRef: editExternalRef.value.trim() === '' ? null : editExternalRef.value.trim()
  }
  const result = await commit(etag => api.value.update(props.name, body, etag))
  editBusy.value = false
  if (result.ok) {
    editing.value = false
    createSuccessToast(`${props.name} updated`)
  } else if (!result.conflict) {
    createErrorToast(result.message)
  } else {
    editing.value = false
  }
}

// The instance admin switch asks first and then grants or revokes.
const adminOpen = ref(false)
const adminTarget = ref(false)
const adminBusy = ref(false)
const adminFailure = ref('')

const askAdmin = (value) => {
  adminTarget.value = value
  adminFailure.value = ''
  adminOpen.value = true
}

const changeAdmin = async () => {
  adminBusy.value = true
  adminFailure.value = ''
  const granting = adminTarget.value
  const result = await commit(etag => (granting
    ? api.value.grantInstanceAdmin(props.name, etag)
    : api.value.revokeInstanceAdmin(props.name, etag)))
  adminBusy.value = false
  if (result.ok) {
    adminOpen.value = false
    createSuccessToast(granting
      ? `${props.name} is an instance admin team now`
      : `${props.name} is no instance admin team any more`)
  } else if (result.conflict) {
    adminOpen.value = false
  } else {
    adminFailure.value = result.message
  }
}

// Deleting asks for the name.
const deleteOpen = ref(false)
const deleteBusy = ref(false)
const deleteFailure = ref('')

const askDelete = () => {
  deleteFailure.value = ''
  deleteOpen.value = true
}

const removeTeam = async () => {
  deleteBusy.value = true
  deleteFailure.value = ''
  const result = await team.remove()
  deleteBusy.value = false
  if (result.ok) {
    deleteOpen.value = false
    createSuccessToast(`Team ${props.name} deleted`)
    emit('deleted', props.name)
  } else {
    deleteFailure.value = result.message
  }
}

const overwrite = async () => {
  const result = await team.overwriteConflict()
  if (result.ok) emit('changed')
  else if (result.message) createErrorToast(result.message)
}

const manualCount = computed(() => (detail.value ? manualMembers(detail.value).length : 0))
</script>

<template>
  <section
    class="flex min-w-0 flex-col gap-4"
    aria-labelledby="team-title"
  >
    <MdButton
      variant="text"
      class="self-start min-[840px]:hidden"
      data-test="back"
      @click="emit('back')"
    >
      Back to teams
    </MdButton>

    <p
      v-if="status === 'loading'"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="status === 'missing'"
      role="alert"
      class="md-body-large text-on-surface-variant"
      data-test="missing"
    >
      {{ name }} does not exist (any more).
    </div>
    <div
      v-else-if="status === 'error'"
      role="alert"
      class="flex flex-wrap items-center gap-3"
    >
      <span class="md-body-medium text-error">Could not load {{ name }}: {{ failure }}</span>
      <MdButton
        variant="text"
        @click="team.load()"
      >
        Retry
      </MdButton>
    </div>

    <template v-else-if="detail">
      <div class="flex flex-col gap-4 rounded-corner-large bg-surface-container-low px-4 py-4">
        <div class="flex flex-wrap items-start justify-between gap-2">
          <div class="flex min-w-0 flex-col gap-2">
            <h3
              id="team-title"
              class="md-title-large break-words text-on-surface"
            >
              {{ detail.name }}
            </h3>
            <div
              v-if="detail.instanceRole"
              class="flex flex-wrap items-center gap-2"
            >
              <TokenTag
                accent
                data-test="admin-badge"
              >
                Instance admin
              </TokenTag>
            </div>
          </div>
          <MdButton
            variant="outlined"
            error
            data-test="delete-team"
            @click="askDelete()"
          >
            Delete team
          </MdButton>
        </div>

        <form
          v-if="editing"
          class="flex flex-col gap-4"
          data-test="edit-form"
          @submit.prevent="saveEdit()"
        >
          <MdTextArea
            id="team-description"
            v-model="editDescription"
            label="Description"
          />
          <MdTextField
            id="team-external-ref"
            v-model="editExternalRef"
            label="External reference"
            type="text"
            autocomplete="off"
            supporting-text="The group of your identity provider this team stands for."
          />
          <div class="flex flex-wrap justify-end gap-2">
            <MdButton
              variant="text"
              :disabled="editBusy"
              @click="editing = false"
            >
              Cancel
            </MdButton>
            <MdButton
              type="submit"
              data-test="save-details"
              :disabled="editBusy"
            >
              Save
            </MdButton>
          </div>
        </form>
        <dl
          v-else
          class="m-0 grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 md-body-medium"
        >
          <dt class="text-on-surface-variant">
            Description
          </dt>
          <dd
            class="m-0 min-w-0 break-words text-on-surface"
            data-test="description"
          >
            {{ detail.description || 'None' }}
          </dd>
          <dt class="text-on-surface-variant">
            External reference
          </dt>
          <dd
            class="m-0 min-w-0 break-words text-on-surface"
            data-test="external-ref"
          >
            {{ detail.externalRef || 'None' }}
          </dd>
          <dt class="text-on-surface-variant">
            Members
          </dt>
          <dd class="m-0 text-on-surface">
            {{ detail.members.length }} ({{ manualCount }} added by hand)
          </dd>
        </dl>
        <MdButton
          v-if="!editing"
          variant="tonal"
          class="self-start"
          data-test="edit-details"
          @click="startEdit()"
        >
          Edit details
        </MdButton>

        <div class="flex flex-col gap-1">
          <MdSwitch
            data-test="instance-admin"
            :model-value="detail.instanceRole"
            @update:model-value="askAdmin"
          >
            Instance admin
          </MdSwitch>
          <p class="md-body-small text-on-surface-variant">
            Members of an instance admin team can do every action on every repository, and manage users, teams and settings.
          </p>
        </div>
      </div>

      <TeamMembersCard
        :team="detail"
        :principals="principals"
        :principals-error="principalsError"
        :commit="setMembers"
      />
      <TeamGrantsCard
        :grants="detail.grants"
        :repositories="repositories"
        :commit="setGrants"
      />
    </template>

    <ConfirmActionDialog
      v-model="adminOpen"
      :title="adminTarget ? 'Make instance admin team' : 'Remove instance admin'"
      :confirm-label="adminTarget ? 'Make instance admin' : 'Remove instance admin'"
      :danger="!adminTarget"
      :busy="adminBusy"
      :failure="adminFailure"
      @confirm="changeAdmin()"
    >
      <template v-if="adminTarget">
        Every member of {{ name }} will be able to do every action on every repository, and manage users, teams, tokens and settings.
      </template>
      <template v-else>
        The members of {{ name }} lose the instance admin role. Their grants stay.
      </template>
    </ConfirmActionDialog>
    <ConfirmActionDialog
      v-model="deleteOpen"
      title="Delete team"
      confirm-label="Delete"
      danger
      :type-to-confirm="name"
      type-label="Type the team name to confirm"
      :busy="deleteBusy"
      :failure="deleteFailure"
      @confirm="removeTeam()"
    >
      {{ name }} and its grants are deleted. The members stay, but lose what the team gave them. This cannot be undone.
    </ConfirmActionDialog>
    <ConflictDialog
      :conflict="conflict"
      @reload="team.reloadConflict()"
      @overwrite="overwrite()"
      @dismiss="team.dismissConflict()"
    />
  </section>
</template>
