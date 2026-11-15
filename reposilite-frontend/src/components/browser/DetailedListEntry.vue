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
import { computed } from 'vue'
import { useSession } from '../../store/session'
import usePlaceholders from '../../store/placeholders'
import { createURL } from '../../store/client'
import { property } from '../../helpers/vue-extensions'
import MdIconButton from '../md/MdIconButton.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import BrowserIcon from './BrowserIcon.vue'
import { describeEntry, describeSize } from './entryMeta'
import { actionCountOf, actionSlotClass, canDeleteAt, rowActionsOf } from './rowActions'

/*
 * One row of the listing. The row is a list item; its name is the link, stretched over the
 * whole row, and the actions sit above that stretch as buttons of their own. A link inside
 * a link, or a button inside a link, is invalid, so the actions never live in the link.
 */
const props = defineProps({
  file: property(Object, true),
  qualifier: property(Object, true),
  url: property(String, false),
  // A router location, for a directory: the row is a link into the browser.
  to: property(Object, false),
  openDeleteEntryModal: property(Function, true),
  compactMode: property(Boolean, true),
  signedIn: property(Boolean, false),
  busy: property(Boolean, false),
  // The number of actions the widest row of the listing offers. The list passes it so that every row
  // reserves the same slot; without it the row sizes its slot by its own actions.
  actionSlots: property(Number, false)
})

const emit = defineEmits(['download'])

const { hasPermissionTo } = useSession()
const { javadocSuffixes } = usePlaceholders()

const isDirectory = computed(() => props.file.type === 'DIRECTORY')
const canDelete = computed(() => canDeleteAt(props.qualifier.path, hasPermissionTo))
const actions = computed(() => rowActionsOf({
  file: props.file,
  qualifier: props.qualifier,
  canDelete: canDelete.value,
  javadocSuffixes
}))
const canViewContent = computed(() => actions.value.view)
const slotClass = computed(() => actionSlotClass(
  props.actionSlots ?? actionCountOf({ file: props.file, qualifier: props.qualifier, canDelete: canDelete.value, javadocSuffixes })
))
const meta = computed(() => describeEntry(props.file))
const size = computed(() => describeSize(props.file))

// Compact rows put name and size on one line; default rows stack the size and date under the name.
const textClass = computed(() =>
  props.compactMode
    ? 'flex min-w-0 flex-1 items-center gap-2 medium:gap-4'
    : 'flex min-w-0 flex-1 flex-col'
)

const openUrl = (url) =>
  window.open(url, '_blank')

const getJavaDocsUrl = () => {
  const qualifier = props.qualifier.path
  const elements = qualifier.split('/')

  if (elements.length < 2 || elements[1] === '') {
    return null
  }

  return createURL(`/javadoc/${qualifier}`)
}

const canOpenJavadocs = computed(() => actions.value.javadoc)

// Anonymous visitors follow the link and the browser saves the file. A signed-in request has
// to carry the token, which a link cannot send, so the page fetches the file itself.
const onFileClick = (event) => {
  if (!props.signedIn) {
    return
  }

  event.preventDefault()
  emit('download', props.file)
}
</script>

<template>
  <li
    class="md-state-layer group relative flex items-center rounded-corner-medium transition-colors"
    :class="compactMode ? 'min-h-14 gap-2 medium:gap-4 px-4' : 'min-h-18 gap-4 px-4 py-2'"
    :aria-busy="busy ? 'true' : undefined"
    data-ingot-component="detailed-list-entry"
  >
    <router-link
      v-if="isDirectory"
      :to="to"
      class="md-focus-ring flex min-h-12 min-w-0 flex-1 items-center rounded-corner-medium text-on-surface after:absolute after:inset-0 after:rounded-corner-medium"
      :class="compactMode ? 'gap-2 medium:gap-4' : 'gap-4'"
    >
      <BrowserIcon
        name="folder"
        class="text-on-surface-variant"
      />
      <span :class="textClass">
        <span
          class="md-body-large truncate"
          :class="compactMode ? 'min-w-0 flex-1' : ''"
        >{{ file.name }}</span>
        <span
          v-if="compactMode && size"
          class="shrink-0 md-label-small text-on-surface-variant"
        >{{ size }}</span>
        <span
          v-if="!compactMode && meta"
          class="md-body-medium text-on-surface-variant"
        >{{ meta }}</span>
      </span>
    </router-link>

    <a
      v-else
      :href="url"
      target="_blank"
      class="md-focus-ring flex min-h-12 min-w-0 flex-1 items-center rounded-corner-medium text-on-surface after:absolute after:inset-0 after:rounded-corner-medium"
      :class="compactMode ? 'gap-2 medium:gap-4' : 'gap-4'"
      @click="onFileClick"
    >
      <BrowserIcon
        name="file"
        class="text-on-surface-variant"
      />
      <span :class="textClass">
        <span
          class="md-body-large truncate"
          :class="compactMode ? 'min-w-0 flex-1' : ''"
        >{{ file.name }}</span>
        <span
          v-if="compactMode && size"
          class="shrink-0 md-label-small text-on-surface-variant"
        >{{ size }}</span>
        <span
          v-if="!compactMode && meta"
          class="md-body-medium text-on-surface-variant"
        >{{ meta }}</span>
      </span>
      <MdLinearProgress
        v-if="busy"
        indeterminate
        :label="`Downloading ${file.name}`"
        :class="compactMode ? 'absolute inset-x-3 bottom-0' : 'absolute inset-x-4 bottom-0'"
      />
    </a>

    <!--
      Hidden until the row is hovered, focused or touched, but never display: none. A keyboard
      user tabs into these buttons, and a touch screen has no hover to reveal them with.
      The slot is as wide as the widest row of the listing needs, on every row, so the size text
      right-aligns in one column. A listing without any action reserves no slot at all.
    -->
    <div
      data-row-actions
      class="relative z-10 flex shrink-0 items-center justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 [@media(pointer:coarse)]:opacity-100"
      :class="slotClass"
    >
      <MdIconButton
        v-if="canViewContent"
        :aria-label="`View ${file.name} in a new tab`"
        @click.stop="openUrl(url)"
      >
        <BrowserIcon name="visibility" />
      </MdIconButton>
      <MdIconButton
        v-if="!isDirectory && canOpenJavadocs"
        :aria-label="`Open javadoc for ${file.name} in a new tab`"
        @click.stop="openUrl(getJavaDocsUrl())"
      >
        <BrowserIcon name="code" />
      </MdIconButton>
      <MdIconButton
        v-if="canDelete"
        :aria-label="`Delete ${file.name}`"
        @click.stop="openDeleteEntryModal(file)"
      >
        <BrowserIcon name="delete" />
      </MdIconButton>
    </div>
  </li>
</template>
