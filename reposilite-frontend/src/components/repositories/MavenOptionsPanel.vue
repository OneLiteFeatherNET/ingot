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
import { computed, inject, provide } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { collectionsOf } from '../settings/collections'
import { domainFormOf } from '../settings/domainForm'

/*
 * The domain-level fields of the maven domain, shown on the Repositories page below the table. The Maven page used to
 * show them, and the page is gone, so the one option of the domain is edited here. The form is generated from the served
 * schema, the same way the settings page generates the fields of every other domain. Nothing is shown when the schema
 * declares no such field.
 *
 * The form is given the whole domain object, so a change writes the domain back with its repositories. Nothing is saved
 * here: the save bar of the page saves the draft with the repositories.
 */
const { store: sharedStore, renderers, configurationStyles } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
// The renderers take their class names from this key, as they do on the settings page that holds this section.
provide('styles', configurationStyles)

const form = computed(() => {
  const schema = store.schemas.value.maven
  return domainFormOf(schema, collectionsOf(schema).map(collection => collection.key))
})

const formsConfiguration = {
  showUnfocusedDescription: true
}

const onChange = (event) => {
  store.setDomainData('maven', event.data)
  store.validateDomain('maven')
}
</script>

<template>
  <section
    v-if="form && store.configurations.value.maven"
    aria-labelledby="maven-options-title"
    data-test="maven-options"
    data-ingot-component="maven-options-panel"
    class="flex flex-col gap-3"
  >
    <h2
      id="maven-options-title"
      class="md-title-large text-on-surface"
    >
      Maven options
    </h2>
    <JsonForms
      :config="formsConfiguration"
      :data="store.configurations.value.maven"
      :schema="form.schema"
      :uischema="form.layout"
      :renderers="renderers"
      :ajv="store.configurationValidator"
      @change="onChange"
    />
  </section>
</template>
