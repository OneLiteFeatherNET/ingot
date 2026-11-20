<script setup lang="ts">
import { ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'

defineOptions({
  inheritAttrs: false
})

const props = defineProps<{
  callback: () => void
}>()

const showFactoryReset = ref(false)

/*
 * The reset saves at once to every instance, so the dialog states that plainly. The callback
 * runs the save; the dialog closes and the result shows in the settings action bar.
 */
const factoryReset = () => {
  props.callback()
  showFactoryReset.value = false
}
</script>

<template>
  <div
    id="factory-reset"
    class="shrink-0"
    data-ingot-component="factory-reset-modal"
  >
    <MdDialog
      v-model="showFactoryReset"
      v-bind="$attrs"
      labelledby="factory-reset-title"
      describedby="factory-reset-body"
      role="alertdialog"
    >
      <template #header>
        <h2
          id="factory-reset-title"
          class="md-headline-small text-on-surface"
        >
          Reset all to defaults?
        </h2>
      </template>
      <div
        id="factory-reset-body"
        class="mt-4 flex flex-col gap-3 md-body-medium text-on-surface-variant"
      >
        <p>
          Every setting on all your instances goes back to its default value. The reset is
          saved immediately to all instances and cannot be undone.
        </p>
        <p>
          Unsaved edits in any tab are discarded.
        </p>
      </div>
      <template #actions>
        <MdDialogActions class="mt-6">
          <MdButton
            variant="text"
            @click="showFactoryReset = false"
          >
            Cancel
          </MdButton>
          <MdButton
            variant="filled"
            error
            @click="factoryReset"
          >
            Reset all to defaults
          </MdButton>
        </MdDialogActions>
      </template>
    </MdDialog>
    <div @click="showFactoryReset = true">
      <slot name="button" />
    </div>
  </div>
</template>
