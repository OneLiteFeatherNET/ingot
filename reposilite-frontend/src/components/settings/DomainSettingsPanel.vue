<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup lang="ts">
import { computed, inject, onMounted, provide, ref, watch } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { useSession } from '../../store/session'
import { ADMIN_NODES } from '../../store/nodeAccess'
import { collectionsOf } from './collections'
import { domainFormOf } from './domainForm'
import { diffSettings, revertChange } from './settingsDiff'
import { settingsByDomain } from './settingsExport'
import UnsavedChangesBar from './UnsavedChangesBar.vue'
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import ConflictDialog from './ConflictDialog.vue'

/*
 * The fields of one settings domain, edited on a page that is not the settings page. The form is the one the settings page
 * generates for every domain (domainFormOf and the same renderers), and the save flow is the store's: the bar saves, discards
 * and reviews the draft, and a save refused as a conflict opens the conflict dialog. Nothing is saved from the form itself.
 *
 * Editing needs the settings node. A visitor without it sees the fields' heading and a line that says so.
 */
const props = defineProps<{
  domain: string
  heading: string
}>()

const { store: sharedStore, renderers, configurationStyles } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
// The renderers take their class names from this key, as they do on the settings page.
provide('styles', configurationStyles)

const session = useSession()
const canEdit = computed(() => (typeof session.can === 'function' ? session.can(ADMIN_NODES.settings) : true))

const form = computed(() => {
  const schema = store.schemas.value[props.domain]
  return domainFormOf(schema, collectionsOf(schema).map(collection => collection.key))
})

const formsConfiguration = {
  showUnfocusedDescription: true
}

const onChange = (event: { data: Record<string, unknown> }) => {
  store.setDomainData(props.domain, event.data)
  store.validateDomain(props.domain)
}

// The configuration is fetched once, when the panel opens and nothing has been loaded yet (the settings page does the same).
onMounted(() => {
  if (canEdit.value && store.domains.value.length === 0) store.fetchConfiguration()
})

// The changes of this domain only. The review lists the same changes the bar counts, as the settings page does.
const changes = computed(() => diffSettings(
  settingsByDomain(store.domains.value, store.savedSettings.value),
  settingsByDomain(store.domains.value, store.configurations.value),
  store.schemas.value
).filter(change => change.domain === props.domain))

const reviewOpen = ref(false)
watch(() => changes.value.length, (count) => {
  if (count === 0) reviewOpen.value = false
})

const revert = (change: Parameters<typeof revertChange>[2]) => {
  const reverted = revertChange(store.configurations.value[props.domain], store.savedSettings.value[props.domain], change)
  if (!reverted) return
  store.setDomainData(props.domain, reverted)
  store.validateDomain(props.domain)
}

const saveFromReview = async () => {
  const saved = await store.saveConfiguration()
  if (saved || store.conflict.value) reviewOpen.value = false
}

const discardAll = () => {
  store.discardChanges()
  reviewOpen.value = false
}
</script>

<template>
  <section
    :aria-labelledby="`${domain}-settings-title`"
    :data-test="`${domain}-settings`"
    data-ingot-component="domain-settings-panel"
    class="flex flex-col gap-3"
  >
    <h2
      :id="`${domain}-settings-title`"
      class="md-title-large text-on-surface"
    >
      {{ heading }}
    </h2>

    <p
      v-if="!canEdit"
      class="md-body-large text-on-surface-variant"
    >
      Changing these settings needs the settings permission.
    </p>
    <p
      v-else-if="store.loadError.value"
      role="alert"
      class="md-body-medium text-error"
    >
      {{ store.loadError.value }}
    </p>
    <JsonForms
      v-else-if="form && store.configurations.value[domain]"
      :config="formsConfiguration"
      :data="store.configurations.value[domain]"
      :schema="form.schema"
      :uischema="form.layout"
      :renderers="renderers"
      :ajv="store.configurationValidator"
      @change="onChange"
    />
    <p
      v-else
      class="md-body-large text-on-surface-variant"
    >
      Loading configuration
    </p>

    <div
      v-if="store.hasUnsavedChanges.value"
      class="sticky bottom-0"
    >
      <UnsavedChangesBar
        :count="changes.length"
        :invalid="store.invalidDomains.value.length > 0"
        :saving="store.saving.value"
        @save="store.saveConfiguration()"
        @discard="store.discardChanges()"
        @review="reviewOpen = true"
      />
    </div>

    <SettingsReviewSheet
      :open="reviewOpen"
      :changes="changes"
      :invalid="store.invalidDomains.value.length > 0"
      :saving="store.saving.value"
      @update:open="reviewOpen = false"
      @revert="revert"
      @discard="discardAll"
      @save="saveFromReview"
    />

    <ConflictDialog
      v-if="store.conflict.value"
      :conflict="store.conflict.value"
      @reload="store.reloadConflict()"
      @overwrite="store.overwriteConflict()"
      @dismiss="store.dismissConflict()"
    />
  </section>
</template>
