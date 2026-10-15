<script setup>
import { computed, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'

/*
 * Shown when a save was refused because the stored settings or token changed since this dashboard read them.
 * The dialog only reports the user's choice: the owning store performs reload, review or overwrite.
 * `conflict` is { kind: 'settings' | 'token', name, title, reviewed, differences } or null when closed.
 */
const props = defineProps({
  conflict: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['review', 'reload', 'overwrite', 'dismiss'])

// Overwriting drops someone else's change, so it takes a second, explicit press.
const confirmingOverwrite = ref(false)
watch(() => props.conflict, () => { confirmingOverwrite.value = false })

const isSettings = computed(() => props.conflict?.kind === 'settings')
const title = computed(() => isSettings.value ? 'Settings changed elsewhere' : 'Token changed elsewhere')

// The scrim is the MD3 role colour at 32%, set inline because the modal paints a fixed black overlay.
const overlayStyle = {
  backgroundColor: 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)'
}

const formatValue = (value) => {
  if (value === undefined) return 'not set'
  const text = JSON.stringify(value)
  return text.length > 200 ? `${text.slice(0, 200)}…` : text
}

const onOpenChange = (open) => {
  if (!open) emit('dismiss')
}
</script>

<template>
  <VueFinalModal
    :model-value="conflict !== null"
    teleport-to="body"
    :overlay-style="overlayStyle"
    class="flex min-h-full items-center justify-center p-4"
    @update:model-value="onOpenChange"
  >
    <div
      v-if="conflict"
      class="w-full max-w-lg rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="conflict-title"
      aria-describedby="conflict-body"
    >
      <h2
        id="conflict-title"
        class="md-headline-small text-on-surface"
      >
        {{ title }}
      </h2>

      <div
        id="conflict-body"
        class="mt-4 flex flex-col gap-3 md-body-medium text-on-surface-variant"
      >
        <p v-if="isSettings">
          The settings of <strong class="text-on-surface">{{ conflict.title }}</strong> were saved by someone else after you
          loaded them. Your changes have not been saved.
        </p>
        <p v-else>
          The token <strong class="text-on-surface">{{ conflict.name }}</strong> was changed by someone else after you
          loaded it. Your change has not been saved.
        </p>

        <section
          v-if="isSettings && conflict.reviewed"
          aria-label="Fields that differ"
          class="flex flex-col gap-2"
        >
          <p v-if="conflict.differences.length === 0">
            Nothing differs between the stored settings and your draft.
          </p>
          <ul
            v-else
            class="flex flex-col gap-2"
          >
            <li
              v-for="difference in conflict.differences"
              :key="difference.path"
              class="rounded-corner-small bg-surface-container p-3"
            >
              <code class="md-label-large text-on-surface">{{ difference.path }}</code>
              <div class="md-body-small">
                Yours: {{ formatValue(difference.local) }}
              </div>
              <div class="md-body-small">
                Stored: {{ formatValue(difference.server) }}
              </div>
            </li>
          </ul>
        </section>

        <p
          v-if="confirmingOverwrite"
          class="text-error"
        >
          Overwriting replaces the change made elsewhere with your draft. This cannot be undone.
        </p>
      </div>

      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <button
          type="button"
          class="md-state-layer md-focus-ring relative inline-flex min-h-12 items-center justify-center rounded-corner-full px-6 md-label-large text-primary"
          @click="emit('dismiss')"
        >
          Keep my draft
        </button>
        <MdButton
          v-if="isSettings && !conflict.reviewed"
          variant="tonal"
          @click="emit('review')"
        >
          Review
        </MdButton>
        <MdButton
          variant="outlined"
          @click="emit('reload')"
        >
          Reload and discard my changes
        </MdButton>
        <MdButton
          v-if="!confirmingOverwrite"
          variant="error"
          @click="confirmingOverwrite = true"
        >
          Overwrite
        </MdButton>
        <MdButton
          v-else
          variant="error"
          @click="emit('overwrite')"
        >
          Confirm overwrite
        </MdButton>
      </div>
    </div>
  </VueFinalModal>
</template>
