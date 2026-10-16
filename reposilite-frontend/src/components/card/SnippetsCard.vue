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

<script setup>
import { onBeforeUnmount, ref, watch, watchEffect } from 'vue'
import { useSession } from '../../store/session'
import { copyToClipboard, selectText } from './copySnippet'
import useRepository from '../../store/maven/repository'
import useMetadata from '../../store/maven/metadata'
import MdCard from '../md/MdCard.vue'
import MdIconButton from '../md/MdIconButton.vue'
import BrowserIcon from '../browser/BrowserIcon.vue'
import CardMenu from './CardMenu.vue'
import RepositorySnippet from "./RepositorySnippet.vue"
import ArtifactSnippet from "./ArtifactSnippet.vue"

const props = defineProps({
  qualifier: {
    type: Object,
    required: true
  }
})

const title = ref('')
const configurations = [
  { name: 'Maven', lang: 'xml' },
  { name: 'Gradle Kotlin', lang: 'kotlin' },
  { name: 'Gradle Groovy', lang: 'groovy' },
  { name: 'SBT', lang: 'scala' }
]
const data = ref({})
const loading = ref(false)
const { createRepositories } = useRepository()
const { parseMetadata } = useMetadata()
const { client } = useSession()

const displayRepository = () => {
  title.value = 'Repository details'
  data.value = createRepositories(props.qualifier)
}

const displayArtifact = (metadataSource, version) => {
  title.value = 'Artifact details'
  const { groupId, artifactId, versions } = parseMetadata(metadataSource)
  const latestVersion = versions[version ? versions.indexOf(version) : versions.length - 1]
  data.value = { type: 'artifact', groupId, artifactId, version: latestVersion }
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

  loading.value = true

  client.value.maven.content(`${qualifier}/maven-metadata.xml`)
    .then(response => {
      if (isStale()) return
      displayArtifact(response.data)
      loading.value = false
    })
    .catch(() => {
      client.value.maven.content(`${qualifier.substring(0, qualifier.indexOf(elements[elements.length-1])-1)}/maven-metadata.xml`)
        .then(response => {
          if (isStale()) return
          displayArtifact(response.data, elements[elements.length-1])
          loading.value = false
        })
        .catch(error => {
          if (isStale()) return
          if (error.response?.status !== 404 && error.response?.status !== 403) {
            console.log(error)
          }
          displayRepository()
          loading.value = false
        })
    })
})

const selectedTab = ref()
const transitionName = ref('slide-right')

watch(selectedTab, (to, from) => {
  const toIndex = configurations.findIndex(entry => entry.name === to)
  const fromIndex = configurations.findIndex(entry => entry.name === from)
  transitionName.value = toIndex - fromIndex < 0 ? 'slide-left' : 'slide-right'
})

const snippetRef = ref()
const codeBlock = ref()

// '' (nothing yet), 'copied' or 'selected'. Shown as a status line next to the copy action.
const copyState = ref('')
let resetCopyState

const copy = async () => {
  if (copyState.value === 'copied') return

  const snippet = snippetRef.value?.[0]?.content?.trim() ?? ''
  if (await copyToClipboard(snippet, navigator.clipboard)) {
    copyState.value = 'copied'
    clearTimeout(resetCopyState)
    resetCopyState = setTimeout(() => {
      copyState.value = ''
    }, 2000)
    return
  }

  // No clipboard here: select the snippet so the keyboard shortcut finishes the job.
  selectText(codeBlock.value)
  copyState.value = 'selected'
}

onBeforeUnmount(() => clearTimeout(resetCopyState))

const selectTab = (tab) =>
  selectedTab.value = tab
</script>

<template>
  <!-- Outlined on the surface: the file listing beside it is the tonal block, this card the quiet frame. -->
  <MdCard
    variant="outlined"
    class="w-full min-w-0"
  >
    <div class="px-2 pb-2">
      <div class="flex flex-row justify-between">
        <h1 class="flex w-full items-center md-title-large">
          <span
            v-if="loading"
            class="h-5 w-36 rounded bg-surface-container-high skeleton-bars"
          />
          <template v-else>
            {{ title }}
          </template>
        </h1>
      </div>

      <CardMenu
        :configurations="configurations"
        @select-tab="selectTab"
      />

      <div class="mt-5">
        <transition
          :name="transitionName"
          mode="out-in"
        >
          <div
            :key="selectedTab"
          >
            <!--
              The copy action has a row of its own above the code, so it never sits over a line of
              the snippet, and the code block below scrolls sideways without moving it.
            -->
            <div
              class="rounded-corner-medium bg-surface-container-highest text-on-surface"
            >
              <div
                v-if="!loading"
                class="flex justify-end px-2 pt-1"
              >
                <MdIconButton
                  aria-label="Copy snippet"
                  @click="copy"
                >
                  <BrowserIcon :name="copyState === 'copied' ? 'check' : 'copy'" />
                </MdIconButton>
              </div>
              <!-- Preformatted: lines keep their indentation and are never broken, so long ones scroll sideways. -->
              <div
                ref="codeBlock"
                class="card-editor max-w-full overflow-x-auto whitespace-pre break-normal font-mono md-body-small min-h-29 px-4 pb-3 pt-1"
              >
                <div
                  v-if="loading"
                  class="skeleton-bars space-y-2.5 pt-1"
                >
                  <div
                    class="h-3 rounded bg-surface-container-high"
                    style="width: 80%"
                  />
                  <div
                    class="h-3 rounded bg-surface-container-high"
                    style="width: 55%"
                  />
                  <div
                    class="h-3 rounded bg-surface-container-high"
                    style="width: 68%"
                  />
                  <div
                    class="h-3 rounded bg-surface-container-high"
                    style="width: 40%"
                  />
                </div>
                <template v-else>
                  <template
                    v-for="entry in configurations"
                    :key="entry.name"
                  >
                    <template v-if="entry.name === selectedTab">
                      <RepositorySnippet
                        v-if="data.type === 'repository'"
                        ref="snippetRef"
                        :configuration="entry"
                        :data="data"
                      />
                      <ArtifactSnippet
                        v-else-if="data.type === 'artifact'"
                        ref="snippetRef"
                        :configuration="entry"
                        :data="data"
                      />
                    </template>
                  </template>
                </template>
              </div>
            </div>
            <!-- Below the snippet, so a status line never sits over a line of it. -->
            <p
              v-if="copyState === 'copied'"
              role="status"
              class="mt-2 md-body-small text-success"
            >
              Copied
            </p>
            <p
              v-if="copyState === 'selected'"
              role="status"
              class="mt-2 md-body-small text-on-surface-variant"
            >
              Copying is not available here. The snippet is selected: press Ctrl+C (Cmd+C on macOS) to copy it.
            </p>
          </div>
        </transition>
      </div>
    </div>
  </MdCard>
</template>

<style>
.slide-right-enter-active,
.slide-right-leave-active,
.slide-left-enter-active,
.slide-left-leave-active {
  transition: opacity .1s ease, transform .1s ease;
}

.slide-right-leave-to,
.slide-left-enter-from {
  opacity: 0;
  transform: translateX(60px);
}

.slide-right-enter-from,
.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-60px);
}

::-webkit-scrollbar {
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background-color: var(--md-sys-color-outline-variant);
  border-radius: 20px;
  border: transparent;
  margin-top: 10px;
}
</style>
