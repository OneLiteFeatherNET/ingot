import { describe, it, expect } from 'vitest'
import { linkAllowed } from './pluginBlocks'

describe('linkAllowed accepts a same-origin path only', () => {
  it.each(['/plugins/x/y', '/browse/repo/a/b.jar', '/repo/list?page=2&sort=name', '/a/b~c_d.e-f'])(
    'accepts %s',
    (href) => {
      expect(linkAllowed(href), `link ${href}`).toBe(true)
    },
  )

  it.each(['//h', '/\\h', 'https://h', 'javascript:alert(1)', 'data:text/html,x', 'mailto:a@b'])(
    'refuses %s, which leaves the host or starts a scheme',
    (href) => {
      expect(linkAllowed(href), `link ${href}`).toBe(false)
    },
  )

  it.each(['/api/tokens', '/assets/x'])('refuses %s, a data route or an asset', (href) => {
    expect(linkAllowed(href), `link ${href}`).toBe(false)
  })

  it.each(['/a/../b', '/a%2e%2e/b', '/a/./b', '/a//b', '/a b', '/a#frag'])(
    'refuses %s, with traversal, encoding, empty segments or characters outside the set',
    (href) => {
      expect(linkAllowed(href), `link ${href}`).toBe(false)
    },
  )

  it('refuses a link that is not a string', () => {
    expect(linkAllowed(undefined), 'an undefined link').toBe(false)
    expect(linkAllowed(42), 'a number as link').toBe(false)
  })

  it('refuses a path of 501 characters', () => {
    expect(linkAllowed('/' + 'a'.repeat(500)), 'a 501 character link').toBe(false)
  })

  it('accepts a path of 500 characters', () => {
    expect(linkAllowed('/' + 'a'.repeat(499)), 'a 500 character link').toBe(true)
  })
})
