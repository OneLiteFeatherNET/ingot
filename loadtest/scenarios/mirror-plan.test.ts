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
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { ARTIFACT_SIZES } from '../lib/artifacts.ts';
import {
  MIRROR_EXPECTED_STATUSES,
  MIRROR_REPOSITORY,
  MIRROR_UPSTREAM_BASE_URL,
  mirrorPath,
  mirrorSizeFor,
  mirrorUpstreamUrl,
  MIRROR_SIZES,
} from './mirror-plan.ts';

const SCENARIO_DIRECTORY = dirname(fileURLToPath(import.meta.url));

test('a mirror request asks the proxy repository for a seeded artifact', () => {
  assert.equal(mirrorPath('1KB'), `/${MIRROR_REPOSITORY}/seeded/1KB.bin`);
});

test('the proxy fetches a seeded artifact from the upstream mock inside the cluster', () => {
  assert.equal(mirrorUpstreamUrl('1MB'), 'http://upstream-mock.ingot-loadtest.svc.cluster.local:8080/seeded/1MB.bin');
  assert.equal(MIRROR_UPSTREAM_BASE_URL, 'http://upstream-mock.ingot-loadtest.svc.cluster.local:8080');
});

test('the mirror covers every seeded artifact size, in the order of the generator', () => {
  assert.deepEqual([...MIRROR_SIZES], Object.keys(ARTIFACT_SIZES));
});

test('the mirror iterations cycle through the sizes deterministically', () => {
  assert.equal(mirrorSizeFor(0), '1KB');
  assert.equal(mirrorSizeFor(1), '1MB');
  assert.equal(mirrorSizeFor(MIRROR_SIZES.length), '1KB');
  assert.equal(mirrorSizeFor(MIRROR_SIZES.length + 2), '50MB');
});

test('a mirror answer of 200 is the expected outcome', () => {
  assert.deepEqual([...MIRROR_EXPECTED_STATUSES], [200]);
});

test('no URL of the mirror scenario points outside the cluster', () => {
  // The license header names the Apache URL, so only the code after it is checked.
  const sources = ['mirror-plan.ts', 'mirror.ts'].map((file) => {
    const source = readFileSync(join(SCENARIO_DIRECTORY, file), 'utf8');
    return source.slice(source.indexOf('*/') + 2);
  });
  const urls = sources.flatMap((source) => [...source.matchAll(/https?:\/\/[^\s'"`/]+/g)].map((match) => match[0]));
  assert.ok(urls.length > 0, 'the scenario names no URL to check');
  for (const url of urls) {
    const host = new URL(url).hostname;
    const inCluster = host === 'localhost' || host.endsWith('.svc.cluster.local');
    assert.ok(inCluster, `"${url}" points outside the cluster`);
  }
});
