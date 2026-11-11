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
import { computed, inject, nextTick, onMounted, provide, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import EntryDetail from '../settings/EntryDetail.vue'
import { collectionsOf, entryAddresses, idText, validateNewId } from '../settings/collections'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { tagsInUse } from '../../store/maven/repositories'
import {
  TYPE_LABELS,
  blankEntriesRemovedBy,
  blankEntriesNotice,
  effectiveTypeOf,
  isBlankEntry
} from '../../store/maven/repositoryTypes'
import TypeBadge from './TypeBadge.vue'
import WritePolicyField from './WritePolicyField.vue'
import DeclareTypeDialog from './DeclareTypeDialog.vue'
import { writePolicyChange, writePolicyOf } from './writePolicy'
import { MIXED_WARNING, groupsReferencing } from './repositoryReferences'
import { rowsOf } from './repositoryRows'

/*
 * One repository, at /settings/repositories/:id. The entry is edited in the existing entry editor, which keeps the
 * type switch, the generated form and the access card. This page adds three things: the type in use, with "Declare
 * type" for a repository that has none of its own; the write policy of a hosted repository; and the heading the focus
 * moves to when the page opens, so a new repository is announced.
 *
 * Every change is a draft change of the shared settings, written with the store's setEntry, so the save bar and the
 * leave guard see it. The address follows the id once the id is usable, as the collection editor does.
 */

const props = defineProps({
  // The id of the repository, from the address.
  id: {
    type: String,
    required: true
  }
})

const store = inject(configurationStoreKey, null) ?? useConfiguration().store
const { renderers, configurationStyles } = useConfiguration()
provide('styles', configurationStyles)

const router = useRouter()
const heading = ref(null)
const declareOpen = ref(false)
const typeHeading = ref(null)

const collection = computed(() => collectionsOf(store.schemas.value.maven).find(list => list.key === 'repositories') ?? null)
const entries = computed(() => store.configurations.value.maven?.repositories ?? [])
// The address of each entry, as the list links to it: the id, or a key for an entry without a usable id.
const addresses = computed(() => entryAddresses(entries.value, store.entryKeysOf('maven', 'repositories')))
// The position of the entry in the draft. It is found from the address, and kept while the id is being typed, so a
// rename does not lose the entry (the address follows the new id only once that id is usable).
const index = ref(-1)
watch(() => [props.id, entries.value.length], () => {
  index.value = addresses.value.indexOf(props.id)
}, { immediate: true })
const entry = computed(() => (index.value >= 0 ? entries.value[index.value] : null))
// The name of the entry in the heading and in the list; an entry without an id is named as the list names it.
const title = computed(() => idText(entry.value) || 'Unnamed repository')
const others = computed(() => entries.value.filter((_, position) => position !== index.value))
const otherIds = computed(() => others.value.map(idText))
const otherTags = computed(() => tagsInUse(others.value))
const serverError = computed(() => store.saveError.value ?? '')

const badge = computed(() => (entry.value ? rowsOf([entry.value])[0].type : null))
const effective = computed(() => (entry.value ? effectiveTypeOf(entry.value) : null))
const isHosted = computed(() => effective.value?.type === 'HOSTED')
const isMixed = computed(() => effective.value?.type === 'MIXED')
// A derived type can be declared, except Mixed: no declared type reproduces it.
const canDeclare = computed(() => entry.value !== null && !effective.value.declared && !isMixed.value)
const declaredLabel = computed(() => TYPE_LABELS[(effective.value?.type ?? '').toLowerCase()] ?? '')
const blankCount = computed(() => (entry.value && canDeclare.value ? blankEntriesRemovedBy(entry.value, effective.value.type) : 0))
const blankNotice = computed(() => blankEntriesNotice(blankCount.value))
const writeChoice = computed(() => writePolicyOf(entry.value ?? {}))
const offerReadOnly = computed(() => Boolean(collection.value?.itemSchema?.properties?.readOnly))
const groupsListingIt = computed(() => groupsReferencing(entries.value, idText(entry.value)))
const backHref = computed(() => router.resolve({ name: 'settings-repositories' }).href)

// The draft entry at this position is replaced; the page never writes a key of its own besides the ones named here.
const write = (next) => store.setEntry('maven', 'repositories', index.value, next)


const onWritePolicy = (choice) => {
  if (entry.value) write(writePolicyChange(entry.value, choice))
}

// The form reports the whole entry. An id that is usable moves the address, as the collection editor does.
const onChange = (next) => {
  const id = idText(next)
  // The id is checked against the other repositories before the write, while this entry is still left out of them.
  const problem = validateNewId(id, otherIds.value, collection.value?.noun ?? 'repository')
  write(next)
  if (id === props.id || problem !== '') return
  router.replace({ name: 'settings-repository', params: { id } })
}

const applyDeclare = async () => {
  const type = effective.value.type
  // Declaring writes the type and nothing else; empty mirrored entries are the one thing a typed repository drops.
  const next = { ...entry.value, type }
  if (Array.isArray(next.proxied)) next.proxied = next.proxied.filter(item => !isBlankEntry(item))
  write(next)
  await nextTick()
  typeHeading.value?.focus()
}

const declare = () => {
  if (blankCount.value > 0) {
    declareOpen.value = true
    return
  }
  applyDeclare()
}

// Deleting in the form takes the entry out of the draft, and the list says so once it opens.
const onDelete = () => {
  const name = title.value
  store.removeEntry('maven', 'repositories', index.value)
  router.push({ name: 'settings-repositories', query: { deleted: name } })
}

const back = () => router.push({ name: 'settings-repositories' })

// A new page takes the focus on its heading, so that a screen reader announces the repository it opens.
onMounted(() => {
  nextTick(() => heading.value?.focus())
})
</script>

<template>
  <div
    v-if="!collection"
    class="md-body-large text-on-surface-variant"
  >
    Loading configuration
  </div>

  <div
    v-else-if="entry === null"
    class="flex flex-col items-start gap-4"
  >
    <h2
      ref="heading"
      tabindex="-1"
      class="md-headline-small text-on-surface focus:outline-none"
    >
      Repository not found
    </h2>
    <p class="md-body-large text-on-surface-variant">
      There is no repository with the id "{{ id }}" in this configuration.
    </p>
    <MdButton
      variant="text"
      type="button"
      :href="backHref"
      @click.prevent="back"
    >
      Back to repositories
    </MdButton>
  </div>

  <div
    v-else
    class="flex flex-col gap-6"
  >
    <MdButton
      variant="text"
      type="button"
      class="self-start"
      :href="backHref"
      @click.prevent="back"
    >
      Back to repositories
    </MdButton>

    <header class="flex flex-col gap-4">
      <h2
        ref="heading"
        tabindex="-1"
        data-test="entry-title"
        class="min-w-0 break-all md-headline-small text-on-surface focus:outline-none"
      >
        {{ title }}
      </h2>

      <section
        aria-labelledby="repository-type-title"
        class="flex flex-col gap-3"
      >
        <h2
          id="repository-type-title"
          ref="typeHeading"
          tabindex="-1"
          class="md-title-medium text-on-surface focus:outline-none"
        >
          Repository type
        </h2>
        <div class="flex flex-wrap items-center gap-3">
          <TypeBadge :type="badge" />
          <MdButton
            v-if="canDeclare"
            variant="tonal"
            type="button"
            @click="declare"
          >
            Declare type
          </MdButton>
        </div>
        <p
          v-if="isMixed"
          data-test="mixed-warning"
          class="md-body-medium text-warning"
        >
          {{ MIXED_WARNING }}
        </p>
      </section>
    </header>

    <WritePolicyField
      v-if="isHosted"
      :model-value="writeChoice"
      :offer-read-only="offerReadOnly"
      @update:model-value="onWritePolicy"
    />

    <EntryDetail
      :id="id"
      :collection="collection"
      :entry="entry"
      :other-ids="otherIds"
      :other-tags="otherTags"
      :back-href="backHref"
      :renderers="renderers"
      :validator="store.configurationValidator"
      :show-access="true"
      :typed="true"
      :show-header="false"
      :omit="['redeployment', 'readOnly']"
      :references="groupsListingIt"
      :server-error="serverError"
      @change="onChange"
      @delete="onDelete"
      @back="back"
    />

    <DeclareTypeDialog
      v-model="declareOpen"
      :type-label="declaredLabel"
      :notice="blankNotice"
      @confirm="applyDeclare"
    />
  </div>
</template>
