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
/*
 * A summary and the body under it, on the native details element, so the browser keeps the open state, the keyboard
 * and the disclosure role. The chevron turns with the open state, on the motion token.
 *
 * Props:
 *   open     whether the body is shown (v-model:open). It starts closed.
 *   variant  'default' puts the label and a chevron at the end of the summary. 'tree' puts a right-pointing chevron
 *            before the summary content, which is laid out by the caller, and adds no padding around the body.
 * Emits update:open when the browser opens or closes the body.
 * Slots: summary (the label of the disclosure) and default (the body).
 */
import { ref, watch } from 'vue'
import MdIcon from './MdIcon.vue'
import MdTransition from './MdTransition.vue'

const props = withDefaults(defineProps<{ open?: boolean; variant?: 'default' | 'tree' }>(), {
  open: false,
  variant: 'default',
})
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

// expanded drives the body and the chevron. detailsOpen is the open attribute of the details element: it follows
// expanded when the body opens, and lags behind it on a close until the leave transition has ended, so the browser does
// not hide the body while it collapses.
const expanded = ref(props.open)
const detailsOpen = ref(props.open)

const apply = (open: boolean) => {
  expanded.value = open
  if (open) detailsOpen.value = true
}

watch(() => props.open, apply)

const change = (open: boolean) => {
  if (open === expanded.value) return
  apply(open)
  if (open !== props.open) emit('update:open', open)
}

// The summary is toggled here instead of by the browser, which would close the body at once. Enter and Space on the
// summary arrive as a click too.
const onClick = () => change(!expanded.value)

// The browser still toggles the element itself, for example when a find in the page opens the body. The toggle event
// also arrives, late, for the attribute this component set, which it ignores.
const onToggle = (event: Event) => {
  const open = (event.target as HTMLDetailsElement).open
  if (open === detailsOpen.value) return
  if (!open) detailsOpen.value = false
  change(open)
}

const onAfterLeave = () => {
  if (!expanded.value) detailsOpen.value = false
}

const tree = () => props.variant === 'tree'
const CHEVRON = () => (tree() ? 'M9 6l6 6-6 6' : 'M6 9l6 6 6-6')
const chevronClass = () => [
  tree() ? 'shrink-0 text-on-surface-variant' : '',
  expanded.value ? (tree() ? 'rotate-90' : 'rotate-180') : '',
]
const SUMMARY_CLASS = {
  default:
    'md-state-layer md-title-small flex cursor-pointer items-center justify-between gap-2 rounded-corner-small py-3 text-on-surface focus-visible:outline-2 focus-visible:outline-primary',
  tree: 'flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 bg-surface-container-low px-4 py-2',
}
</script>

<template>
  <details
    :open="detailsOpen"
    data-ingot-component="md-disclosure"
    @toggle="onToggle"
  >
    <summary
      :class="SUMMARY_CLASS[variant]"
      @click.prevent="onClick"
    >
      <template v-if="tree()">
        <MdIcon
          :path="CHEVRON()"
          size="sm"
          class="transition-transform duration-md-short3"
          :class="chevronClass()"
        />
        <slot name="summary" />
      </template>
      <template v-else>
        <span><slot name="summary" /></span>
        <MdIcon
          :path="CHEVRON()"
          size="md"
          class="transition-transform duration-md-short3"
          :class="chevronClass()"
        />
      </template>
    </summary>
    <MdTransition
      pattern="expand"
      @after-leave="onAfterLeave"
    >
      <div
        v-show="expanded"
        class="expand-rows"
      >
        <div class="min-h-0 overflow-hidden">
          <div :class="tree() ? '' : 'pb-4 text-on-surface-variant'">
            <slot />
          </div>
        </div>
      </div>
    </MdTransition>
  </details>
</template>
