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
import { computed, onMounted, ref, watch } from 'vue'
import type { AxiosResponse } from 'axios'
import { useSession } from '../../store/session'
import { errorMessage, createSuccessToast } from '../../helpers/toast'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdChipInput from '../md/MdChipInput.vue'
import MdSection from '../md/MdSection.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import MdSwitch from '../md/MdSwitch.vue'
import MdTextField from '../md/MdTextField.vue'
import { LDAP_DEFAULTS, validateLdap, type LdapSettings } from './loginSettings'
import { useLoginSettings } from './useLoginSettings'
import AuthenticationGroupForm from './AuthenticationGroupForm.vue'

/*
 * The page has two LDAP directories. The first signs users in to the dashboard: one form for the
 * connection, the bind, the user and group search and the admin groups; the test looks up an optional
 * user against the saved configuration, so it is offered only while the form matches what is saved.
 * The second, below, is the directory that Maven and Gradle clients use for basic authentication. It
 * is the LDAP group of the authentication settings, edited with the same form as Settings had, and
 * saved on its own. Neither one changes when the other does.
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

/** The answer of the LDAP test: whether the bind and the optional user lookup worked. */
interface LdapTestResult {
  ok: boolean
  error?: string
  userDn?: string
  groups?: string[]
}

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T

// Set from the saved directory before the first render (the watch below is immediate), so it is never empty when drawn.
const form = ref<LdapSettings>(clone(LDAP_DEFAULTS))
const submitted = ref(false)
const testUsername = ref('')
const testPassword = ref('')
const testing = ref(false)
const testResult = ref<LdapTestResult | null>(null)

const saved = computed(() => settings.value.ldap)
const dirty = computed(() => form.value !== null && JSON.stringify(form.value) !== JSON.stringify(saved.value))
const problems = computed(() => (form.value ? validateLdap(form.value) : {}))
const errorOf = (field: string) => (submitted.value ? problems.value[field] ?? '' : '')

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
  const body: { username?: string; password?: string } = {}
  if (testUsername.value) body.username = testUsername.value
  if (testPassword.value) body.password = testPassword.value
  try {
    const response: AxiosResponse<LdapTestResult> = await client.value.auth.testLdapProvider(body)
    testResult.value = { ...response.data, ok: response.data?.ok === true }
  } catch (error) {
    testResult.value = { ok: false, error: errorMessage(error) }
  } finally {
    testing.value = false
  }
}

const groupsOf = (groups: unknown) => (Array.isArray(groups) ? groups.join(', ') : '')

onMounted(load)
</script>

<template>
  <section
    aria-labelledby="ldap-title"
    class="flex flex-col gap-6"
    data-test="ldap"
    data-ingot-component="ldap-panel"
  >
    <MdSection
      title="Dashboard sign in"
      heading-id="ldap-title"
      aria-labelledby="ldap-title"
      description="Users with an account in the directory sign in to this dashboard with their directory username and password. The directory that Maven and Gradle clients use is set in the section Maven and Gradle clients below, and does not change this one."
    >
      <MdStatePanel
        v-if="!loaded"
        state="loading"
      />
      <MdStatePanel
        v-else-if="loadError"
        state="error"
        title="Could not load the LDAP settings"
        :description="loadError"
        @retry="load()"
      />

      <form
        v-else-if="form"
        id="ldap-form"
        class="flex flex-col gap-6"
        novalidate
        @submit.prevent="submit()"
      >
        <MdSection
          title="Connection"
          :level="3"
          heading-id="ldap-connection-title"
          aria-labelledby="ldap-connection-title"
        >
          <MdSwitch
            id="ldap-enabled"
            v-model="form.enabled"
          >
            Enabled: offer LDAP sign in
          </MdSwitch>
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
          <MdSwitch
            id="ldap-start-tls"
            v-model="form.startTls"
          >
            Upgrade the connection with StartTLS
          </MdSwitch>
          <MdTextField
            id="ldap-label"
            v-model="form.label"
            label="Label on the sign in form"
            type="text"
            :error="errorOf('label') !== ''"
            :error-text="errorOf('label')"
          />
        </MdSection>

        <MdSection
          title="Bind"
          :level="3"
          heading-id="ldap-bind-title"
          aria-labelledby="ldap-bind-title"
        >
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
        </MdSection>

        <MdSection
          title="User search"
          :level="3"
          heading-id="ldap-users-title"
          aria-labelledby="ldap-users-title"
        >
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
        </MdSection>

        <MdSection
          title="Groups"
          :level="3"
          heading-id="ldap-groups-title"
          aria-labelledby="ldap-groups-title"
        >
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
        </MdSection>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant pt-4">
          <p
            v-if="dirty"
            class="md-body-medium text-on-surface-variant"
          >
            You have unsaved changes.
          </p>
          <MdAlert
            v-if="saveError"
            tone="error"
          >
            {{ saveError }}
          </MdAlert>
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
      <MdCard
        v-if="form"
        variant="low"
        data-test="ldap-test"
      >
        <MdSection
          title="Test connection"
          :level="3"
          heading-id="ldap-test-title"
          aria-labelledby="ldap-test-title"
          :description="dirty
            ? 'Save your changes first. The test reads the saved directory.'
            : 'Connects with the saved settings. A user name and password also check that the user signs in.'"
        >
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
              <template v-if="testResult.userDn">
                User found: {{ testResult.userDn }}.
              </template>
              <template v-if="groupsOf(testResult.groups)">
                Groups: {{ groupsOf(testResult.groups) }}.
              </template>
            </p>
            <p v-else>
              The test failed: {{ testResult.error }}
            </p>
          </div>
        </MdSection>
      </MdCard>
    </MdSection>

    <MdSection
      title="Maven and Gradle clients"
      heading-id="ldap-clients-title"
      aria-labelledby="ldap-clients-title"
      description="Maven and Gradle clients sign in to the repositories with the directory below, through basic authentication. It is separate from the dashboard sign in above."
      class="border-t border-outline-variant pt-6"
      data-test="ldap-clients"
    >
      <AuthenticationGroupForm
        group="ldap"
        save-label="Save Maven and Gradle LDAP"
      />
    </MdSection>
  </section>
</template>
