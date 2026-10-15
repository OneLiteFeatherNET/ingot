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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import { etagOf, isPreconditionFailed } from '../../store/client'
import MoreIcon from '../icons/MoreIcon.vue'
import MdIconButton from '../md/MdIconButton.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import ResetPasswordDialog from './ResetPasswordDialog.vue'
import { detailOf } from './accessPrincipals'

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
 * The open menu is teleported to the body and positioned with `fixed` from the bounding rect of
 * its trigger, because the list card clips whatever sticks out of it (overflow-hidden keeps its
 * rounded corners). It opens upwards when there is no room below. As the menu then no longer
 * follows its trigger, it closes on scroll and resize. It also closes on Escape (the focus goes
 * back to the trigger), on a press outside of it and when the focus leaves it.
 *
 * Disable and enable read the principal again right before the write and send the entity tag
 * of that fresh read as If-Match, so a change made elsewhere in the meantime answers 412
 * instead of being overwritten. A rejection that names a reason, such as the last instance
 * admin, is shown with the server's own wording: in the dialog that caused it, or in a snackbar.
 */
const props = defineProps({
  principal: {
    type: Object,
    required: true
  },
  instanceAdmin: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['changed', 'deleted', 'toggle'])

const menuOpen = ref(false)
const root = ref(null)
const menuElement = ref(null)
const placement = ref({})

const GAP = 4
const triggerElement = () => root.value?.querySelector('[data-test="actions"]')

const place = async () => {
  const trigger = triggerElement()
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  const right = `${window.innerWidth - rect.right}px`
  placement.value = { top: `${rect.bottom + GAP}px`, right }
  await nextTick()
  const height = menuElement.value?.getBoundingClientRect().height ?? 0
  if (rect.bottom + GAP + height > window.innerHeight && rect.top - GAP - height >= 0) {
    placement.value = { bottom: `${window.innerHeight - rect.top + GAP}px`, right }
  }
}

const closeMenu = () => {
  menuOpen.value = false
}

const insideMenu = (node) => root.value?.contains(node) || menuElement.value?.contains(node)

const onPointerdown = (event) => {
  if (!insideMenu(event.target)) closeMenu()
}

const listen = (on) => {
  const toggle = on ? 'addEventListener' : 'removeEventListener'
  document[toggle]('pointerdown', onPointerdown)
  window[toggle]('scroll', closeMenu, true)
  window[toggle]('resize', closeMenu)
}

watch(menuOpen, async (open) => {
  emit('toggle', open)
  listen(open)
  if (!open) return
  await place()
  menuElement.value?.querySelector('[role="menuitem"]:not(:disabled)')?.focus({ preventScroll: true })
})

onBeforeUnmount(() => listen(false))
const passwordOpen = ref(false)
const adminOpen = ref(false)
const deleteOpen = ref(false)
const busy = ref(false)
const failure = ref('')

const isUser = computed(() => props.principal.kind === 'USER')
const noun = computed(() => (isUser.value ? 'user' : 'machine account'))
const label = computed(() => props.principal.displayName || props.principal.name)

const choose = (action) => {
  menuOpen.value = false
  failure.value = ''
  action()
}

const onKeydown = (event) => {
  if (event.key !== 'Escape' || !menuOpen.value) return
  menuOpen.value = false
  triggerElement()?.focus()
}

// Closes the menu when the focus leaves it, so a click elsewhere does not leave it hanging open.
const onFocusout = (event) => {
  if (!insideMenu(event.relatedTarget)) menuOpen.value = false
}

const toggleDisabled = async () => {
  const disabling = !props.principal.disabled
  busy.value = true
  try {
    const fresh = await props.api.get(props.principal.name)
    const response = await props.api.update(props.principal.name, { disabled: disabling }, etagOf(fresh))
    createSuccessToast(`${props.principal.name} ${disabling ? 'disabled' : 'enabled'}`)
    emit('changed', detailOf(response?.data))
  } catch (error) {
    if (isPreconditionFailed(error)) {
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
    const response = granting
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

const itemClass = 'md-state-layer relative flex w-full items-center rounded-corner-small px-3 py-2 text-left md-label-large text-on-surface'
</script>

<template>
  <div
    ref="root"
    class="relative shrink-0"
    @keydown="onKeydown"
    @focusout="onFocusout"
  >
    <MdIconButton
      data-test="actions"
      :aria-label="`Actions for ${principal.name}`"
      aria-haspopup="menu"
      :aria-expanded="menuOpen ? 'true' : 'false'"
      @click="menuOpen = !menuOpen"
    >
      <MoreIcon />
    </MdIconButton>
    <Teleport to="body">
      <div
        v-if="menuOpen"
        ref="menuElement"
        role="menu"
        :aria-label="`Actions for ${principal.name}`"
        :style="{ position: 'fixed', ...placement }"
        class="z-50 flex w-56 max-w-[calc(100vw-2rem)] flex-col gap-1 rounded-corner-extra-small bg-surface-container p-2 shadow-elevation-2"
        @keydown="onKeydown"
        @focusout="onFocusout"
      >
        <button
          v-if="isUser"
          type="button"
          role="menuitem"
          data-test="reset-password"
          :class="itemClass"
          @click="choose(() => (passwordOpen = true))"
        >
          Reset password
        </button>
        <button
          type="button"
          role="menuitem"
          data-test="toggle-disabled"
          :class="itemClass"
          :disabled="busy"
          @click="choose(toggleDisabled)"
        >
          {{ principal.disabled ? 'Enable' : 'Disable' }}
        </button>
        <button
          v-if="isUser"
          type="button"
          role="menuitem"
          data-test="toggle-admin"
          :class="itemClass"
          @click="choose(() => (adminOpen = true))"
        >
          {{ instanceAdmin ? 'Remove instance admin' : 'Make instance admin' }}
        </button>
        <button
          type="button"
          role="menuitem"
          data-test="delete"
          :class="[itemClass, '!text-error']"
          @click="choose(() => (deleteOpen = true))"
        >
          Delete
        </button>
      </div>
    </Teleport>

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
      {{ label }} and {{ isUser ? 'their' : 'its' }} tokens are deleted. This cannot be undone.
    </ConfirmActionDialog>
  </div>
</template>
