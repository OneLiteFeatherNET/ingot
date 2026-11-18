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
import { computed, inject, onMounted, provide } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { entryLayout, type SettingsSchema } from '../settings/collections'
import { nestedUischemas } from '../settings/nestedLayouts'
import MdButton from '../md/MdButton.vue'

/*
 * One group of a settings domain, drawn by the same generic form as the Settings page and saved by the same store.
 * The Access pages show the LDAP directory of Maven clients, the brute force protection and the sign-in protection
 * with it, so the fields, the validation and the save are the ones Settings had. The domain is written as a whole,
 * as always, so a save of one tab also writes drafts of the other tabs of the same domain.
 *
 * Props:
 *   domain     the settings domain the group belongs to (default authentication)
 *   group      the property of the domain to draw: ldap or bruteForceProtection
 *   fields     several properties of the domain to draw instead of one group, for example the scalars of login
 *   saveLabel  the text of the save button, which names the tab or the domain it saves
 */
const props = defineProps({
  domain: {
    type: String,
    default: 'authentication'
  },
  group: {
    type: String,
    default: ''
  },
  fields: {
    type: Array as () => string[] | null,
    default: null
  },
  saveLabel: {
    type: String,
    default: 'Save'
  }
})

const DOMAIN = props.domain
const drawn = (): string[] => props.fields ?? [props.group]

const { store: sharedStore, renderers, configurationStyles } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
// The renderers read their class names from this injection, as they do under the Settings page.
provide('styles', configurationStyles)

const {
  domains,
  schemas,
  configurations,
  loadError,
  saving,
  saveError,
  conflict,
  invalidDomains,
  savedSettings
} = store

const formsConfiguration = {
  showUnfocusedDescription: true
}

const loaded = computed(() => domains.value.length > 0)

// Only the drawn properties are shown: the schema keeps the domain's shape, so the layout and the validation match Settings.
const form = computed(() => {
  const schema = schemas.value[DOMAIN]
  const properties = (schema?.properties ?? {}) as Record<string, SettingsSchema>
  const picked: Record<string, SettingsSchema> = Object.fromEntries(drawn().filter(name => properties[name]).map(name => [name, properties[name]]))
  if (Object.keys(picked).length === 0) return null
  const groupSchema = { ...schema, properties: picked }
  return { schema: groupSchema, layout: entryLayout(groupSchema, { exclude: [] }) }
})

// The drawn part is unsaved when its draft differs from what the server confirmed. The save writes the whole domain.
const dirty = computed(() => drawn().some(name => JSON.stringify(configurations.value[DOMAIN]?.[name])
  !== JSON.stringify(savedSettings.value[DOMAIN]?.[name])))

const onChange = (event: { data: Record<string, unknown> }) => {
  store.setDomainData(DOMAIN, event.data)
  store.validateDomain(DOMAIN)
}

const save = () => store.saveConfiguration([DOMAIN])

onMounted(() => {
  if (domains.value.length === 0) store.fetchConfiguration()
})
</script>

<template>
  <div
    class="flex flex-col gap-4"
    data-test="authentication-group"
    data-ingot-component="authentication-group-form"
  >
    <p
      v-if="!loaded && !loadError"
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
      <span class="md-body-medium text-error">Could not load the {{ DOMAIN }} settings: {{ loadError }}</span>
      <MdButton
        variant="text"
        @click="store.fetchConfiguration()"
      >
        Retry
      </MdButton>
    </div>
    <p
      v-else-if="!form || !configurations[DOMAIN]"
      class="md-body-medium text-on-surface-variant"
    >
      These settings are not available on this server.
    </p>
    <template v-else>
      <JsonForms
        :key="`${DOMAIN}:${drawn().join(',')}`"
        :config="formsConfiguration"
        :data="configurations[DOMAIN]"
        :schema="form.schema"
        :uischema="form.layout"
        :renderers="renderers"
        :uischemas="nestedUischemas"
        :ajv="store.configurationValidator"
        @change="onChange"
      />

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant pt-4">
        <p
          v-if="dirty"
          class="md-body-medium text-on-surface-variant"
        >
          You have unsaved changes.
        </p>
        <p
          v-if="conflict?.name === DOMAIN"
          role="alert"
          class="md-body-medium text-error"
        >
          The {{ DOMAIN }} settings changed elsewhere. Open Settings to review them before saving again.
        </p>
        <p
          v-else-if="saveError"
          role="alert"
          class="md-body-medium text-error"
        >
          {{ saveError }}
        </p>
        <span v-else />
        <MdButton
          :disabled="saving || invalidDomains.includes(DOMAIN)"
          @click="save()"
        >
          {{ saving ? 'Saving...' : saveLabel }}
        </MdButton>
      </div>
    </template>
  </div>
</template>
