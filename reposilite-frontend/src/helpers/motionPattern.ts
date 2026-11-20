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

import type { MotionDirection } from './motionDirection'

/**
 * The transition names of the motion patterns, the class prefixes of src/styles/md3/motion-transitions.css. MdTransition
 * hands them to a Vue <Transition>, and a component whose transition belongs to a library, such as the modal of MdDialog,
 * passes them to that library, so no component writes a class prefix of its own.
 */
export type MotionPattern = 'fade-through' | 'shared-x' | 'shared-y' | 'menu' | 'dialog' | 'sheet' | 'snackbar' | 'expand' | 'fade'

// A sibling that comes earlier in the list moves the page the other way, so shared-x has a class for each direction.
export const motionTransitionName = (pattern: MotionPattern, direction: MotionDirection = 0): string => {
  const base = `motion-${pattern}`
  return pattern === 'shared-x' && direction === -1 ? `${base}-back` : base
}

// The scrim behind a dialog fades on its own, with the timing of the pattern it belongs to.
export const motionScrimTransitionName = (pattern: 'dialog'): string => `${motionTransitionName(pattern)}-scrim`
