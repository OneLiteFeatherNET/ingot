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

// Roughly two lines of the log. Less than that and a reader who has nudged the panel up
// by a line is pulled back down; more and a reader who is clearly reading earlier output
// is still treated as following the tail.
export const NEAR_BOTTOM_PX = 32

/**
 * Whether a scrollable panel is showing its end, within a small tolerance.
 *
 * @param {{ scrollTop: number, clientHeight: number, scrollHeight: number }} metrics
 * @param {number} threshold how far from the end still counts as the end, in pixels
 */
export const isNearBottom = (
  { scrollTop, clientHeight, scrollHeight },
  threshold = NEAR_BOTTOM_PX
) => scrollHeight - (scrollTop + clientHeight) <= threshold
