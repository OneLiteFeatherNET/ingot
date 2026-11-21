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
// The bucket of the topologies that keep their artifacts in object storage. The bucket claim of the infrastructure
// repository publishes the endpoint in the ConfigMap ingot-loadtest-s3 (BUCKET_HOST, BUCKET_NAME, BUCKET_PORT) and the
// credentials in the Secret of the same name, which the overlays already pass as objectStorage.existingSecret. The
// server reads endpoint and bucket from the storage provider settings of a repository, not from the environment, so the
// runner reads the ConfigMap and builds those settings. No host of the cluster is kept in the repository.
//
// The settings reach the release as the chart's shared configuration, so every pod starts on the bucket. A worker of
// scalable mode refuses to start while a repository is on the file system, so the settings cannot wait for the k6
// setup() and the settings API: the pods would never become ready and the TestRun would never start. The shared
// configuration also leaves the settings in the shared database untouched, so a later monolith run does not inherit the
// bucket. The chart then owns INGOT_OPTS in env, which wins over the INGOT_OPTS of the run Secret in envFrom, so the
// bucket overlays pass the run token in env as a reference to the password of that Secret.

import { LOAD_NAMESPACE, type Topology } from '../lib/config.ts';

/** The ConfigMap of the bucket claim, in the load test namespace. */
export const OBJECT_STORAGE_CONFIG_MAP = 'ingot-loadtest-s3';

/**
 * The region of the requests when the ConfigMap names none. Ceph RGW does not check it unless its zone group has an API
 * name, and us-east-1 is the region the S3 clients fall back to, so it is the one value both accept.
 */
export const DEFAULT_BUCKET_REGION = 'us-east-1';

/** The S3 storage provider settings of a repository, with the field names of the Ingot settings. */
export interface S3StorageProvider {
  readonly type: 's3';
  readonly bucketName: string;
  readonly endpoint: string;
  readonly region: string;
  /** All repositories share the one bucket, each under its own key prefix. */
  readonly sharedBucket: true;
}

/** The monolith and split keep a single server on its volume; the replicated topologies share the bucket. */
export function usesObjectStorage(topology: Topology): boolean {
  return topology === 'ha' || topology.startsWith('scalable');
}

/** The arguments of the kubectl call that prints the data of the ConfigMap as one JSON object. */
export function objectStorageConfigMapCommand(): string[] {
  return ['-n', LOAD_NAMESPACE, 'get', 'configmap', OBJECT_STORAGE_CONFIG_MAP, '-o', 'jsonpath={.data}'];
}

const HOST_PATTERN = /^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$/;

/**
 * The S3 settings of a topology on the bucket, from the ConfigMap data printed by `objectStorageConfigMapCommand`.
 * Undefined for a topology on the file system, which does not read the data at all.
 */
export function objectStorageSettings(topology: Topology, configMapData: string): S3StorageProvider | undefined {
  if (!usesObjectStorage(topology)) {
    return undefined;
  }
  const data = parseData(configMapData);
  const host = required(data, 'BUCKET_HOST');
  if (!HOST_PATTERN.test(host)) {
    throw new Error(`refusing BUCKET_HOST "${host}" of ${OBJECT_STORAGE_CONFIG_MAP}: expected a host name without scheme, port or path`);
  }
  const portText = required(data, 'BUCKET_PORT');
  const port = Number(portText);
  if (!/^\d+$/.test(portText) || port < 1 || port > 65535) {
    throw new Error(`refusing BUCKET_PORT "${portText}" of ${OBJECT_STORAGE_CONFIG_MAP}: expected a TCP port from 1 to 65535`);
  }
  const scheme = port === 443 ? 'https' : 'http';
  return {
    type: 's3',
    bucketName: required(data, 'BUCKET_NAME'),
    endpoint: `${scheme}://${host}:${port}`,
    region: data.BUCKET_REGION?.trim() || DEFAULT_BUCKET_REGION,
    sharedBucket: true,
  };
}

/** The default repositories of Ingot, each on the bucket. The private one keeps its visibility. */
const REPOSITORIES = [
  { id: 'releases', visibility: 'PUBLIC' },
  { id: 'snapshots', visibility: 'PUBLIC' },
  { id: 'private', visibility: 'PRIVATE' },
] as const;

/**
 * The shared configuration document of a bucket run: the maven domain with every default repository on the bucket. It
 * names no credentials, so the server reads them from the bucket Secret the overlays pass.
 */
export function sharedConfigurationDocument(settings: S3StorageProvider): string {
  const repositories = REPOSITORIES.map((repository) => ({ ...repository, storageProvider: { ...settings } }));
  return `${JSON.stringify({ maven: { repositories } }, null, 2)}\n`;
}

/** The helm arguments that enable the chart's shared configuration with the document in the given file. */
export function objectStorageHelmArguments(documentPath: string): string[] {
  return [
    '--set', 'sharedConfiguration.enabled=true',
    '--set-file', `sharedConfiguration.content=${documentPath}`,
  ];
}

function parseData(text: string): Record<string, string | undefined> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = undefined;
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`refusing the data of ConfigMap ${OBJECT_STORAGE_CONFIG_MAP}: expected one JSON object of keys and values`);
  }
  return parsed as Record<string, string | undefined>;
}

function required(data: Record<string, string | undefined>, key: string): string {
  const value = data[key];
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`missing ${key} in ConfigMap ${OBJECT_STORAGE_CONFIG_MAP} of namespace ${LOAD_NAMESPACE}`);
  }
  return value.trim();
}
