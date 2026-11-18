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
import { LOADTEST_NODE_LABEL } from '../lib/config.ts';
import { checkReferenceNodes, labelledNodesCommand, nodesNeeded } from './nodes.ts';

test('a reference run on the monolith needs two labelled nodes, one for the server and one for the load generator', () => {
  assert.equal(nodesNeeded('monolith'), 2);
});

test('a reference run on the scalable read-4 topology needs eight labelled nodes', () => {
  assert.equal(nodesNeeded('scalable-read-4'), 8);
});

test('a reference run with enough labelled nodes passes the check', () => {
  assert.deepEqual(checkReferenceNodes({ reference: true, topology: 'monolith', found: 2 }), { reference: true });
});

test('a reference run with too few labelled nodes is refused and names the found and the needed count', () => {
  assert.throws(
    () => checkReferenceNodes({ reference: true, topology: 'ha', found: 2 }),
    (error: unknown) =>
      error instanceof Error && /found 2/.test(error.message) && /needs 4/.test(error.message),
  );
});

test('a non-reference run skips the node check and is marked reference false', () => {
  assert.deepEqual(checkReferenceNodes({ reference: false, topology: 'ha', found: 0 }), { reference: false });
});

test('the node listing selects the nodes by the load test label read from the config', () => {
  assert.deepEqual(labelledNodesCommand(), ['get', 'nodes', '-l', `${LOADTEST_NODE_LABEL}=true`, '-o', 'name']);
});
