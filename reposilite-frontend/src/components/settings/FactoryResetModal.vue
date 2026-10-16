<script setup>
import { ref } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { property } from '../../helpers/vue-extensions'
import MdButton from '../md/MdButton.vue'

const props = defineProps({
  callback: property(Function, true)
})

const showFactoryReset = ref(false)

// The scrim is the MD3 role colour at 32%. The modal's own stylesheet paints the overlay in a
// fixed black, so the colour goes in as an inline style that wins over it.
const overlayStyle = {
  backgroundColor: 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)'
}

/*
 * The reset saves at once to every instance, so the dialog states that plainly. The callback
 * runs the save; the dialog closes and the result shows in the settings action bar.
 */
const factoryReset = () => {
  props.callback()
  showFactoryReset.value = false
}
</script>

<script>
export default {
  inheritAttrs: false
}
</script>

<template>
  <div id="factory-reset">
    <VueFinalModal
      v-model="showFactoryReset"
      v-bind="$attrs"
      teleport-to="body"
      :overlay-style="overlayStyle"
      class="flex min-h-full items-center justify-center p-4"
    >
      <!-- The role sits on the card: the modal root always carries role="dialog" of its own. -->
      <div
        class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="factory-reset-title"
        aria-describedby="factory-reset-body"
      >
        <h2
          id="factory-reset-title"
          class="md-headline-small text-on-surface"
        >
          Reset all to defaults?
        </h2>
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
        <div class="mt-6 flex flex-wrap justify-end gap-2">
          <button
            type="button"
            class="md-state-layer md-focus-ring relative inline-flex min-h-12 items-center justify-center rounded-corner-full px-6 md-label-large text-primary"
            @click="showFactoryReset = false"
          >
            Cancel
          </button>
          <MdButton
            variant="error"
            @click="factoryReset"
          >
            Reset all to defaults
          </MdButton>
        </div>
      </div>
    </VueFinalModal>
    <div @click="showFactoryReset = true">
      <slot name="button" />
    </div>
  </div>
</template>
