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
// The runner entry point: `npm run load -- --topology <t> --profile <p> --scenario <s> [--reference] [--parallelism n]`.
// The Ingot image tag is read from INGOT_IMAGE_TAG, the remote write endpoint of the metrics store from INGOT_LOADTEST_RW_URL. The namespace is fixed to the load test namespace; the TestRun and
// the steps refuse any other one before a kubectl call. The effects below call kubectl, helm and docker (for k6 archive).

import { execFile } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { INGOT_RELEASE, K6_IMAGE, LOAD_NAMESPACE, parseRunOptions } from '../lib/config.ts';
import { remoteWriteUrl, scriptArguments, scriptVariables } from './environment.ts';
import { labelledNodesCommand } from './nodes.ts';
import { buildTestRun } from './testrun.ts';
import { runSteps, type RunnerPorts } from './steps.ts';

const exec = promisify(execFile);
const loadtestRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const chartPath = join(loadtestRoot, '..', 'charts', 'ingot');

const READY_TIMEOUT_MS = 15 * 60_000;
/** The longest profile is the soak (2 hours) on top of the plateau series; the deadline leaves room for both. */
const COMPLETION_TIMEOUT_MS = 4 * 60 * 60_000;
const POLL_MS = 10_000;

async function kubectl(args: string[]): Promise<string> {
  const { stdout } = await exec('kubectl', args, { maxBuffer: 16 * 1024 * 1024 });
  return stdout;
}

/** A poll that fails, for example on a timeout of the API server, is retried at the next poll until the deadline. */
async function polled(args: string[]): Promise<string | undefined> {
  try {
    return await kubectl(args);
  } catch (error) {
    console.error(`poll failed, retrying: ${error instanceof Error ? error.message : String(error)}`);
    return undefined;
  }
}

function flagValue(argv: readonly string[], flag: string): string | undefined {
  const index = argv.indexOf(flag);
  return index === -1 ? undefined : argv[index + 1];
}

async function main(argv: readonly string[]): Promise<void> {
  const options = parseRunOptions(argv);
  const reference = argv.includes('--reference');
  const parallelism = Number(flagValue(argv, '--parallelism') ?? '1');
  if (!Number.isInteger(parallelism) || parallelism < 1) {
    throw new Error(`--parallelism must be a whole number of at least 1, got "${flagValue(argv, '--parallelism')}"`);
  }
  const ingotVersion = process.env.INGOT_IMAGE_TAG;
  if (!ingotVersion) {
    throw new Error('set INGOT_IMAGE_TAG to the Ingot image tag under test');
  }
  const ingotDigest = process.env.INGOT_IMAGE_DIGEST;
  const rwUrl = remoteWriteUrl(process.env);
  const profileInputs = scriptVariables(process.env);
  const testId = `lt-${Date.now().toString(36)}`;
  const configMap = `${testId}-scripts`;
  // The load test user exists only for this run: the server creates it from --token, and k6 reads it from the Secret.
  const credentialsSecret = `${testId}-credentials`;
  const password = randomBytes(24).toString('hex');
  const archiveFile = `${options.scenario}.tar`;
  const testRun = buildTestRun({
    namespace: LOAD_NAMESPACE,
    testId,
    topology: options.topology,
    profile: options.profile,
    scenario: options.scenario,
    ingotVersion,
    parallelism,
    configMap,
    archiveFile,
    reference,
    credentialsSecret,
    remoteWriteUrl: rwUrl,
    scriptVariables: profileInputs,
    baseUrl: `http://${INGOT_RELEASE}:8080`,
  });
  const workDir = mkdtempSync(join(tmpdir(), 'ingot-load-'));
  const manifestPath = join(workDir, 'testrun.json');
  writeFileSync(manifestPath, JSON.stringify(testRun, null, 2));
  const secretPath = join(workDir, 'credentials.json');
  writeFileSync(
    secretPath,
    JSON.stringify({
      apiVersion: 'v1',
      kind: 'Secret',
      metadata: { name: credentialsSecret, namespace: LOAD_NAMESPACE },
      stringData: {
        LOADTEST_USER: 'loadtest',
        LOADTEST_PASSWORD: password,
        INGOT_OPTS: `--token loadtest:${password}`,
      },
    }),
    { mode: 0o600 },
  );
  console.log(`test run ${testId}: ${options.topology}, ${options.profile}, ${options.scenario}, Ingot ${ingotVersion}`);

  const ports: RunnerPorts = {
    labelledNodes: async () =>
      (await kubectl(labelledNodesCommand())).split('\n').filter((line) => line.trim() !== '').length,
    install: async () => {
      await kubectl(['apply', '-f', secretPath]);
      await exec('helm', [
        'upgrade', '--install', INGOT_RELEASE, chartPath,
        '--namespace', LOAD_NAMESPACE,
        '--values', join(loadtestRoot, 'topologies', `${options.topology}.yaml`),
        '--set', `image.tag=${ingotVersion}`,
        // A rolling tag such as nightly can resolve to an older image on the node, so a digest pins the exact one.
        ...(ingotDigest ? ['--set', `image.digest=${ingotDigest}`] : []),
        '--set', `envFrom[0].secretRef.name=${credentialsSecret}`,
        '--wait=false',
      ]);
    },
    ready: async () => {
      const output = await polled([
        '-n', LOAD_NAMESPACE, 'get', 'pods',
        '-l', `app.kubernetes.io/instance=${INGOT_RELEASE}`,
        '-o', 'jsonpath={range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\\n"}{end}',
      ]);
      const statuses = (output ?? '').split('\n').filter((line) => line.trim() !== '');
      return statuses.length > 0 && statuses.every((status) => status === 'True');
    },
    archive: async () => {
      // k6 archive runs in the pinned k6 image, so the archive matches the k6 that runs it in the cluster. The image user
      // cannot write to the private work directory, so the container runs as root (the calling user under rootless
      // podman). The script options need the profile already while archiving.
      await exec('docker', [
        'run', '--rm', '--user', '0:0', '-v', `${loadtestRoot}:/work:ro`, '-v', `${workDir}:/out`, '-w', '/work',
        K6_IMAGE, 'archive', ...scriptArguments(options.profile, profileInputs),
        `scenarios/${options.scenario}.ts`, '-O', `/out/${archiveFile}`,
      ]);
      await kubectl([
        '-n', LOAD_NAMESPACE, 'create', 'configmap', configMap, '--from-file', join(workDir, archiveFile),
      ]);
    },
    apply: async () => {
      await kubectl(['apply', '-f', manifestPath]);
    },
    finished: async () => {
      const stage = (
        (await polled([
          '-n', LOAD_NAMESPACE, 'get', 'testrun', testRun.metadata.name, '-o', 'jsonpath={.status.stage}',
        ])) ?? ''
      ).trim();
      if (stage === 'error') {
        throw new Error(`TestRun ${testRun.metadata.name} ended in the error stage`);
      }
      return stage === 'finished';
    },
    // The record step is `recordStep` of record.ts. The runner does not yet apply a pod size or read the measured outcome,
    // so a run writes no record until both are wired, rather than a record with a size that was never applied.
    record: async () => {},
    // Removes everything the run created, each part even when an earlier one failed. The chart keeps its volume on
    // uninstall, so it is deleted by name, which keeps every run on a fresh data directory.
    uninstall: async () => {
      const removals = [
        () => exec('helm', ['uninstall', INGOT_RELEASE, '--namespace', LOAD_NAMESPACE, '--ignore-not-found']),
        () => kubectl(['-n', LOAD_NAMESPACE, 'delete', 'testrun', testRun.metadata.name, '--ignore-not-found']),
        () => kubectl(['-n', LOAD_NAMESPACE, 'delete', 'configmap', configMap, '--ignore-not-found']),
        () => kubectl(['-n', LOAD_NAMESPACE, 'delete', 'secret', credentialsSecret, '--ignore-not-found']),
        () => kubectl(['-n', LOAD_NAMESPACE, 'delete', 'pvc', INGOT_RELEASE, '--ignore-not-found']),
      ];
      const failures: string[] = [];
      for (const removal of removals) {
        try {
          await removal();
        } catch (error) {
          failures.push(error instanceof Error ? error.message : String(error));
        }
      }
      // Reported without throwing, so the error of an earlier step is not replaced by this one.
      if (failures.length > 0) {
        console.error(`the cleanup of ${testId} was incomplete:\n${failures.join('\n')}`);
        process.exitCode = 1;
      }
    },
    pause: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  };

  await runSteps(ports, { now: () => Date.now() }, {
    namespace: LOAD_NAMESPACE,
    topology: options.topology,
    reference,
    readyTimeoutMs: READY_TIMEOUT_MS,
    completionTimeoutMs: COMPLETION_TIMEOUT_MS,
    pollMs: POLL_MS,
  });
}

main(process.argv.slice(2)).catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
