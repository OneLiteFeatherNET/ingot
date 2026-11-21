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
import { CPU_MODEL_VARIABLE, cpuModel, nodeTypeFrom } from './node-type.ts';

const node = (cpu: string, memory: string): string => JSON.stringify({ status: { capacity: { cpu, memory } } });

test('the node type takes cores and memory from the node capacity and the CPU model from the operator', () => {
  assert.deepEqual(nodeTypeFrom(node('8', '32780412Ki'), 'Example CPU'), {
    cpuModel: 'Example CPU',
    cores: 8,
    memoryGiB: 31.3,
  });
});

test('the node memory is read in Mi, Gi and plain bytes as well', () => {
  assert.equal(nodeTypeFrom(node('4', '16384Mi'), 'Example CPU').memoryGiB, 16);
  assert.equal(nodeTypeFrom(node('4', '16Gi'), 'Example CPU').memoryGiB, 16);
  assert.equal(nodeTypeFrom(node('4', '17179869184'), 'Example CPU').memoryGiB, 16);
});

test('a CPU capacity in millicores counts whole cores', () => {
  assert.equal(nodeTypeFrom(node('8000m', '16Gi'), 'Example CPU').cores, 8);
});

test('a node without a readable capacity is refused', () => {
  assert.throws(() => nodeTypeFrom(JSON.stringify({ status: {} }), 'Example CPU'), /capacity/);
  assert.throws(() => nodeTypeFrom(node('8', 'lots'), 'Example CPU'), /memory/);
});

test('the CPU model comes from its variable', () => {
  assert.equal(cpuModel({ [CPU_MODEL_VARIABLE]: ' Example CPU ' }), 'Example CPU');
});

test('a run that writes a record without a CPU model is refused, and the error names the variable', () => {
  assert.throws(() => cpuModel({}), new RegExp(CPU_MODEL_VARIABLE));
  assert.throws(() => cpuModel({ [CPU_MODEL_VARIABLE]: '  ' }), new RegExp(CPU_MODEL_VARIABLE));
});
