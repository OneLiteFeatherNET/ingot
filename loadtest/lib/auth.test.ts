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
import { basicAuthHeader, bearerAuthHeader, base64Encode } from './auth.ts';

test('base64Encode matches the standard encoding of an ASCII credential', () => {
  assert.equal(base64Encode('maven:s3cret'), Buffer.from('maven:s3cret').toString('base64'));
});

test('base64Encode pads the output to a multiple of four characters', () => {
  assert.equal(base64Encode('a'), 'YQ==');
  assert.equal(base64Encode('ab'), 'YWI=');
  assert.equal(base64Encode('abc'), 'YWJj');
});

test('basicAuthHeader encodes user and password as Basic credentials', () => {
  assert.deepEqual(basicAuthHeader('maven', 's3cret'), {
    Authorization: `Basic ${Buffer.from('maven:s3cret').toString('base64')}`,
  });
});

test('bearerAuthHeader sends the token as a Bearer credential', () => {
  assert.deepEqual(bearerAuthHeader('tok_123'), { Authorization: 'Bearer tok_123' });
});
