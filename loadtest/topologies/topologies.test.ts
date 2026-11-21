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
// Checks the topology overlays as text, so the unit suite needs no helm binary and no cluster. The rendered output is
// checked by the helm template step of the verification, see the pull request description.

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { TOPOLOGIES } from '../lib/config.ts';

const here = dirname(fileURLToPath(import.meta.url));
const overlayPath = (name: string): string => join(here, `${name}.yaml`);
const overlayText = (name: string): string => readFileSync(overlayPath(name), 'utf8');

const SHARED_KEYS = /^(database|objectStorage|clusterBus):/;

/** The top-level blocks that name the shared PostgreSQL, bucket and NATS settings, kept in file order. */
function sharedSettings(text: string): string {
  const kept: string[] = [];
  let inside = false;
  for (const line of text.split('\n')) {
    if (/^\S/.test(line)) {
      inside = SHARED_KEYS.test(line);
    }
    if (inside && line.trim() !== '') {
      kept.push(line);
    }
  }
  return kept.join('\n');
}

function numberAt(text: string, pattern: RegExp, name: string): number {
  const match = pattern.exec(text);
  assert.ok(match, `${name} does not set the replica count the test looks for`);
  return Number(match[1]);
}

const deploymentReplicas = (text: string, name: string): number =>
  numberAt(text, /^deployment:\n  replicas: (\d+)$/m, name);
const readReplicas = (text: string, name: string): number =>
  numberAt(text, /^  read:\n    replicas: (\d+)$/m, name);
const writeReplicas = (text: string, name: string): number =>
  numberAt(text, /^  write:\n    replicas: (\d+)$/m, name);

test('every topology of the config has an overlay file', () => {
  for (const name of TOPOLOGIES) {
    assert.ok(existsSync(overlayPath(name)), `the overlay of "${name}" is missing`);
  }
});

test('the shared PostgreSQL, bucket and NATS settings are identical in every overlay', () => {
  const reference = sharedSettings(overlayText('monolith'));
  assert.match(reference, /existingSecret: ingot-loadtest-database/, 'the reference overlay names no database secret');
  for (const name of TOPOLOGIES) {
    assert.equal(sharedSettings(overlayText(name)), reference, `the shared settings of "${name}" differ from monolith`);
  }
});

test('no overlay sets a pod size, because the runner applies the reference sizes', () => {
  for (const name of TOPOLOGIES) {
    assert.doesNotMatch(overlayText(name), /^\s*resources:/m, `"${name}" sets resources`);
    assert.doesNotMatch(overlayText(name), /\b(cpu|memory)\b/i, `"${name}" names a pod size`);
  }
});

test('the monolith overlay runs the all-in-one chart with one replica', () => {
  const text = overlayText('monolith');
  assert.match(text, /^mode: allInOne$/m);
  assert.equal(deploymentReplicas(text, 'monolith'), 1);
});

test('the ha overlay runs the all-in-one chart with three replicas', () => {
  const text = overlayText('ha');
  assert.match(text, /^mode: allInOne$/m);
  assert.equal(deploymentReplicas(text, 'ha'), 3);
});

test('the ha overlay turns persistence off, because its replicas cannot share a ReadWriteOnce claim', () => {
  // The repositories, the database and the configuration are shared through the bucket, PostgreSQL and the shared
  // configuration, so each replica keeps its data directory in an emptyDir, as the scalable overlays do.
  assert.match(overlayText('ha'), /^persistence:\n  enabled: false$/m, 'ha mounts one claim on all three replicas');
});

test('the split overlay runs the server and the dashboard as split mode', () => {
  const text = overlayText('split');
  assert.match(text, /^mode: split$/m);
  assert.equal(deploymentReplicas(text, 'split'), 1);
});

test('the scalable overlay sets two read replicas, two write replicas and one worker', () => {
  const text = overlayText('scalable');
  assert.match(text, /^mode: scalable$/m);
  assert.equal(readReplicas(text, 'scalable'), 2);
  assert.equal(writeReplicas(text, 'scalable'), 2);
  assert.match(text, /^  worker:\n    replicas: 1$/m);
});

for (const readCount of [1, 2, 4]) {
  test(`the scalable-read-${readCount} overlay has ${readCount} read replicas`, () => {
    assert.equal(readReplicas(overlayText(`scalable-read-${readCount}`), `scalable-read-${readCount}`), readCount);
  });
}

test('the scalable overlays turn persistence off, because the chart refuses scalable mode with a volume', () => {
  for (const name of ['scalable', 'scalable-read-1', 'scalable-read-2', 'scalable-read-4']) {
    assert.match(overlayText(name), /^persistence:\n  enabled: false$/m, `"${name}" keeps persistence on`);
  }
});

test('the scaling series changes only the read replicas against the scalable overlay', () => {
  const base = overlayText('scalable').replace(/^(  read:\n    replicas: )\d+$/m, '$1N');
  for (const readCount of [1, 2, 4]) {
    const name = `scalable-read-${readCount}`;
    assert.equal(overlayText(name).replace(/^(  read:\n    replicas: )\d+$/m, '$1N'), base, `"${name}" differs beyond its read count`);
  }
});

test('every overlay sizes the heap as a percentage of the memory limit and sets no fixed heap', () => {
  for (const name of TOPOLOGIES) {
    const text = overlayText(name);
    assert.match(
      text,
      /^env:\n  - name: JAVA_OPTS\n    value: "-XX:MaxRAMPercentage=75"$/m,
      `"${name}" does not size the heap as a percentage of the memory limit`,
    );
    assert.doesNotMatch(text, /-Xmx/, `"${name}" sets a fixed heap, which overrides the percentage`);
  }
});

const OBJECT_STORAGE_OVERLAYS = ['ha', 'scalable', 'scalable-read-1', 'scalable-read-2', 'scalable-read-4'];
const TOKEN_FROM_SECRET = /^  - name: INGOT_OPTS\n    value: "--token loadtest:\$\(LOADTEST_PASSWORD\)"$/m;

test('the bucket overlays pass the run token in env, where the shared configuration of the runner keeps it', () => {
  for (const name of OBJECT_STORAGE_OVERLAYS) {
    assert.match(overlayText(name), TOKEN_FROM_SECRET, `"${name}" does not pass the token in env`);
  }
});

test('no overlay holds a token secret, only the reference to the password of the run Secret', () => {
  for (const name of TOPOLOGIES) {
    assert.doesNotMatch(overlayText(name).replace(TOKEN_FROM_SECRET, ''), /--token/, `"${name}" holds a token`);
  }
});
