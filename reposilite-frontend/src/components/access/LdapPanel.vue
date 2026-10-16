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
import { useSession } from '../../store/session'
import { errorMessage, createSuccessToast } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdChipInput from '../md/MdChipInput.vue'
import MdSwitch from '../md/MdSwitch.vue'
import MdTextField from '../md/MdTextField.vue'
import { validateLdap } from './loginSettings'
import { useLoginSettings } from './useLoginSettings'

/*
 * The LDAP directory that signs users in to the dashboard. One form for the connection, the bind,
 * the user and group search and the admin groups; the test looks up an optional user against the
 * saved configuration, so it is offered only while the form matches what is saved.
 *
 * The directory that Maven clients use for basic authentication is configured elsewhere, in the
 * authentication settings, and does not change when this one does.
 */
const { client } = useSession()
const {
  settings,
  loaded,
  loadError,
  saving,
  saveError,
  load,
  save
} = useLoginSettings(() => client.value.settings)

const clone = (value) => JSON.parse(JSON.stringify(value))

const form = ref(null)
const submitted = ref(false)
const testUsername = ref('')
const testPassword = ref('')
const testing = ref(false)
const testResult = ref(null)

const saved = computed(() => settings.value.ldap)
const dirty = computed(() => form.value !== null && JSON.stringify(form.value) !== JSON.stringify(saved.value))
const problems = computed(() => (form.value ? validateLdap(form.value) : {}))
const errorOf = (field) => (submitted.value ? problems.value[field] ?? '' : '')

// Once the server has answered, and again after each save, the form starts from what is stored.
watch(saved, (value) => {
  form.value = clone(value)
}, { immediate: true })

const submit = async () => {
  submitted.value = true
  if (Object.keys(problems.value).length > 0) return
  const ok = await save({ ...settings.value, ldap: clone(form.value) })
  if (ok) {
    submitted.value = false
    createSuccessToast('LDAP settings saved')
  }
}

// The test answers for the saved directory. An empty test name only checks the connection and the bind.
const test = async () => {
  testing.value = true
  const body = {}
  if (testUsername.value) body.username = testUsername.value
  if (testPassword.value) body.password = testPassword.value
  try {
    const response = await client.value.auth.testLdapProvider(body)
    testResult.value = { ...response.data, ok: response.data?.ok === true }
  } catch (error) {
    testResult.value = { ok: false, error: errorMessage(error) }
  } finally {
    testing.value = false
  }
}

const groupsOf = (groups) => (Array.isArray(groups) ? groups.join(', ') : '')

onMounted(load)
</script>

<template>
  <section
    aria-labelledby="ldap-title"
    class="flex flex-col gap-6"
    data-test="ldap"
  >
    <div class="flex flex-col gap-1">
      <h3
        id="ldap-title"
        class="md-title-large text-on-surface"
      >
        LDAP sign in
      </h3>
      <p class="md-body-medium text-on-surface-variant">
        Users with an account in the directory sign in with their directory username and password.
        The LDAP that Maven clients use for basic authentication is set in the authentication settings
        and is separate from this one.
      </p>
    </div>

    <p
      v-if="!loaded"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="loadError"
      role="alert"
      class="flex flex-wrap items-center gap-3"
    >
      <span class="md-body-medium text-error">Could not load the LDAP settings: {{ loadError }}</span>
      <MdButton
        variant="text"
        @click="load()"
      >
        Retry
      </MdButton>
    </div>

    <form
      v-else-if="form"
      id="ldap-form"
      class="flex flex-col gap-6"
      novalidate
      @submit.prevent="submit()"
    >
      <section
        class="flex flex-col gap-4"
        aria-labelledby="ldap-connection-title"
      >
        <h4
          id="ldap-connection-title"
          class="md-title-small text-on-surface"
        >
          Connection
        </h4>
        <MdSwitch
          id="ldap-enabled"
          v-model="form.enabled"
        >
          Enabled: offer LDAP sign in
        </MdSwitch>
        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="ldap-label"
            v-model="form.label"
            label="Label on the sign in form"
            type="text"
            :error="errorOf('label') !== ''"
            :error-text="errorOf('label')"
          />
          <MdTextField
            id="ldap-url"
            v-model="form.url"
            label="Server address"
            type="text"
            autocomplete="off"
            placeholder="ldaps://ldap.example.org:636"
            supporting-text="ldaps:// for TLS from the start, or ldap:// with StartTLS below"
            :error="errorOf('url') !== ''"
            :error-text="errorOf('url')"
          />
        </div>
        <MdSwitch
          id="ldap-start-tls"
          v-model="form.startTls"
        >
          Upgrade the connection with StartTLS
        </MdSwitch>
      </section>

      <section
        class="flex flex-col gap-4"
        aria-labelledby="ldap-bind-title"
      >
        <h4
          id="ldap-bind-title"
          class="md-title-small text-on-surface"
        >
          Bind
        </h4>
        <p class="md-body-medium text-on-surface-variant">
          The service account the server uses to search. Leave both empty to search anonymously.
        </p>
        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="ldap-bind-dn"
            v-model="form.bindDn"
            label="Bind DN"
            type="text"
            autocomplete="off"
          />
          <MdTextField
            id="ldap-bind-password"
            v-model="form.bindPassword"
            label="Bind password"
            type="password"
            autocomplete="off"
            supporting-text="A stored password shows as ********. Type a new value to replace it."
            :error="errorOf('bindPassword') !== ''"
            :error-text="errorOf('bindPassword')"
          />
        </div>
      </section>

      <section
        class="flex flex-col gap-4"
        aria-labelledby="ldap-users-title"
      >
        <h4
          id="ldap-users-title"
          class="md-title-small text-on-surface"
        >
          User search
        </h4>
        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="ldap-user-base"
            v-model="form.userBaseDn"
            label="User base DN"
            type="text"
            autocomplete="off"
            supporting-text="Where users are found, for example ou=people,dc=example,dc=org"
            :error="errorOf('userBaseDn') !== ''"
            :error-text="errorOf('userBaseDn')"
          />
          <MdTextField
            id="ldap-user-filter"
            v-model="form.userFilter"
            label="User filter"
            type="text"
            autocomplete="off"
            supporting-text="{username} stands for the username typed at sign in, as in (uid={username})"
            :error="errorOf('userFilter') !== ''"
            :error-text="errorOf('userFilter')"
          />
          <MdTextField
            id="ldap-attr-username"
            v-model="form.usernameAttribute"
            label="Username attribute"
            type="text"
            autocomplete="off"
            :error="errorOf('usernameAttribute') !== ''"
            :error-text="errorOf('usernameAttribute')"
          />
          <MdTextField
            id="ldap-attr-name"
            v-model="form.displayNameAttribute"
            label="Display name attribute"
            type="text"
            autocomplete="off"
          />
          <MdTextField
            id="ldap-attr-email"
            v-model="form.emailAttribute"
            label="Email attribute"
            type="text"
            autocomplete="off"
          />
        </div>
        <MdSwitch
          id="ldap-create-users"
          v-model="form.autoCreateUsers"
        >
          Create an account at the first sign in
        </MdSwitch>
      </section>

      <section
        class="flex flex-col gap-4"
        aria-labelledby="ldap-groups-title"
      >
        <h4
          id="ldap-groups-title"
          class="md-title-small text-on-surface"
        >
          Groups
        </h4>
        <p class="md-body-medium text-on-surface-variant">
          A team whose external reference is <code>ldap:&lt;group&gt;</code> is synced from the groups
          of this directory: its members follow the group, and they cannot be removed by hand.
        </p>
        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="ldap-group-base"
            v-model="form.groupBaseDn"
            label="Group base DN"
            type="text"
            autocomplete="off"
          />
          <MdTextField
            id="ldap-group-filter"
            v-model="form.groupFilter"
            label="Group filter"
            type="text"
            autocomplete="off"
            supporting-text="{dn} stands for the user DN, as in (member={dn}). {username} also works"
            :error="errorOf('groupFilter') !== ''"
            :error-text="errorOf('groupFilter')"
          />
          <MdTextField
            id="ldap-group-name"
            v-model="form.groupNameAttribute"
            label="Group name attribute"
            type="text"
            autocomplete="off"
          />
        </div>
        <MdChipInput
          id="ldap-admin-groups"
          v-model="form.adminGroups"
          label="Admin groups"
          supporting-text="Members of these groups become instance admins at sign in"
        />
      </section>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant pt-4">
        <p
          v-if="dirty"
          class="md-body-medium text-on-surface-variant"
        >
          You have unsaved changes.
        </p>
        <p
          v-if="saveError"
          role="alert"
          class="md-body-medium text-error"
        >
          {{ saveError }}
        </p>
        <span v-else-if="!dirty" />
        <MdButton
          type="submit"
          :disabled="saving"
        >
          {{ saving ? 'Saving...' : 'Save LDAP settings' }}
        </MdButton>
      </div>
    </form>

    <!-- The test sits apart from the form: it reads the saved directory, never the draft. -->
    <section
      v-if="form"
      class="flex flex-col gap-4 rounded-corner-medium bg-surface-container-low p-4"
      aria-labelledby="ldap-test-title"
    >
      <h4
        id="ldap-test-title"
        class="md-title-small text-on-surface"
      >
        Test connection
      </h4>
      <p class="md-body-medium text-on-surface-variant">
        {{ dirty
          ? 'Save your changes first. The test reads the saved directory.'
          : 'Connects with the saved settings. A user name and password also check that the user signs in.' }}
      </p>
      <div class="grid gap-4 sm:grid-cols-2">
        <MdTextField
          id="ldap-test-username"
          v-model="testUsername"
          label="Test username (optional)"
          type="text"
          autocomplete="off"
        />
        <MdTextField
          id="ldap-test-password"
          v-model="testPassword"
          label="Test password (optional)"
          type="password"
          autocomplete="off"
        />
      </div>
      <div class="flex flex-wrap items-center gap-3">
        <MdButton
          variant="outlined"
          :disabled="dirty || testing"
          @click="test()"
        >
          {{ testing ? 'Testing...' : 'Test connection' }}
        </MdButton>
      </div>
      <div
        v-if="testResult"
        role="status"
        data-test="ldap-test-result"
        class="md-body-medium"
        :class="testResult.ok ? 'text-success' : 'text-error'"
      >
        <p v-if="testResult.ok">
          Connected.
          <template v-if="testResult.userDn">User found: {{ testResult.userDn }}.</template>
          <template v-if="groupsOf(testResult.groups)">Groups: {{ groupsOf(testResult.groups) }}.</template>
        </p>
        <p v-else>
          The test failed: {{ testResult.error }}
        </p>
      </div>
    </section>
  </section>
</template>
