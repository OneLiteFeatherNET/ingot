<!--
  - Copyright (c) 2023 dzikoysk
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
import { ref, useId, watch, watchEffect, type PropType } from 'vue'
import { useSession } from '../../store/session'
import { readDetailsCollapsed, writeDetailsCollapsed } from './detailsCollapsed'
import useRepository from '../../store/maven/repository'
import useMetadata from '../../store/maven/metadata'
import MdCode from '../md/MdCode.vue'
import MdCodeBlock from '../md/MdCodeBlock.vue'
import MdTransition from '../md/MdTransition.vue'
import DownIcon from '../icons/DownIcon.vue'
import CardMenu from './CardMenu.vue'
import RepositorySnippet from "./RepositorySnippet.vue"
import ArtifactSnippet from "./ArtifactSnippet.vue"
import type { SnippetConfiguration, SnippetData } from './snippetTypes'

const props = defineProps({
  qualifier: {
    type: Object as PropType<{ path: string }>,
    required: true
  },
  // The names of the files the browser lists for the path. Null while that listing is still loading, so the card
  // waits for it. Left out, the card asks the remote for the metadata of both levels, as it did before the listing.
  listing: {
    type: Array as PropType<string[] | null>,
    default: undefined
  }
})

const emit = defineEmits<{
  'update:collapsed': [collapsed: boolean]
}>()

// Folded away or open. Remembered per viewer, and the page reads the same choice to lay out the listing.
const collapsed = ref(readDetailsCollapsed())
const bodyId = useId()

const toggleCollapsed = () => {
  collapsed.value = !collapsed.value
  writeDetailsCollapsed(collapsed.value)
  emit('update:collapsed', collapsed.value)
}

const title = ref('')
// The full name of the card for assistive technology: the visible title is the short part of it.
const label = ref('')
// The repository the details are for, shown under the heading. Empty on the root page and for an artifact.
const subtitle = ref('')
const rootPage = ref(false)
const configurations: SnippetConfiguration[] = [
  { name: 'Maven', lang: 'xml' },
  { name: 'Gradle Kotlin', lang: 'kotlin' },
  { name: 'Gradle Groovy', lang: 'groovy' },
  { name: 'SBT', lang: 'scala' }
]
const data = ref<SnippetData>({})
const loading = ref(false)
const { createRepositories } = useRepository()
const { parseMetadata } = useMetadata()
const { client } = useSession()

// The root page has no repository yet, so its heading says what to replace; a repository path names its repository.
const displayRepository = () => {
  const repository = props.qualifier.path.split('/')[0]
  rootPage.value = repository === ''
  title.value = rootPage.value ? 'Use a repository' : 'Repository details'
  label.value = rootPage.value ? 'Use a repository' : `Repository details: ${repository}`
  subtitle.value = rootPage.value ? '' : repository
  data.value = { ...createRepositories(props.qualifier), type: 'repository' }
}

const displayArtifact = (metadataSource: string, version?: string) => {
  title.value = 'Artifact details'
  label.value = 'Artifact details'
  subtitle.value = ''
  const { metadata, groupId, artifactId, versions } = parseMetadata(metadataSource)
  // Version-level metadata (a snapshot's own directory) names its single version at the top and lists no versions.
  const ownVersion = [...metadata.documentElement.children].find(node => node.tagName === 'version')?.textContent?.trim()
  const latestVersion = ownVersion || versions[version ? versions.indexOf(version) : versions.length - 1]
  data.value = { type: 'artifact', groupId, artifactId, version: latestVersion }
}

// Maven writes a version directory's files with the version in their name: the .pom and .module are the ones to look for.
const isVersionFile = (name: string) => name.endsWith('.pom') || name.endsWith('.module')

// A probe answering one of these means the file is not there, which is expected on a remote and is not logged.
const EXPECTED_ABSENT = [403, 404, 406]
const logUnlessExpected = (error: { response?: { status?: number } }) => {
  if (!EXPECTED_ABSENT.includes(error.response?.status ?? 0)) {
    console.log(error)
  }
}

watchEffect(() => {
  // 1. Check if gave enough length to be an artifact
  //   1.1 check if gav is an artifact (fetch and process metadata file)
  // 2. If not display repository credentials if at least GAV has one element
  // 3. If GAV is empty display generic snippet to repository like domain.com/{repository}
  const qualifier = props.qualifier.path
  const elements = qualifier.split('/')

  const isStale = () => qualifier !== props.qualifier.path

  if (elements.length === 1 && elements[0] == '') {
    loading.value = false
    displayRepository()
    return
  }

  const listing = props.listing

  // The listing is not in yet: the card waits for it instead of asking the remote.
  if (listing === null) {
    loading.value = true
    return
  }

  loading.value = true

  const finishWithRepository = () => {
    displayRepository()
    loading.value = false
  }

  // The metadata of the parent directory, which names the versions of the artifact the version directory belongs to.
  const probeParent = () => {
    const last = elements[elements.length - 1]
    client.value.maven.content(`${qualifier.substring(0, qualifier.indexOf(last) - 1)}/maven-metadata.xml`)
      .then(response => {
        if (isStale()) return
        displayArtifact(response.data, last)
        loading.value = false
      })
      .catch(error => {
        if (isStale()) return
        logUnlessExpected(error)
        finishWithRepository()
      })
  }

  // The metadata of the directory itself, which an artifact directory has. onMissing decides what a miss means.
  const probeOwn = (onMissing: () => void) => {
    client.value.maven.content(`${qualifier}/maven-metadata.xml`)
      .then(response => {
        if (isStale()) return
        displayArtifact(response.data)
        loading.value = false
      })
      .catch(() => {
        if (isStale()) return
        onMissing()
      })
  }

  // Without a listing the card asks both levels, as it always did.
  if (listing === undefined) {
    probeOwn(probeParent)
    return
  }

  const listsOwnMetadata = listing.includes('maven-metadata.xml')
  const listsVersionFiles = listing.some(isVersionFile)

  if (listsOwnMetadata) {
    probeOwn(() => listsVersionFiles ? probeParent() : finishWithRepository())
  } else if (listsVersionFiles) {
    probeParent()
  } else {
    finishWithRepository()
  }
})

const selectedTab = ref<string>()
// The snippet moves along the shared x axis: forward to a tab further right, back to one further left.
const transitionDirection = ref<1 | -1>(1)

watch(selectedTab, (to, from) => {
  const toIndex = configurations.findIndex(entry => entry.name === to)
  const fromIndex = configurations.findIndex(entry => entry.name === from)
  transitionDirection.value = toIndex - fromIndex < 0 ? -1 : 1
})

const selectTab = (tab: string) =>
  selectedTab.value = tab
</script>

<template>
  <!--
    A sidebar on the page surface, like the listing beside it: no frame of its own, so its heading and the listing's
    heading share one top line and its edges are the edges of the column.
  -->
  <div
    class="w-full min-w-0 text-on-surface"
    data-ingot-component="snippets-card"
  >
    <div>
      <div class="flex min-h-12 flex-row items-center justify-between">
        <h2 class="flex w-full min-w-0 items-center md-title-large">
          <button
            type="button"
            class="md-state-layer md-focus-ring -mx-2 inline-flex min-h-12 min-w-0 max-w-full items-center gap-1 rounded-corner-small px-2 text-left text-on-surface"
            :aria-expanded="collapsed ? 'false' : 'true'"
            :aria-controls="bodyId"
            :aria-label="loading ? undefined : label"
            @click="toggleCollapsed"
          >
            <span
              v-if="loading"
              class="h-5 w-36 rounded-corner-extra-small bg-surface-container-high skeleton-bars"
            />
            <template v-else>
              {{ title }}
            </template>
            <!-- Points down while folded, up while open. The button's name and state are already announced. -->
            <span
              aria-hidden="true"
              class="inline-flex shrink-0 transition-transform duration-(--md-sys-motion-duration-short3) motion-reduce:transition-none"
              :class="collapsed ? '' : 'rotate-180'"
            >
              <DownIcon />
            </span>
          </button>
        </h2>
      </div>
      <p
        v-if="subtitle && !loading"
        data-test="details-subtitle"
        class="-mt-1 break-words md-body-medium text-on-surface-variant"
      >
        {{ subtitle }}
      </p>

      <div
        v-show="!collapsed"
        :id="bodyId"
        class="mt-4"
      >
        <p
          v-if="rootPage && !loading"
          class="mb-4 md-body-medium text-on-surface-variant"
        >
          Replace <MdCode>&lt;repository&gt;</MdCode> with the id of a repository from the list.
          The id in the snippet is a label in your build file, so the instance id works too.
        </p>
        <CardMenu
          :configurations="configurations"
          @select-tab="selectTab"
        />

        <div class="mt-5">
          <MdTransition
            pattern="shared-x"
            :direction="transitionDirection"
          >
            <div
              :key="selectedTab"
            >
              <MdCodeBlock
                v-if="loading"
                label="Loading snippet"
                class="min-h-29"
              >
                <span class="skeleton-bars block space-y-2.5 pt-1">
                  <span class="block h-3 w-4/5 rounded-corner-extra-small bg-surface-container-high" />
                  <span class="block h-3 w-1/2 rounded-corner-extra-small bg-surface-container-high" />
                  <span class="block h-3 w-2/3 rounded-corner-extra-small bg-surface-container-high" />
                  <span class="block h-3 w-2/5 rounded-corner-extra-small bg-surface-container-high" />
                </span>
              </MdCodeBlock>
              <template v-else>
                <template
                  v-for="entry in configurations"
                  :key="entry.name"
                >
                  <template v-if="entry.name === selectedTab">
                    <RepositorySnippet
                      v-if="data.type === 'repository'"
                      :configuration="entry"
                      :data="data"
                    />
                    <ArtifactSnippet
                      v-else-if="data.type === 'artifact'"
                      :configuration="entry"
                      :data="data"
                    />
                  </template>
                </template>
              </template>
            </div>
          </MdTransition>
        </div>
      </div>
    </div>
  </div>
</template>
