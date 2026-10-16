<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { property } from '../../helpers/vue-extensions'
import { actionLabelOf, actorLabelOf, actorLocationOf, targetLabelOf, targetLocationOf } from './auditQuery'

/*
 * One audited event. The row is dense: time, actor, action, target and result sit in one line on a
 * wide screen and stack on a phone. The details are folded away until the row is opened, so a long
 * page of events stays scannable. The toggle reports itself with the event id, and the parent keeps
 * the set of open rows.
 */
const props = defineProps({
  event: property(Object, true),
  expanded: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle'])

const detailsId = computed(() => `audit-details-${props.event.id}`)
const time = computed(() => new Date(props.event.timestamp).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' }))
const label = computed(() => actionLabelOf(props.event))
const actor = computed(() => actorLabelOf(props.event))
const actorLink = computed(() => actorLocationOf(props.event))
const target = computed(() => targetLabelOf(props.event))
const targetLink = computed(() => targetLocationOf(props.event))
const detailsText = computed(() => (props.event.details ? JSON.stringify(props.event.details, null, 2) : null))
const attempts = computed(() => (props.event.occurrences > 1 ? `${props.event.occurrences} attempts` : null))
</script>

<template>
  <li class="border-b border-outline-variant px-4 py-3 last:border-b-0">
    <div class="grid gap-x-4 gap-y-1 min-[720px]:grid-cols-[11rem_minmax(7rem,12rem)_minmax(9rem,13rem)_minmax(0,1fr)_auto] min-[720px]:items-center">
      <time
        :datetime="event.timestamp"
        class="md-body-small text-on-surface-variant"
      >{{ time }}</time>

      <div class="min-w-0 md-body-medium text-on-surface">
        <RouterLink
          v-if="actorLink"
          :to="actorLink"
          class="md-state-layer md-focus-ring break-all rounded-corner-small text-primary no-underline hover:underline"
        >
          {{ actor }}
        </RouterLink>
        <span
          v-else
          class="break-all"
        >{{ actor }}</span>
        <span
          v-if="event.actorAddress"
          class="md-body-small text-on-surface-variant"
        > from {{ event.actorAddress }}</span>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <span class="md-label-large text-on-surface">{{ label }}</span>
      </div>

      <div class="min-w-0 md-body-medium text-on-surface-variant">
        <RouterLink
          v-if="targetLink"
          :to="targetLink"
          class="md-state-layer md-focus-ring break-all rounded-corner-small text-primary no-underline hover:underline"
        >
          {{ target }}
        </RouterLink>
        <span
          v-else-if="target"
          class="break-all"
        >{{ target }}</span>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-2 min-[720px]:justify-end">
        <span
          class="md-label-medium"
          :class="event.success ? 'text-on-surface-variant' : 'text-error'"
        >
          {{ event.success ? 'Success' : 'Failed' }}
          <template v-if="attempts">· {{ attempts }}</template>
        </span>
        <button
          type="button"
          class="md-state-layer md-focus-ring rounded-corner-full px-3 py-1 md-label-large text-primary"
          :aria-expanded="expanded ? 'true' : 'false'"
          :aria-controls="detailsId"
          @click="emit('toggle', event.id)"
        >
          {{ expanded ? 'Hide details' : 'Details' }}
        </button>
      </div>
    </div>

    <div
      v-if="expanded"
      :id="detailsId"
      class="mt-3 rounded-corner-medium bg-surface-container-low p-3"
    >
      <dl class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 md-body-small text-on-surface-variant">
        <dt>Event id</dt>
        <dd class="text-on-surface">
          {{ event.id }}
        </dd>
        <dt>Time (UTC)</dt>
        <dd class="break-all text-on-surface">
          {{ event.timestamp }}
        </dd>
        <dt>Repository</dt>
        <dd class="break-all text-on-surface">
          {{ event.repository || '-' }}
        </dd>
        <dt>Path</dt>
        <dd class="break-all text-on-surface">
          {{ event.path || '-' }}
        </dd>
      </dl>
      <pre
        v-if="detailsText"
        class="mt-3 overflow-x-auto whitespace-pre-wrap break-all rounded-corner-small bg-surface-container-lowest p-3 md-body-small text-on-surface"
      >{{ detailsText }}</pre>
    </div>
  </li>
</template>
