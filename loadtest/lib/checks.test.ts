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
import { statusIn, statusIs } from './checks.ts';

test('statusIs accepts the expected status', () => {
  assert.equal(statusIs(200)({ status: 200 }), true);
});

test('statusIs refuses any other status', () => {
  assert.equal(statusIs(200)({ status: 404 }), false);
});

test('statusIn accepts both of the expected statuses', () => {
  const check = statusIn([200, 404]);

  assert.equal(check({ status: 200 }), true);
  assert.equal(check({ status: 404 }), true);
});

test('statusIn refuses a status outside the expected set', () => {
  assert.equal(statusIn([200, 404])({ status: 500 }), false);
});
