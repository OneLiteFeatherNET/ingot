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
import { thresholdFor } from '../lib/thresholds.ts';
import { DEPLOY_EXPECTED_STATUSES, DEPLOY_P95_MS, DEPLOY_SIZES, deployPath } from './deploy-plan.ts';

const SCENARIO_DIRECTORY = dirname(fileURLToPath(import.meta.url));

const COORDINATE = { groupId: 'net.onelitefeather.loadtest', artifactId: 'deploy-fixture' } as const;

test('the deploy scenario uploads 1 KB, 1 MB, 50 MB and 200 MB artifacts', () => {
  assert.deepEqual([...DEPLOY_SIZES], ['1KB', '1MB', '50MB', '200MB']);
  assert.equal(ARTIFACT_SIZES[DEPLOY_SIZES[3]!], 209_715_200);
});

test('a deploy writes the jar under a version of its own sequence number', () => {
  assert.equal(
    deployPath('snapshots', COORDINATE, 3),
    '/snapshots/net/onelitefeather/loadtest/deploy-fixture/1.0.3/deploy-fixture-1.0.3.jar',
  );
});

test('two deploys in one run never share a path', () => {
  assert.notEqual(deployPath('releases', COORDINATE, 1), deployPath('releases', COORDINATE, 2));
});

test('an accepted deploy answers 200 OK', () => {
  assert.deepEqual([...DEPLOY_EXPECTED_STATUSES], [200]);
});

test('the deploy script runs the profile the runner chose instead of a single iteration', () => {
  // The script imports k6 modules and cannot load under node, so its exports are read from the source.
  const source = readFileSync(join(SCENARIO_DIRECTORY, 'deploy.ts'), 'utf8');

  assert.match(source, /export const options = profileOptions\(__ENV, 'deploy', DEPLOY_P95_MS\);/);
  assert.match(source, /export \{ stepVerdict \} from '\.\.\/profiles\/step-verdict-k6\.ts';/);
});

test('the deploy profile is held to the p95 limit of its largest size, which dominates the p95 of the cycle', () => {
  assert.equal(DEPLOY_P95_MS, thresholdFor('deploy', '200MB').p95Ms);
});
