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
// Writes the seeded artifacts that the upstream mock serves. It runs in the init container of the mock with Node 24, which
// strips the types of this file, so it imports the generator of the load tests and uses no other dependency. The
// ConfigMap that carries it keeps the layout of the repository: generate.ts at k8s/upstream-mock/ and artifacts.ts at lib/.

import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ARTIFACT_SIZES, generateArtifact } from '../../lib/artifacts.ts';

/** The seed of every mock artifact. Setup of the load tests must use the same seed, so the checksums match. */
export const MOCK_SEED = 20261118;

export interface MockArtifact {
  readonly file: string;
  readonly seed: number;
  readonly size: number;
}

/** One file per seeded size, served at /seeded/<file>. */
export function mockArtifactPlan(): MockArtifact[] {
  return (Object.keys(ARTIFACT_SIZES) as (keyof typeof ARTIFACT_SIZES)[]).map((name) => ({
    file: `${name}.bin`,
    seed: MOCK_SEED,
    size: ARTIFACT_SIZES[name],
  }));
}

export function writeMockArtifacts(directory: string, plan: readonly MockArtifact[]): void {
  for (const entry of plan) {
    writeFileSync(join(directory, entry.file), generateArtifact(entry.seed, entry.size));
  }
}

if (process.argv[1]?.endsWith('generate.ts')) {
  const directory = process.argv[2];
  if (directory === undefined) {
    throw new Error('usage: node generate.ts <directory>');
  }
  mkdirSync(directory, { recursive: true });
  writeMockArtifacts(directory, mockArtifactPlan());
}
