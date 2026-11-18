import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arrivalVus } from './arrival.ts';

test('arrivalVus preallocates one virtual user per request per second', () => {
  assert.equal(arrivalVus(10).preAllocatedVUs, 10);
});

test('arrivalVus allows four times the rate as maximum virtual users', () => {
  assert.equal(arrivalVus(10).maxVUs, 40);
});

test('arrivalVus keeps at least one virtual user for a fractional rate', () => {
  assert.equal(arrivalVus(0.5).preAllocatedVUs, 1);
});
