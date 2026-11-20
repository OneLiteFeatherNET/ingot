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
import { computed, ref, type PropType } from 'vue'
import type { AxiosError, AxiosResponse } from 'axios'
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import { etagOf, isPreconditionFailed } from '../../store/client'
import MoreIcon from '../icons/MoreIcon.vue'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import type { MenuItem } from '../md/types'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import ResetPasswordDialog from './ResetPasswordDialog.vue'
import { detailOf, type PrincipalDetailAnswer, type PrincipalSummary, type PrincipalsApi } from './accessPrincipals'

/*
 * The actions on one principal, in a menu: reset the password (users only), disable or enable,
 * make or remove the instance admin role (users only) and delete. Each one that changes
 * something asks first, or runs at once for disable and enable, which are undone with one click.
 *
 * Props:
 *   principal      { name, kind, displayName, disabled }
 *   instanceAdmin  whether the principal holds the instance admin role itself. It decides
 *                  which of "Make" and "Remove" the menu offers.
 *   api            { get, update, setPassword, grantInstanceAdmin, revokeInstanceAdmin, remove }
 * Emits:
 *   changed  the principal changed; the PrincipalDetailDto the server answered with, or nothing
 *            when the caller has to read it again (after a conflict)
 *   deleted  the principal was deleted
 *   toggle   the menu opened (true) or closed (false). A list row is its own stacking context, so
 *            the row of an open menu has to be raised above the rows that follow it.
 *
 * The menu is the shared MdOverflowMenu: it is teleported and placed from its trigger, follows the
 * trigger on scroll and closes on Escape, on a press outside of it and when the focus leaves it.
 * The change item is disabled while a change is running.
 *
 * Disable and enable read the principal again right before the write and send the entity tag
 * of that fresh read as If-Match, so a change made elsewhere in the meantime answers 412
 * instead of being overwritten. A rejection that names a reason, such as the last instance
 * admin, is shown with the server's own wording: in the dialog that caused it, or in a snackbar.
 */
const props = defineProps({
  principal: {
    type: Object as PropType<Pick<PrincipalSummary, 'name' | 'kind' | 'displayName' | 'disabled'>>,
    required: true
  },
  instanceAdmin: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object as PropType<Pick<PrincipalsApi, 'get' | 'update' | 'setPassword' | 'grantInstanceAdmin' | 'revokeInstanceAdmin' | 'remove'>>,
    required: true
  }
})

const emit = defineEmits(['changed', 'deleted', 'toggle'])

const passwordOpen = ref(false)
const adminOpen = ref(false)
const deleteOpen = ref(false)
const busy = ref(false)
const failure = ref('')

const isUser = computed(() => props.principal.kind === 'USER')
const noun = computed(() => (isUser.value ? 'user' : 'machine account'))
const label = computed(() => props.principal.displayName || props.principal.name)
const menuLabel = computed(() => `Actions for ${props.principal.name}`)

// The change item is disabled while a change runs, so a second press cannot start another one.
const menuItems = computed<MenuItem[]>(() => [
  ...(isUser.value ? [{ value: 'reset-password', label: 'Reset password' }] : []),
  { value: 'toggle-disabled', label: props.principal.disabled ? 'Enable' : 'Disable', disabled: busy.value },
  ...(isUser.value ? [{ value: 'toggle-admin', label: props.instanceAdmin ? 'Remove instance admin' : 'Make instance admin' }] : []),
  { value: 'delete', label: 'Delete', error: true }
])

// The menu has closed by the time an item is chosen: the choice opens its dialog or runs its change.
const choose = (value: string) => {
  failure.value = ''
  if (value === 'reset-password') passwordOpen.value = true
  else if (value === 'toggle-disabled') toggleDisabled()
  else if (value === 'toggle-admin') adminOpen.value = true
  else if (value === 'delete') deleteOpen.value = true
}

const toggleDisabled = async () => {
  const disabling = !props.principal.disabled
  busy.value = true
  try {
    const fresh: AxiosResponse = await props.api.get(props.principal.name)
    const response = await props.api.update(props.principal.name, { disabled: disabling }, etagOf(fresh))
    createSuccessToast(`${props.principal.name} ${disabling ? 'disabled' : 'enabled'}`)
    emit('changed', detailOf(response?.data))
  } catch (error) {
    if (isPreconditionFailed(error as AxiosError)) {
      createErrorToast(`${props.principal.name} was changed in the meantime. The list is up to date again, please retry.`)
      emit('changed')
    } else {
      createErrorToast(errorMessage(error))
    }
  } finally {
    busy.value = false
  }
}

const changeAdmin = async () => {
  busy.value = true
  failure.value = ''
  const granting = !props.instanceAdmin
  try {
    const response: AxiosResponse<PrincipalDetailAnswer> = granting
      ? await props.api.grantInstanceAdmin(props.principal.name)
      : await props.api.revokeInstanceAdmin(props.principal.name)
    createSuccessToast(granting
      ? `${props.principal.name} is an instance admin now`
      : `${props.principal.name} is no instance admin any more`)
    emit('changed', detailOf(response?.data))
    adminOpen.value = false
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

const remove = async () => {
  busy.value = true
  failure.value = ''
  try {
    await props.api.remove(props.principal.name)
    createSuccessToast(`${props.principal.name} deleted`)
    emit('deleted', props.principal)
    deleteOpen.value = false
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div
    class="relative shrink-0"
    data-ingot-component="principal-actions-menu"
  >
    <MdOverflowMenu
      :label="menuLabel"
      :trigger-label="menuLabel"
      :items="menuItems"
      @select="choose"
      @toggle="$emit('toggle', $event)"
    >
      <template #trigger>
        <MoreIcon />
      </template>
    </MdOverflowMenu>

    <ResetPasswordDialog
      v-if="isUser"
      v-model="passwordOpen"
      :principal="principal"
      :api="api"
    />
    <ConfirmActionDialog
      v-model="adminOpen"
      :title="instanceAdmin ? 'Remove instance admin' : 'Make instance admin'"
      :confirm-label="instanceAdmin ? 'Remove instance admin' : 'Make instance admin'"
      :danger="instanceAdmin"
      :busy="busy"
      :failure="failure"
      @confirm="changeAdmin()"
    >
      <template v-if="instanceAdmin">
        {{ label }} will no longer manage users, teams and settings.
      </template>
      <template v-else>
        {{ label }} will be able to manage users, teams, tokens and settings, and reach every repository.
      </template>
    </ConfirmActionDialog>
    <ConfirmActionDialog
      v-model="deleteOpen"
      :title="`Delete ${noun}`"
      confirm-label="Delete"
      danger
      :type-to-confirm="principal.name"
      :type-label="`Type the ${isUser ? 'username' : 'name'} to confirm`"
      :busy="busy"
      :failure="failure"
      @confirm="remove()"
    >
      {{ principal.name }} and {{ isUser ? 'their' : 'its' }} tokens are deleted. This cannot be undone.
    </ConfirmActionDialog>
  </div>
</template>
