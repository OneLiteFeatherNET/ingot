import { describe, it, expect } from 'vitest'
import { entryDirection, entryPosition, motionDirection } from './motionDirection'

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

// Two groups of the side navigation: the order of the flat list runs through the first group, then the second.
const groups = [
  { heading: 'Accounts', links: [{ key: 'users' }, { key: 'machines' }, { key: 'teams' }] },
  { heading: 'Sign in', links: [{ key: 'sso' }, { key: 'ldap' }] }
]

describe('entryDirection', () => {
  it('returns 1 when the entry is in the next group of the tree', () => {
    expect(entryDirection(groups, 'teams', 'sso'), 'the last entry of the first group to the first of the second').toBe(1)
  })

  it('returns -1 when the entry is in the group before', () => {
    expect(entryDirection(groups, 'sso', 'teams'), 'the first entry of the second group back to the last of the first').toBe(-1)
  })

  it('returns 0 when there is no entry on one side, so the page moves without a direction', () => {
    expect(entryDirection(groups, undefined, 'users'), 'no open entry before').toBe(0)
    expect(entryDirection(groups, 'users', 'unknown'), 'an entry that is not in the tree').toBe(0)
  })
})

describe('entryPosition', () => {
  it('counts the entries of all groups in order', () => {
    expect(entryPosition(groups, 'ldap'), 'the last entry of the tree').toBe(4)
  })

  it('returns -1 for an entry that is not in the tree', () => {
    expect(entryPosition(groups, 'unknown'), 'a key the tree does not hold').toBe(-1)
  })
})
