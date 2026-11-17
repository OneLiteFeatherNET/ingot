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
import {
  LOAD_NAMESPACE,
  PLATEAU_DEFAULT_HOLD_MINUTES,
  PLATEAU_MIN_HOLD_MINUTES,
  assertLoadNamespace,
  assertPlateauHold,
  parseRunOptions,
} from './config.ts';

test('parseRunOptions returns the topology, profile and scenario given as flags', () => {
  const options = parseRunOptions(['--topology', 'ha', '--profile', 'plateau', '--scenario', 'resolve']);

  assert.deepEqual(options, { topology: 'ha', profile: 'plateau', scenario: 'resolve' });
});

test('parseRunOptions refuses a missing flag and names it', () => {
  assert.throws(
    () => parseRunOptions(['--topology', 'ha', '--scenario', 'resolve']),
    /--profile/,
  );
});

test('parseRunOptions refuses an unknown topology and names it', () => {
  assert.throws(
    () => parseRunOptions(['--topology', 'galaxy', '--profile', 'plateau', '--scenario', 'resolve']),
    /galaxy/,
  );
});

test('parseRunOptions refuses an unknown scenario and names it', () => {
  assert.throws(
    () => parseRunOptions(['--topology', 'ha', '--profile', 'plateau', '--scenario', 'login-flood']),
    /login-flood/,
  );
});

test('the default plateau hold is fifteen minutes', () => {
  assert.equal(PLATEAU_DEFAULT_HOLD_MINUTES, 15);
});

test('the minimum plateau hold is ten minutes', () => {
  assert.equal(PLATEAU_MIN_HOLD_MINUTES, 10);
});

test('assertPlateauHold accepts the minimum hold itself', () => {
  assert.equal(assertPlateauHold(10), 10);
});

test('assertPlateauHold refuses a hold of nine minutes and names the ten minute minimum', () => {
  assert.throws(() => assertPlateauHold(9), /at least 10 minutes/);
});

test('assertLoadNamespace accepts the load test namespace', () => {
  assert.equal(assertLoadNamespace(LOAD_NAMESPACE), 'ingot-loadtest');
});

test('assertLoadNamespace refuses another namespace and names it', () => {
  assert.throws(() => assertLoadNamespace('ingot-staging'), /ingot-staging/);
});
