import assert from 'node:assert/strict';
import { test } from 'node:test';
import { SOAK_DEFAULT_MINUTES, soakScenario } from './soak.ts';

test('soakScenario holds the maximum rate with the constant-arrival-rate executor', () => {
  const scenario = soakScenario({ rate: 40, exec: 'mix' });

  assert.equal(scenario.executor, 'constant-arrival-rate');
  assert.equal(scenario.rate, 40);
});

test('soakScenario runs for two hours by default', () => {
  const scenario = soakScenario({ rate: 40, exec: 'mix' });

  assert.equal(SOAK_DEFAULT_MINUTES, 120);
  assert.equal(scenario.duration, '120m');
});

test('soakScenario takes its duration in minutes when one is given', () => {
  const scenario = soakScenario({ rate: 40, exec: 'mix', minutes: 30 });

  assert.equal(scenario.duration, '30m');
});

test('soakScenario runs the given exec function', () => {
  const scenario = soakScenario({ rate: 40, exec: 'deploy' });

  assert.equal(scenario.exec, 'deploy');
});

test('soakScenario refuses a rate below one request per second and names it', () => {
  assert.throws(() => soakScenario({ rate: 0.5, exec: 'mix' }), /rate of 0.5/);
});

test('soakScenario refuses a duration under one minute and names it', () => {
  assert.throws(() => soakScenario({ rate: 40, exec: 'mix', minutes: 0 }), /0 minute/);
});
