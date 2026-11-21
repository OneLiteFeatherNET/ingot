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
import { LOAD_NAMESPACE, TOPOLOGIES } from '../lib/config.ts';
import {
  DEFAULT_BUCKET_REGION,
  OBJECT_STORAGE_CONFIG_MAP,
  objectStorageConfigMapCommand,
  objectStorageSettings,
  usesObjectStorage,
} from './object-storage.ts';

const bucket = JSON.stringify({
  BUCKET_HOST: 'rook-ceph-rgw-store.rook-ceph.svc',
  BUCKET_NAME: 'ingot-loadtest-0a1b2c',
  BUCKET_PORT: '80',
});

test('ha and the scalable topologies keep their artifacts in the bucket', () => {
  for (const topology of ['ha', 'scalable', 'scalable-read-1', 'scalable-read-2', 'scalable-read-4'] as const) {
    assert.equal(usesObjectStorage(topology), true, `${topology} should use the bucket`);
  }
});

test('the monolith and split topologies keep their artifacts on the file system', () => {
  assert.equal(usesObjectStorage('monolith'), false);
  assert.equal(usesObjectStorage('split'), false);
});

test('every topology of the config is either on the bucket or on the file system', () => {
  for (const topology of TOPOLOGIES) {
    assert.equal(typeof usesObjectStorage(topology), 'boolean', `${topology} has no storage decision`);
  }
});

test('the ConfigMap is read from the load test namespace only', () => {
  assert.deepEqual(objectStorageConfigMapCommand(), [
    '-n', LOAD_NAMESPACE, 'get', 'configmap', OBJECT_STORAGE_CONFIG_MAP, '-o', 'jsonpath={.data}',
  ]);
  assert.equal(OBJECT_STORAGE_CONFIG_MAP, 'ingot-loadtest-s3');
});

test('the settings name the bucket, the endpoint from host and port, and the default region', () => {
  assert.deepEqual(objectStorageSettings('ha', bucket), {
    type: 's3',
    bucketName: 'ingot-loadtest-0a1b2c',
    endpoint: 'http://rook-ceph-rgw-store.rook-ceph.svc:80',
    region: DEFAULT_BUCKET_REGION,
    sharedBucket: true,
  });
});

test('the default region is the one Ceph RGW accepts without a configured zone group name', () => {
  assert.equal(DEFAULT_BUCKET_REGION, 'us-east-1');
});

test('port 443 is reached over https', () => {
  const settings = objectStorageSettings('scalable', JSON.stringify({ ...JSON.parse(bucket), BUCKET_PORT: '443' }));

  assert.equal(settings?.endpoint, 'https://rook-ceph-rgw-store.rook-ceph.svc:443');
});

test('a BUCKET_REGION in the ConfigMap replaces the default region', () => {
  const settings = objectStorageSettings('ha', JSON.stringify({ ...JSON.parse(bucket), BUCKET_REGION: 'eu-central-1' }));

  assert.equal(settings?.region, 'eu-central-1');
});

test('a topology on the file system gets no bucket settings and does not need the ConfigMap', () => {
  assert.equal(objectStorageSettings('monolith', '{}'), undefined);
  assert.equal(objectStorageSettings('split', ''), undefined);
});

for (const key of ['BUCKET_HOST', 'BUCKET_NAME', 'BUCKET_PORT']) {
  test(`a ConfigMap without ${key} is refused and the key is named`, () => {
    const data = JSON.parse(bucket);
    delete data[key];

    assert.throws(() => objectStorageSettings('ha', JSON.stringify(data)), new RegExp(`${key}.*${OBJECT_STORAGE_CONFIG_MAP}`));
  });
}

test('a port that is not a TCP port is refused', () => {
  for (const port of ['0', '65536', '80a', '-1']) {
    assert.throws(
      () => objectStorageSettings('ha', JSON.stringify({ ...JSON.parse(bucket), BUCKET_PORT: port })),
      /BUCKET_PORT/,
      `port "${port}" should be refused`,
    );
  }
});

test('a host with a scheme or a path is refused, so the endpoint is always scheme, host and port', () => {
  for (const host of ['http://rgw', 'rgw/path', 'rgw:80']) {
    assert.throws(
      () => objectStorageSettings('ha', JSON.stringify({ ...JSON.parse(bucket), BUCKET_HOST: host })),
      /BUCKET_HOST/,
      `host "${host}" should be refused`,
    );
  }
});

test('ConfigMap data that is not a JSON object is refused', () => {
  assert.throws(() => objectStorageSettings('ha', ''), /ConfigMap/);
  assert.throws(() => objectStorageSettings('ha', '[]'), /ConfigMap/);
});
