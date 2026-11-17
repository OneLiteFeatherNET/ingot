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
// Seeded test artifacts. The same seed always yields the same bytes, so a run can be repeated and its checksums compared.
// The generator is pure: it hashes nothing, so the same file runs in k6 (setup) and in the Node unit tests.

export const ARTIFACT_SIZES = {
  '1KB': 1_024,
  '1MB': 1_048_576,
  '50MB': 52_428_800,
  '200MB': 209_715_200,
} as const;

export type ArtifactSize = keyof typeof ARTIFACT_SIZES;

/** mulberry32: a small, fast 32-bit generator with a fixed sequence for a given seed. */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return (t ^ (t >>> 14)) >>> 0;
  };
}

export function generateArtifact(seed: number, size: number): Uint8Array {
  const next = mulberry32(seed);
  const bytes = new Uint8Array(size);
  for (let offset = 0; offset < size; offset += 4) {
    const word = next();
    for (let shift = 0; shift < 4 && offset + shift < size; shift++) {
      bytes[offset + shift] = (word >>> (shift * 8)) & 0xff;
    }
  }
  return bytes;
}
