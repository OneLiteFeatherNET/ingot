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
import { PLATEAU_DEFAULT_HOLD_MINUTES, PLATEAU_MIN_HOLD_MINUTES } from '../lib/config.ts';
import { plateauScenarios, plateauSteps } from './plateau.ts';

test('plateauSteps returns the five steps at 60, 70, 80, 90 and 100 percent in that order', () => {
  const steps = plateauSteps(100);

  assert.deepEqual(
    steps.map((step) => step.percent),
    [60, 70, 80, 90, 100],
  );
});

test('plateauSteps scales each step rate from the breakpoint', () => {
  const steps = plateauSteps(200);

  assert.deepEqual(
    steps.map((step) => step.rate),
    [120, 140, 160, 180, 200],
  );
});

test('plateauSteps numbers the steps from one', () => {
  const steps = plateauSteps(100);

  assert.deepEqual(
    steps.map((step) => step.index),
    [1, 2, 3, 4, 5],
  );
});

test('plateauSteps holds every step for the default fifteen minutes when no hold is given', () => {
  const steps = plateauSteps(100);

  assert.equal(PLATEAU_DEFAULT_HOLD_MINUTES, 15);
  assert.deepEqual(
    steps.map((step) => step.holdMinutes),
    [15, 15, 15, 15, 15],
  );
});

test('plateauSteps holds every step for the given hold', () => {
  const steps = plateauSteps(100, 30);

  assert.deepEqual(
    steps.map((step) => step.holdMinutes),
    [30, 30, 30, 30, 30],
  );
});

test('plateauSteps accepts a hold of exactly the ten minute minimum', () => {
  const steps = plateauSteps(100, PLATEAU_MIN_HOLD_MINUTES);

  assert.equal(steps[0]?.holdMinutes, 10);
});

test('plateauSteps refuses a hold of nine minutes and names the ten minute minimum', () => {
  assert.throws(() => plateauSteps(100, 9), /10 minute/);
});

test('plateauSteps rounds each rate to a whole request per second, as k6 expects', () => {
  // 60 percent of 7 is 4.2 and 70 percent is 4.9, so the rates are 4 and 5.
  const steps = plateauSteps(7);

  assert.deepEqual(
    steps.map((step) => step.rate),
    [4, 5, 6, 6, 7],
  );
});

test('plateauSteps never returns a rate of zero for a breakpoint of one request per second', () => {
  const steps = plateauSteps(1);

  assert.ok(steps.every((step) => step.rate >= 1));
});

test('plateauSteps refuses a breakpoint of zero and names it', () => {
  assert.throws(() => plateauSteps(0), /breakpoint of 0/);
});

test('plateauSteps refuses a breakpoint that is not a finite number', () => {
  assert.throws(() => plateauSteps(Number.NaN), /breakpoint/);
  assert.throws(() => plateauSteps(Number.POSITIVE_INFINITY), /breakpoint/);
});

test('plateauSteps refuses a negative breakpoint and names it', () => {
  assert.throws(() => plateauSteps(-5), /breakpoint of -5/);
});

test('plateauScenarios names one constant-arrival-rate scenario per step', () => {
  const scenarios = plateauScenarios(plateauSteps(100), 'mix');

  assert.deepEqual(Object.keys(scenarios), ['plateau-1', 'plateau-2', 'plateau-3', 'plateau-4', 'plateau-5']);
  assert.ok(Object.values(scenarios).every((scenario) => scenario.executor === 'constant-arrival-rate'));
});

test('plateauScenarios holds each step at its rate for its hold', () => {
  const scenarios = plateauScenarios(plateauSteps(200), 'mix');

  assert.deepEqual(
    Object.values(scenarios).map((scenario) => [scenario.rate, scenario.duration]),
    [
      [120, '15m'],
      [140, '15m'],
      [160, '15m'],
      [180, '15m'],
      [200, '15m'],
    ],
  );
});

test('plateauScenarios starts each step when the steps before it have ended', () => {
  const scenarios = plateauScenarios(plateauSteps(100, 10), 'mix');

  assert.deepEqual(
    Object.values(scenarios).map((scenario) => scenario.startTime),
    ['0m', '10m', '20m', '30m', '40m'],
  );
});

test('plateauScenarios runs every step through the given exec function', () => {
  const scenarios = plateauScenarios(plateauSteps(100), 'deploy');

  assert.ok(Object.values(scenarios).every((scenario) => scenario.exec === 'deploy'));
});

test('plateauScenarios sizes the virtual users from the rate of each step', () => {
  const scenarios = plateauScenarios(plateauSteps(100), 'mix');

  assert.equal(scenarios['plateau-1']?.preAllocatedVUs, 60);
  assert.equal(scenarios['plateau-1']?.maxVUs, 240);
});
