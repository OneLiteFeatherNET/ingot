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
// Checks the upstream mock manifest as text. The live check of the mirror scenario runs in the cluster, not here.

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { LOAD_NAMESPACE } from '../lib/config.ts';

const manifest = readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'upstream-mock.yaml'), 'utf8');

test('every object of the upstream mock lives in the load test namespace', () => {
  const namespaces = [...manifest.matchAll(/^\s*namespace: (.+)$/gm)].map((match) => match[1] ?? '');
  assert.ok(namespaces.length > 0, 'the manifest names no namespace');
  for (const namespace of namespaces) {
    assert.equal(namespace, LOAD_NAMESPACE);
  }
});

test('the upstream mock pins every image by tag and never uses latest', () => {
  const images = [...manifest.matchAll(/^\s*image: (.+)$/gm)].map((match) => match[1] ?? '');
  assert.ok(images.length >= 3, 'the manifest names fewer images than seed, latency and the server');
  for (const image of images) {
    assert.match(image, /:[^:]+$/, `"${image}" has no tag`);
    assert.doesNotMatch(image, /:latest$/, `"${image}" uses the latest tag`);
  }
});

test('the upstream mock sets its latency through the LATENCY_MS value', () => {
  assert.match(manifest, /name: LATENCY_MS\n\s+value: "0"/);
});
