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

<script>
import { computed, ref, watch } from 'vue'
import { COLOUR_PROPERTIES, isColour } from '../../theme/settings'

const ROLES = [
  { key: 'primary', label: 'Primary' },
  { key: 'secondary', label: 'Secondary' },
  { key: 'tertiary', label: 'Tertiary' },
  { key: 'error', label: 'Error' },
  { key: 'surface', label: 'Surface' }
]

/*
 * The colours of the form that the generator may use. A value that is not #RRGGBB is left out here,
 * because the field already reports it, and the generator would only warn about it on every key press.
 */
const usableColours = (appearance) => ({
  ...appearance,
  ...Object.fromEntries(COLOUR_PROPERTIES.map(key => [key, isColour(appearance[key]) ? appearance[key] : '']))
})

/*
 * A strip of the colours the generated scheme gives for the seed, computed from the current form
 * values. The colour generator is loaded when the preview is first shown, like the dashboard does.
 */
export default {
  name: 'AppearancePreview',
  props: {
    appearance: {
      type: Object,
      default: () => ({})
    }
  },
  setup(props) {
    // Undefined until the first answer, then the five roles, or null when no seed is set.
    const roles = ref(undefined)
    const failed = ref(false)
    let latest = 0

    const refresh = async () => {
      const request = ++latest
      try {
        const { previewRoles } = await import('../../theme/runtime')
        if (request !== latest) return
        roles.value = previewRoles(usableColours(props.appearance ?? {}))
        failed.value = false
      } catch (error) {
        if (request !== latest) return
        console.error('Could not load the appearance preview:', error)
        failed.value = true
      }
    }

    watch(() => props.appearance, refresh, { deep: true, immediate: true })

    const swatches = computed(() => (roles.value
      ? ROLES.map(({ key, label }) => ({ key, label, colour: roles.value[key] }))
      : []))

    return { failed, roles, swatches }
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-medium bg-surface-container-low p-4"
    aria-labelledby="appearance-preview-title"
    data-test="appearance-preview"
  >
    <h4
      id="appearance-preview-title"
      class="md-title-small text-on-surface"
    >
      Preview
    </h4>
    <p
      v-if="failed"
      class="md-body-small text-error"
    >
      The preview could not be loaded.
    </p>
    <p
      v-else-if="roles === null"
      class="md-body-small text-on-surface-variant"
    >
      Set a seed colour to preview the generated scheme.
    </p>
    <ul
      v-else-if="roles"
      class="flex flex-wrap gap-3"
    >
      <li
        v-for="swatch in swatches"
        :key="swatch.key"
        class="flex w-20 flex-col items-center gap-1"
      >
        <span
          class="block h-10 w-full rounded-corner-small border border-outline-variant"
          :style="{ backgroundColor: swatch.colour }"
          :data-role="swatch.key"
          :data-colour="swatch.colour"
          aria-hidden="true"
        />
        <span class="md-label-medium text-on-surface">{{ swatch.label }}</span>
        <span class="md-label-small text-on-surface-variant">{{ swatch.colour }}</span>
      </li>
    </ul>
  </section>
</template>
