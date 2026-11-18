<script setup>
import { computed, ref, useId, watch } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'

/*
 * Shown when a save was refused because the stored settings, token, team or permission nodes changed since this dashboard read them.
 * The dialog only reports the user's choice: the owning store performs reload, review or overwrite.
 * `conflict` is { kind: 'settings' | 'token', name, title, reviewed, server } or null when closed. Once the
 * stored settings were reviewed, `differenceCount` says how many changes the review lists; the review itself
 * is a sheet the owner opens, so the dialog keeps no list of its own.
 */
const props = defineProps({
  conflict: {
    type: Object,
    default: null
  },
  differenceCount: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['review', 'reload', 'overwrite', 'dismiss'])

// Overwriting drops someone else's change, so it takes a second, explicit press.
const confirmingOverwrite = ref(false)
watch(() => props.conflict, () => { confirmingOverwrite.value = false })

// The dialog fades out once the conflict is cleared, so the last conflict stays drawn until the fade ends.
const shown = ref(props.conflict)
watch(() => props.conflict, (conflict) => { if (conflict) shown.value = conflict })

const titleId = useId()
const bodyId = useId()

const isSettings = computed(() => shown.value?.kind === 'settings')
const TITLES = {
  settings: 'Settings changed elsewhere',
  team: 'Team changed elsewhere',
  nodes: 'Permission nodes changed elsewhere'
}
const title = computed(() => TITLES[shown.value?.kind] ?? 'Token changed elsewhere')

const onOpenChange = (open) => {
  if (!open) emit('dismiss')
}
</script>

<template>
  <MdDialog
    :model-value="conflict !== null"
    :labelledby="titleId"
    :describedby="bodyId"
    role="alertdialog"
    @update:model-value="onOpenChange"
  >
    <div
      v-if="shown"
      data-ingot-component="conflict-dialog"
    >
      <h2
        :id="titleId"
        class="md-headline-small text-on-surface"
      >
        {{ title }}
      </h2>

      <div
        :id="bodyId"
        class="mt-4 flex flex-col gap-3 md-body-medium text-on-surface-variant"
      >
        <p v-if="isSettings">
          The settings of <strong class="text-on-surface">{{ shown.title }}</strong> were saved by someone else after you
          loaded them. Your changes have not been saved.
        </p>
        <p v-else-if="shown.kind === 'team'">
          The team <strong class="text-on-surface">{{ shown.name }}</strong> was changed by someone else after you
          loaded it. Your change has not been saved.
        </p>
        <p v-else-if="shown.kind === 'nodes'">
          The permission nodes of <strong class="text-on-surface">{{ shown.name }}</strong> were changed by someone else
          after you loaded them. Your changes have not been saved.
        </p>
        <p v-else>
          The token <strong class="text-on-surface">{{ shown.name }}</strong> was changed by someone else after you
          loaded it. Your change has not been saved.
        </p>

        <p
          v-if="isSettings && differenceCount !== null"
          role="status"
        >
          <template v-if="differenceCount === 0">
            Nothing differs between the stored settings and your draft.
          </template>
          <template v-else>
            {{ differenceCount }} {{ differenceCount === 1 ? 'change differs' : 'changes differ' }} between the stored settings and your draft.
          </template>
        </p>

        <p
          v-if="confirmingOverwrite"
          class="text-error"
        >
          Overwriting replaces the change made elsewhere with your draft. This cannot be undone.
        </p>
      </div>

      <MdDialogActions class="mt-6">
        <MdButton
          variant="text"
          @click="emit('dismiss')"
        >
          Keep my draft
        </MdButton>
        <MdButton
          v-if="isSettings"
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
      </MdDialogActions>
    </div>
  </MdDialog>
</template>
