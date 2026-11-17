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

// The uptime of a JVM in the two largest adjacent units, as the administrator reads it: "2 d 4 h", "3 h 12 min",
// "11 min 40 s" or "45 s". A whole unit at the end is left out ("3 h", "11 min"). A value that is not a positive time
// reads as zero seconds.
export const formatUptime = (millis: number): string => {
  const total = Number.isFinite(millis) && millis > 0 ? Math.floor(millis / 1000) : 0
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60

  if (days > 0) return hours > 0 ? `${days} d ${hours} h` : `${days} d`
  if (hours > 0) return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`
  if (minutes > 0) return seconds > 0 ? `${minutes} min ${seconds} s` : `${minutes} min`
  return `${seconds} s`
}
