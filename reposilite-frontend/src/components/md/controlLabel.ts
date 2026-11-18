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

/**
 * The gap between a selection control (checkbox, switch, radio) and its label. One part, so the
 * three controls read the same values. `tight` is 8px and `loose` is 12px. Each control has its
 * own default, the gap it had before this part existed, so no render changes unless a caller
 * asks for the other value. The class names are spelled out in full so that Tailwind finds them.
 */
export type ControlLabelGap = 'tight' | 'loose'

const CONTROL_LABEL_GAP_CLASS: Record<ControlLabelGap, string> = {
  tight: 'gap-2',
  loose: 'gap-3'
}

export const controlLabelGapClass = (gap: ControlLabelGap): string => CONTROL_LABEL_GAP_CLASS[gap]
