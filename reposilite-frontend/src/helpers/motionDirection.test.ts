import { describe, it, expect } from 'vitest'
import { motionDirection } from './motionDirection'

// A fixed list of three siblings, so the direction is read from the positions and never from a clock.
const tabs = ['system', 'maintenance', 'settings']

describe('motionDirection', () => {
  it('returns 1 when the target sibling comes later in the list', () => {
    expect(motionDirection(tabs.indexOf('system'), tabs.indexOf('settings')), 'system to settings').toBe(1)
  })

  it('returns -1 when the target sibling comes earlier in the list', () => {
    expect(motionDirection(tabs.indexOf('settings'), tabs.indexOf('maintenance')), 'settings back to maintenance').toBe(-1)
  })

  it('returns 0 when the two positions are the same', () => {
    expect(motionDirection(tabs.indexOf('maintenance'), tabs.indexOf('maintenance')), 'the same tab').toBe(0)
  })
})
