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
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { ARTIFACT_SIZES, generateArtifact } from './artifacts.ts';

const SMALL = ARTIFACT_SIZES['1KB'];

const sha1 = (bytes: Uint8Array): string => createHash('sha1').update(bytes).digest('hex');

test('the same seed yields identical artifact bytes on two calls', () => {
  const first = generateArtifact(42, SMALL);
  const second = generateArtifact(42, SMALL);

  assert.deepEqual(Buffer.from(first), Buffer.from(second));
});

test('the same seed yields identical SHA-1 values on two calls', () => {
  assert.equal(sha1(generateArtifact(42, SMALL)), sha1(generateArtifact(42, SMALL)));
});

test('a different seed yields different artifact bytes', () => {
  assert.notDeepEqual(Buffer.from(generateArtifact(42, SMALL)), Buffer.from(generateArtifact(43, SMALL)));
});

test('a generated artifact has exactly the requested size', () => {
  assert.equal(generateArtifact(7, 1_001).length, 1_001);
});

test('the artifact sizes are 1 KB, 1 MB, 50 MB and 200 MB', () => {
  assert.deepEqual(ARTIFACT_SIZES, {
    '1KB': 1_024,
    '1MB': 1_048_576,
    '50MB': 52_428_800,
    '200MB': 209_715_200,
  });
});
