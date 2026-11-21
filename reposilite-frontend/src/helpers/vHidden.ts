/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { ObjectDirective } from 'vue'

/**
 * v-show for an element that is hidden with the `hidden` attribute instead of `display: none`. The value is true when
 * the element is hidden. Inside a <Transition> (or MdTransition) the element plays the enter and leave classes of the
 * transition, like a v-show child, so a tab panel can move while every panel stays in the document and the panels that
 * are not selected keep the attribute that plugins and tests select on.
 *
 * A leaving element is still drawn until its leave has ended. It is marked `inert` meanwhile, so it takes no focus and no
 * click and is not read out next to the element that replaces it.
 */
const setHidden = (element: HTMLElement, hidden: boolean) => {
  element.hidden = hidden
  element.inert = false
}

export const vHidden: ObjectDirective<HTMLElement, boolean> = {
  beforeMount(element, { value }, { transition }) {
    if (transition && !value) {
      transition.beforeEnter(element)
    } else {
      setHidden(element, value)
    }
  },
  mounted(element, { value }, { transition }) {
    if (transition && !value) {
      transition.enter(element)
    }
  },
  updated(element, { value, oldValue }, { transition }) {
    if (!value === !oldValue) {
      return
    }
    if (!transition) {
      setHidden(element, value)
    } else if (value) {
      element.inert = true
      transition.leave(element, () => setHidden(element, true))
    } else {
      transition.beforeEnter(element)
      setHidden(element, false)
      transition.enter(element)
    }
  },
  beforeUnmount(element, { value }) {
    setHidden(element, value)
  },
}
