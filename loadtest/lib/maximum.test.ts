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
import { maximumSustainableRate, type MeasuredStep } from './maximum.ts';

test('the maximum is the rate of the highest passing step', () => {
  const steps: MeasuredStep[] = [
    { rate: 60, passed: true },
    { rate: 70, passed: true },
    { rate: 80, passed: true },
    { rate: 90, passed: false },
    { rate: 100, passed: false },
  ];

  assert.equal(maximumSustainableRate(steps, true), 80);
});

test('the maximum is the highest passing rate even when the steps are not sorted by rate', () => {
  const steps: MeasuredStep[] = [
    { rate: 160, passed: true },
    { rate: 200, passed: true },
    { rate: 120, passed: true },
    { rate: 240, passed: false },
  ];

  assert.equal(maximumSustainableRate(steps, true), 200);
});

test('a passing step below a failing step is still the maximum when every higher step fails', () => {
  const steps: MeasuredStep[] = [
    { rate: 50, passed: true },
    { rate: 60, passed: false },
    { rate: 70, passed: false },
  ];

  assert.equal(maximumSustainableRate(steps, true), 50);
});

test('the maximum is absent when no step passes', () => {
  const steps: MeasuredStep[] = [
    { rate: 60, passed: false },
    { rate: 70, passed: false },
  ];

  assert.equal(maximumSustainableRate(steps, true), undefined);
});

test('the maximum is absent when there are no steps', () => {
  assert.equal(maximumSustainableRate([], true), undefined);
});

test('the maximum is absent when the breakpoint was not reached, even if every step passes', () => {
  const steps: MeasuredStep[] = [
    { rate: 60, passed: true },
    { rate: 70, passed: true },
  ];

  assert.equal(maximumSustainableRate(steps, false), undefined);
});

test('a rate that is not a finite number is refused', () => {
  const steps: MeasuredStep[] = [{ rate: Number.NaN, passed: true }];

  assert.throws(() => maximumSustainableRate(steps, true), /rate/);
});

test('a negative rate is refused', () => {
  const steps: MeasuredStep[] = [{ rate: -10, passed: true }];

  assert.throws(() => maximumSustainableRate(steps, true), /rate/);
});
