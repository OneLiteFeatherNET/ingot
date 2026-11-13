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
import { computed, ref, useId } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { matchEntries } from '../../navigation/adminFilter'

/*
 * The Administration tree: the find field and the groups of links. It is drawn in the side column beside a page (see
 * SideNavLayout), as the index below 1240 pixels, and in the Sections sheet (see AdministrationFrame). It does not decide
 * its own width or position; the caller gives it its classes, and it has no scroll box of its own.
 *
 * Props:
 *   label        the accessible name of the navigation
 *   groups       [{ heading, links: [{ key, label, description, to }] }], where `to` is a router location
 *   current      the key of the open section, or undefined when no section is open
 *   replace      true when a link replaces the history entry instead of adding one
 *   unsaved      the keys of the entries whose domain has an unsaved draft (see unsavedTreeKeys in settingsHub.js)
 *   described    true where the tree is an index or a sheet: each row then shows its description under its label
 * Emits:
 *   select       a link was clicked; the router has taken the click (the sheet closes on it)
 */
const props = defineProps({
  label: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  replace: { type: Boolean, default: false },
  unsaved: { type: Array, default: () => [] },
  described: { type: Boolean, default: false }
})

const emit = defineEmits(['select'])

/*
 * The find field of the navigation. The text keeps the links that match it (adminFilter.js), the current link always
 * stays, and a polite live region says how many match. Escape clears the text; Enter opens the first match.
 */
const router = useRouter()
const fieldId = useId()
const query = ref('')
const filtered = computed(() => matchEntries(props.groups, query.value, props.current))
const shownGroups = computed(() => filtered.value.groups)
const announcement = computed(() => {
  const text = query.value.trim()
  if (text === '') return ''
  if (filtered.value.count === 0) return `No administration page matches "${text}"`
  return filtered.value.count === 1 ? '1 page matches' : `${filtered.value.count} pages match`
})
// Escape clears a text that is there and keeps the event to itself, so a sheet around the field does not also close.
const clearQuery = (event) => {
  if (query.value === '') return
  event.preventDefault()
  query.value = ''
}
const openFirstMatch = () => {
  if (query.value.trim() === '') return
  const first = matchEntries(props.groups, query.value).groups[0]?.links[0]
  if (first) router.push(first.to)
}

// A link whose domain has an unsaved draft says so: the mark is drawn, and the accessible name ends with it.
const isUnsaved = (link) => props.unsaved.includes(link.key)

// A row shows its description where the tree is an index or a sheet, and only when the entry has one.
const describes = (link) => props.described && Boolean(link.description)

// The name of a link is its label (and the unsaved note); the description is read after it, not as part of the name,
// so a row is found and announced by its label. A link without a description and without a draft has no name of its own.
const nameOf = (link) => {
  if (!describes(link) && !isUnsaved(link)) return undefined
  return isUnsaved(link) ? `${link.label}, unsaved changes` : link.label
}
const descriptionId = (link) => (describes(link) ? `${fieldId}-${link.key.replace(/[^a-zA-Z0-9_-]+/g, '-')}` : undefined)

// The id of the visible heading of a group. Each group list is labelled by it, so a screen reader names the group on entry.
const headingId = (group) => `admin-group-${group.heading.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`

const linkClasses = (active, described) => [
  'md-state-layer md-focus-ring relative flex min-h-10 items-center rounded-corner-full px-4 md-label-large no-underline',
  described ? 'py-2' : '',
  'transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
  active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant hover:text-on-surface'
]

const choose = (event, navigate, link) => {
  navigate(event)
  emit('select', link)
}
</script>

<template>
  <nav
    :aria-label="label"
    data-ingot-component="side-nav-tree"
    class="flex flex-col gap-4"
  >
    <div
      role="search"
      class="flex flex-col gap-1 px-4"
    >
      <label
        :for="fieldId"
        class="md-label-medium text-on-surface-variant"
      >Find an administration page</label>
      <input
        :id="fieldId"
        v-model="query"
        type="search"
        autocomplete="off"
        class="min-w-0 rounded-corner-small border border-outline bg-surface px-3 py-2 md-body-medium text-on-surface [&::-webkit-search-cancel-button]:hidden"
        @keydown.escape="clearQuery"
        @keydown.enter.prevent="openFirstMatch"
      >
      <p
        aria-live="polite"
        class="md-label-medium text-on-surface-variant"
      >
        {{ announcement }}
      </p>
    </div>
    <div
      v-for="group in shownGroups"
      :key="group.heading ?? group.label"
      class="flex flex-col gap-1"
    >
      <p
        v-if="group.heading"
        :id="headingId(group)"
        class="px-4 pb-1 md-label-medium text-on-surface-variant"
      >
        {{ group.heading }}
      </p>
      <ul
        :aria-labelledby="group.heading ? headingId(group) : undefined"
        :aria-label="group.heading ? undefined : group.label"
        class="flex flex-col gap-1"
      >
        <li
          v-for="link in group.links"
          :key="link.key"
        >
          <RouterLink
            v-slot="{ href, navigate }"
            :to="link.to"
            :replace="replace"
            custom
          >
            <a
              :href="href"
              :aria-current="link.key === current ? 'page' : undefined"
              :aria-label="nameOf(link)"
              :aria-describedby="descriptionId(link)"
              :class="linkClasses(link.key === current, describes(link))"
              @click="choose($event, navigate, link)"
            >
              <span class="flex min-w-0 flex-1 flex-col">
                <span>{{ link.label }}</span>
                <span
                  v-if="describes(link)"
                  :id="descriptionId(link)"
                  class="line-clamp-2 md-body-medium text-on-surface-variant"
                >{{ link.description }}</span>
              </span>
              <span
                v-if="isUnsaved(link)"
                class="ml-auto inline-flex items-center gap-1 md-label-medium text-primary"
              >
                <svg
                  class="size-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <circle
                    cx="12"
                    cy="12"
                    r="4"
                    fill="currentColor"
                  />
                </svg>
                Unsaved
              </span>
            </a>
          </RouterLink>
        </li>
      </ul>
    </div>
  </nav>
</template>
