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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import MdButton from './MdButton.vue'
import MdTransition from './MdTransition.vue'
import type { MenuEntry, MenuItem } from './types'

/*
 * A button that opens a menu of actions on what it names, such as the actions of one row of a table. The menu
 * is teleported to the body and placed from the bounding rect of the button, so a scrolling table cannot clip it.
 *
 * Props:
 *   label  the accessible name of the button, such as "Actions for releases"
 *   items  [{ value, label, ariaLabel, supporting, error, disabled, href }]: the text of each item, its accessible
 *          name, an optional second line (`supporting`, which joins the accessible name), whether it is a destructive
 *          action (drawn in the error colour), whether it is disabled (it reports no choice and the arrow keys skip it),
 *          and an optional address. An item with an address is a
 *          link with the menuitem role, so middle click and open in new tab work. A plain click on it is reported
 *          through `select` and not followed by the browser (the router takes it); a click with a modifier key is
 *          left to the browser, and the menu closes.
 *          An item with `type: 'radio'` and `checked` is a menu radio button, drawn as the choice in use. An item
 *          with `type: 'separator'` is a divider between groups, and takes no focus.
 * Slots:
 *   trigger  the content of the button, in place of the word Actions. The button is then named by its content, so the
 *            content carries the name: visually hidden text that says it opens a menu, followed by the visible name.
 *            The label names the menu alone (the menu element is labelled by it)
 *   header   a line above the items, such as the name of the signed in user; it is not a menu item
 * Emits:
 *   select  the value of the chosen item; the menu closes and the focus returns to the button
 *   toggle  the menu opened (true) or closed (false), for a parent that has to raise its own row above the rows that follow
 *
 * Keyboard: Enter or Space open the menu through the click of the native button, and ArrowDown opens it too. The
 * first item then has the focus. Arrow keys move between the items and wrap at the ends. Escape closes the menu
 * and returns the focus to the button. A focus that leaves the menu closes it, so Tab leaves the menu as well.
 *
 * Motion: the menu opens and closes through the menu pattern of MdTransition. It scales from the corner next to the
 * button, the top corner when it opens below the button and the bottom corner when it opens above it.
 */
withDefaults(defineProps<{
  label: string
  items: MenuItem[]
  // Deprecated, kept for one release: classes for the trigger button. Use the `trigger` slot to change the trigger.
  triggerClass?: string
  // The accessible name of the button when a trigger slot is given, such as "Account menu, Alice". Without it the
  // button is named by its content.
  triggerLabel?: string
}>(), {
  triggerClass: '',
  triggerLabel: ''
})

defineSlots<{
  trigger?(): unknown
  header?(): unknown
}>()

const emit = defineEmits<{
  select: [value: string]
  toggle: [open: boolean]
}>()

const menuOpen = ref(false)
const root = ref<HTMLElement | null>(null)
const menuElement = ref<HTMLElement | null>(null)
const placement = ref<Record<string, string>>({})

const GAP = 4
const triggerElement = () => root.value?.querySelector('button')
// The items the arrow keys visit: the disabled ones are skipped.
const menuItems = (): HTMLElement[] => [...(menuElement.value?.querySelectorAll<HTMLElement>(
  '[role="menuitem"]:not([aria-disabled="true"]), [role="menuitemradio"]:not([aria-disabled="true"])'
) ?? [])]

const place = async () => {
  const trigger = triggerElement()
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  const right = `${window.innerWidth - rect.right}px`
  placement.value = { top: `${rect.bottom + GAP}px`, right }
  await nextTick()
  // The layout height, which the scale of the enter transition does not shrink, so the menu flips at the same point.
  const height = menuElement.value?.offsetHeight ?? 0
  if (rect.bottom + GAP + height > window.innerHeight && rect.top - GAP - height >= 0) {
    placement.value = { bottom: `${window.innerHeight - rect.top + GAP}px`, right }
  }
}

// The menu grows from the edge that touches its button.
const origin = computed(() => ('bottom' in placement.value ? 'origin-bottom-right' : 'origin-top-right'))

const closeMenu = () => {
  menuOpen.value = false
}

const insideMenu = (node: EventTarget | null) => root.value?.contains(node as Node | null) || menuElement.value?.contains(node as Node | null)

const onPointerdown = (event: Event) => {
  if (!insideMenu(event.target)) closeMenu()
}

// A scroll moves the button, so the menu follows it, and it closes only when the button has left the screen. A menu
// that closed on every scroll lost the race with a scroll that began before it opened: the browser scrolls a row into
// view for the click, and the scroll event arrives after the menu is drawn.
const onScroll = () => {
  const rect = triggerElement()?.getBoundingClientRect()
  if (!rect || rect.bottom < 0 || rect.top > window.innerHeight) closeMenu()
  else place()
}

const listen = (on: boolean) => {
  const toggle = on ? 'addEventListener' : 'removeEventListener'
  document[toggle]('pointerdown', onPointerdown)
  window[toggle]('scroll', onScroll, true)
  window[toggle]('resize', closeMenu)
}

watch(menuOpen, async (open) => {
  emit('toggle', open)
  listen(open)
  if (!open) return
  await place()
  menuItems()[0]?.focus({ preventScroll: true })
})

onBeforeUnmount(() => listen(false))

const toggle = () => {
  menuOpen.value = !menuOpen.value
}

const openFromKeyboard = (event: KeyboardEvent) => {
  event.preventDefault()
  menuOpen.value = true
}

const choose = (value: string) => {
  menuOpen.value = false
  emit('select', value)
  triggerElement()?.focus({ preventScroll: true })
}

// A link item is followed by the router on a plain click. With a modifier key, or a button other than the primary
// one, the browser opens the address itself, so the menu only closes.
const chooseLink = (event: MouseEvent, value: string) => {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
    closeMenu()
    return
  }
  event.preventDefault()
  choose(value)
}

const onMenuKeydown = (event: KeyboardEvent) => {
  const items = menuItems()
  const index = items.indexOf(document.activeElement as HTMLElement)
  const move = (step: number) => {
    event.preventDefault()
    items[(index + step + items.length) % items.length]?.focus({ preventScroll: true })
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu()
    triggerElement()?.focus({ preventScroll: true })
  } else if (event.key === 'ArrowDown') {
    move(1)
  } else if (event.key === 'ArrowUp') {
    move(-1)
  } else if (event.key === 'Home') {
    event.preventDefault()
    items[0]?.focus({ preventScroll: true })
  } else if (event.key === 'End') {
    event.preventDefault()
    items[items.length - 1]?.focus({ preventScroll: true })
  }
}

// Closes the menu when the focus leaves both the button and the menu, so a click elsewhere does not leave it open.
const onFocusout = (event: FocusEvent) => {
  if (!insideMenu(event.relatedTarget)) closeMenu()
}

// The one horizontal inset of the menu: the header and the items share it, so their text lines up.
const inset = 'px-3'
// The text colour belongs to the tone, not to the base class, so a tone replaces it and no important override is needed.
const itemClass = `md-state-layer md-focus-ring md-touch-target relative flex w-full items-center rounded-corner-small ${inset} py-2 text-left md-label-large`
const TONE = {
  plain: 'text-on-surface',
  error: 'text-error',
  checked: 'bg-secondary-container text-on-secondary-container',
  disabled: 'text-on-surface/md-disabled-content'
}
const toneOf = (item: MenuEntry): string => {
  if (item.disabled) return TONE.disabled
  if (item.type === 'radio' && item.checked) return TONE.checked
  return item.error ? TONE.error : TONE.plain
}
// With an aria label, the supporting line joins it. Without one, the content of the item gives the name.
const nameOf = (item: MenuEntry): string | undefined =>
  item.ariaLabel && item.supporting ? `${item.ariaLabel}, ${item.supporting}` : item.ariaLabel
</script>

<template>
  <div
    ref="root"
    class="relative shrink-0"
    data-ingot-component="md-overflow-menu"
    @focusout="onFocusout"
  >
    <MdButton
      variant="text"
      type="button"
      :class="triggerClass"
      :aria-label="triggerLabel || ($slots.trigger ? undefined : label)"
      aria-haspopup="menu"
      :aria-expanded="menuOpen ? 'true' : 'false'"
      @click="toggle"
      @keydown.down="openFromKeyboard"
    >
      <slot name="trigger">
        Actions
      </slot>
    </MdButton>
    <Teleport to="body">
      <MdTransition pattern="menu">
        <div
          v-if="menuOpen"
          ref="menuElement"
          role="menu"
          :aria-label="label"
          :style="{ position: 'fixed', ...placement }"
          class="z-md-menu flex w-56 max-w-md-menu flex-col gap-1 rounded-corner-extra-small bg-surface-container p-2 shadow-elevation-2"
          :class="origin"
          @keydown="onMenuKeydown"
          @focusout="onFocusout"
        >
          <div
            v-if="$slots.header"
            role="none"
            :class="[inset, 'pb-2 pt-1']"
          >
            <slot name="header" />
          </div>
          <template
            v-for="(item, index) in items"
            :key="item.type === 'separator' ? `separator-${index}` : item.value"
          >
            <div
              v-if="item.type === 'separator'"
              role="separator"
              class="my-1 h-px bg-outline-variant"
            />
            <a
              v-else-if="item.href && !item.disabled"
              :href="item.href"
              role="menuitem"
              :aria-label="nameOf(item)"
              :class="[itemClass, toneOf(item)]"
              @click="chooseLink($event, item.value)"
            >
              <span class="flex min-w-0 flex-col">
                <span>{{ item.label }}</span>
                <span
                  v-if="item.supporting"
                  class="md-body-medium text-on-surface-variant"
                >{{ item.supporting }}</span>
              </span>
            </a>
            <button
              v-else-if="item.type === 'radio'"
              type="button"
              role="menuitemradio"
              :aria-checked="item.checked ? 'true' : 'false'"
              :aria-disabled="item.disabled ? 'true' : undefined"
              :aria-label="nameOf(item)"
              :class="[itemClass, toneOf(item)]"
              @click="item.disabled || choose(item.value)"
            >
              <span class="flex min-w-0 flex-col">
                <span>{{ item.label }}</span>
                <span
                  v-if="item.supporting"
                  class="md-body-medium text-on-surface-variant"
                >{{ item.supporting }}</span>
              </span>
            </button>
            <button
              v-else
              type="button"
              role="menuitem"
              :aria-disabled="item.disabled ? 'true' : undefined"
              :aria-label="nameOf(item)"
              :class="[itemClass, toneOf(item)]"
              @click="item.disabled || choose(item.value)"
            >
              <span class="flex min-w-0 flex-col">
                <span>{{ item.label }}</span>
                <span
                  v-if="item.supporting"
                  class="md-body-medium text-on-surface-variant"
                >{{ item.supporting }}</span>
              </span>
            </button>
          </template>
        </div>
      </MdTransition>
    </Teleport>
  </div>
</template>
