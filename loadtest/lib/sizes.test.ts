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
import { test } from 'node:test';
import { REFERENCE_SIZES, resourcesFor, sizeByName } from './sizes.ts';

test('the reference sizes are N, T, S, M and L in that order', () => {
  assert.deepEqual(
    REFERENCE_SIZES.map((size) => size.name),
    ['N', 'T', 'S', 'M', 'L'],
  );
});

test('the reference sizes have the CPU and memory of the design', () => {
  assert.deepEqual(
    REFERENCE_SIZES.map((size) => [size.name, size.cpu, size.memory]),
    [
      ['N', '250m', '256Mi'],
      ['T', '500m', '512Mi'],
      ['S', '1', '1Gi'],
      ['M', '2', '2Gi'],
      ['L', '4', '4Gi'],
    ],
  );
});

test('the requests of every reference size equal its limits', () => {
  for (const size of REFERENCE_SIZES) {
    const resources = resourcesFor(size);

    assert.deepEqual(resources.requests, resources.limits, `size ${size.name}`);
  }
});

test('resourcesFor returns the Kubernetes quantities of the size', () => {
  assert.deepEqual(resourcesFor(sizeByName('M')), {
    requests: { cpu: '2', memory: '2Gi' },
    limits: { cpu: '2', memory: '2Gi' },
  });
});

test('sizeByName refuses a name that is not a reference size', () => {
  assert.throws(() => sizeByName('XL'), /XL/);
});
