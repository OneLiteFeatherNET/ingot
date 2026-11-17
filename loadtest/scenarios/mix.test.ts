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
import { SCENARIOS } from '../lib/config.ts';
import { MIX_WEIGHTS, pickScenario } from './mix-plan.ts';

test('the mix weights sum to 1', () => {
  const total = Object.values(MIX_WEIGHTS).reduce((sum, weight) => sum + weight, 0);

  assert.ok(Math.abs(total - 1) < 1e-9, `weights sum to ${total}`);
});

test('every scenario in the mix is a known scenario, and the mix never contains itself', () => {
  for (const name of Object.keys(MIX_WEIGHTS)) {
    assert.ok((SCENARIOS as readonly string[]).includes(name), `unknown scenario ${name}`);
    assert.notEqual(name, 'mix');
  }
});

test('the resolve and deploy scenarios are in the mix', () => {
  assert.ok('resolve' in MIX_WEIGHTS);
  assert.ok('deploy' in MIX_WEIGHTS);
});

test('a random value of zero picks the first scenario of the mix', () => {
  assert.equal(pickScenario(MIX_WEIGHTS, 0), 'resolve');
});

test('a random value at the top of the range picks the last scenario of the mix', () => {
  assert.equal(pickScenario(MIX_WEIGHTS, 0.999_999), 'deploy');
});
