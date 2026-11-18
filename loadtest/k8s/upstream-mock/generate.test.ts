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

import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { ARTIFACT_SIZES, generateArtifact } from '../../lib/artifacts.ts';
import { MOCK_SEED, mockArtifactPlan, writeMockArtifacts } from './generate.ts';

test('the mock plan serves one file for each of the four seeded artifact sizes', () => {
  const plan = mockArtifactPlan();
  assert.deepEqual(
    plan.map((entry) => entry.file),
    ['1KB.bin', '1MB.bin', '50MB.bin', '200MB.bin'],
  );
  assert.deepEqual(
    plan.map((entry) => entry.size),
    [ARTIFACT_SIZES['1KB'], ARTIFACT_SIZES['1MB'], ARTIFACT_SIZES['50MB'], ARTIFACT_SIZES['200MB']],
  );
});

test('the mock plan uses the fixed seed of the mock for every file', () => {
  for (const entry of mockArtifactPlan()) {
    assert.equal(entry.seed, MOCK_SEED);
  }
});

test('a written mock file holds the bytes of the seeded generator', () => {
  const directory = mkdtempSync(join(tmpdir(), 'ingot-upstream-mock-'));
  try {
    writeMockArtifacts(directory, [{ file: 'small.bin', seed: MOCK_SEED, size: 1024 }]);
    const written = readFileSync(join(directory, 'small.bin'));
    assert.deepEqual(new Uint8Array(written), generateArtifact(MOCK_SEED, 1024));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
